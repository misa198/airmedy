package updater

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/blang/semver"
	update "github.com/inconshreveable/go-update"
)

const (
	repoOwner = "misa198"
	repoName  = "airmedy"
)

type UpdateInfo struct {
	Version      string `json:"version"`
	ReleaseNotes string `json:"release_notes"`
	PublishedAt  string `json:"published_at"`
}

// ProgressFunc receives bytes downloaded and total size (-1 if unknown).
type ProgressFunc func(downloaded, total int64)

type Service struct {
	currentVersion string
	logger         *slog.Logger
	pending        *pendingRelease
}

type pendingRelease struct {
	info     UpdateInfo
	assetURL string
	checksum string // sha256 hex; empty if SHA256SUMS not present in release
}

type ghRelease struct {
	TagName     string    `json:"tag_name"`
	Body        string    `json:"body"`
	PublishedAt string    `json:"published_at"`
	Assets      []ghAsset `json:"assets"`
}

type ghAsset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
}

func NewService(version string, logger *slog.Logger) *Service {
	return &Service{currentVersion: version, logger: logger}
}

func (s *Service) GetCurrentVersion() string {
	return s.currentVersion
}

func (s *Service) CheckForUpdate(ctx context.Context) (*UpdateInfo, error) {
	s.logger.Info("checking for updates", "current_version", s.currentVersion)

	rel, err := fetchLatestRelease(ctx)
	if err != nil {
		return nil, fmt.Errorf("fetch latest release: %w", err)
	}

	tagVersion := strings.TrimPrefix(rel.TagName, "v")
	latest, err := semver.Parse(tagVersion)
	if err != nil {
		return nil, fmt.Errorf("parse remote version %q: %w", tagVersion, err)
	}

	current, err := semver.Parse(s.currentVersion)
	if err != nil {
		s.logger.Warn("current version not valid semver, skipping update check", "version", s.currentVersion)
		return nil, nil
	}

	if !latest.GT(current) {
		return nil, nil
	}

	asset := findPlatformAsset(rel.Assets)
	if asset == nil {
		return nil, fmt.Errorf("no release asset found for %s/%s", runtime.GOOS, runtime.GOARCH)
	}

	checksum, err := fetchChecksumForAsset(ctx, rel.Assets, asset.Name)
	if err != nil {
		s.logger.Warn("SHA256SUMS not available, update will proceed without checksum verification", "error", err)
	}

	info := UpdateInfo{
		Version:      tagVersion,
		ReleaseNotes: rel.Body,
		PublishedAt:  rel.PublishedAt,
	}
	s.pending = &pendingRelease{
		info:     info,
		assetURL: asset.BrowserDownloadURL,
		checksum: checksum,
	}

	return &info, nil
}

func (s *Service) DownloadAndApply(ctx context.Context, progress ProgressFunc) error {
	if s.pending == nil {
		if _, err := s.CheckForUpdate(ctx); err != nil {
			return fmt.Errorf("no cached release and re-check failed: %w", err)
		}
		if s.pending == nil {
			return fmt.Errorf("no update available")
		}
	}

	pending := s.pending
	s.logger.Info("downloading update", "version", pending.info.Version, "url", pending.assetURL)

	tmpFile, err := os.CreateTemp("", "airmedy-update-*")
	if err != nil {
		return fmt.Errorf("create temp file: %w", err)
	}
	defer os.Remove(tmpFile.Name())
	defer tmpFile.Close()

	hasher := sha256.New()
	if err := downloadWithProgress(ctx, pending.assetURL, io.MultiWriter(tmpFile, hasher), progress); err != nil {
		return fmt.Errorf("download: %w", err)
	}

	if pending.checksum != "" {
		got := hex.EncodeToString(hasher.Sum(nil))
		if got != pending.checksum {
			return fmt.Errorf("checksum mismatch: got %s, want %s", got, pending.checksum)
		}
		s.logger.Info("checksum verified", "sha256", got)
	}

	if _, err := tmpFile.Seek(0, io.SeekStart); err != nil {
		return fmt.Errorf("seek temp file: %w", err)
	}
	archiveData, err := io.ReadAll(tmpFile)
	if err != nil {
		return fmt.Errorf("read temp file: %w", err)
	}

	exeName := "airmedy"
	if runtime.GOOS == "windows" {
		exeName = "airmedy.exe"
	}

	binary, err := extractBinary(archiveData, pending.assetURL, exeName)
	if err != nil {
		return fmt.Errorf("extract binary: %w", err)
	}

	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("get executable path: %w", err)
	}

	s.logger.Info("applying update", "target", exe, "version", pending.info.Version)
	if err := update.Apply(bytes.NewReader(binary), update.Options{TargetPath: exe}); err != nil {
		return fmt.Errorf("apply update: %w", err)
	}

	if err := postUpdate(exe, pending.info.Version); err != nil {
		s.logger.Warn("post-update steps failed (update still applied)", "error", err)
	}

	s.pending = nil
	s.logger.Info("update applied, restart to use new version", "version", pending.info.Version)
	return nil
}

func (s *Service) GetRestartInfo() (bundlePath string, exe string, err error) {
	exe, err = os.Executable()
	if err != nil {
		return "", "", fmt.Errorf("get executable path: %w", err)
	}
	bundlePath = getBundlePath(exe)
	return bundlePath, exe, nil
}

// --- GitHub API ---

func fetchLatestRelease(ctx context.Context) (*ghRelease, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", repoOwner, repoName)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("X-GitHub-Api-Version", "2022-11-28")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("github api status %d", resp.StatusCode)
	}

	var rel ghRelease
	if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
		return nil, err
	}
	return &rel, nil
}

func fetchChecksumForAsset(ctx context.Context, assets []ghAsset, assetName string) (string, error) {
	var sumsURL string
	for _, a := range assets {
		if a.Name == "SHA256SUMS" {
			sumsURL = a.BrowserDownloadURL
			break
		}
	}
	if sumsURL == "" {
		return "", fmt.Errorf("SHA256SUMS asset not in release")
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, sumsURL, nil)
	if err != nil {
		return "", err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	for _, line := range strings.Split(string(data), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 2 && fields[1] == assetName {
			return fields[0], nil
		}
	}
	return "", fmt.Errorf("%q not found in SHA256SUMS", assetName)
}

// findPlatformAsset picks the release asset for the current OS and arch.
// Expected naming: Airmedy_<ver>_<goos>-<goarch>.<ext>
func findPlatformAsset(assets []ghAsset) *ghAsset {
	goos := runtime.GOOS
	goarch := runtime.GOARCH

	archAliases := map[string][]string{
		"amd64": {"amd64", "x86_64", "x64"},
		"arm64": {"arm64", "aarch64"},
	}
	aliases, ok := archAliases[goarch]
	if !ok {
		aliases = []string{goarch}
	}

	wantExt := ".tar.gz"
	if goos == "darwin" || goos == "windows" {
		wantExt = ".zip"
	}

	for i := range assets {
		name := strings.ToLower(assets[i].Name)
		if !strings.Contains(name, goos) || !strings.HasSuffix(name, wantExt) {
			continue
		}
		for _, alias := range aliases {
			if strings.Contains(name, alias) {
				return &assets[i]
			}
		}
	}
	return nil
}

// --- Download ---

func downloadWithProgress(ctx context.Context, url string, dst io.Writer, progress ProgressFunc) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	total := resp.ContentLength
	var downloaded int64
	buf := make([]byte, 32*1024)

	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			if _, writeErr := dst.Write(buf[:n]); writeErr != nil {
				return writeErr
			}
			downloaded += int64(n)
			if progress != nil {
				progress(downloaded, total)
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return readErr
		}
	}
	return nil
}

// --- Archive extraction ---

// extractBinary extracts the named executable from a .zip or .tar.gz archive,
// matching by the base filename only (not directory path).
func extractBinary(data []byte, archiveURL, exeName string) ([]byte, error) {
	switch {
	case strings.HasSuffix(archiveURL, ".zip"):
		return extractFromZip(data, exeName)
	case strings.HasSuffix(archiveURL, ".tar.gz"), strings.HasSuffix(archiveURL, ".tgz"):
		return extractFromTarGz(data, exeName)
	default:
		// Raw binary
		return data, nil
	}
}

func extractFromZip(data []byte, exeName string) ([]byte, error) {
	r, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, err
	}
	for _, f := range r.File {
		if f.FileInfo().IsDir() {
			continue
		}
		if filepath.Base(f.Name) == exeName {
			rc, err := f.Open()
			if err != nil {
				return nil, err
			}
			defer rc.Close()
			return io.ReadAll(rc)
		}
	}
	return nil, fmt.Errorf("binary %q not found in zip", exeName)
}

func extractFromTarGz(data []byte, exeName string) ([]byte, error) {
	gz, err := gzip.NewReader(bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer gz.Close()

	tr := tar.NewReader(gz)
	for {
		h, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
		if !h.FileInfo().IsDir() && filepath.Base(h.Name) == exeName {
			return io.ReadAll(tr)
		}
	}
	return nil, fmt.Errorf("binary %q not found in tar.gz", exeName)
}
