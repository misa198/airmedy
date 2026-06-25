package library

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type deezerSearchResponse struct {
	Data []struct {
		PictureMedium string `json:"picture_medium"`
	} `json:"data"`
}

func (s *LibraryService) StartArtistArtworkWorker(ctx context.Context) {
	s.logger.Info("Starting artist artwork background worker")
	for {
		select {
		case <-ctx.Done():
			s.logger.Info("Stopping artist artwork background worker")
			return
		case job := <-s.artistArtworkQueue:
			s.logger.Info("Processing artist artwork job", "artistID", job.ArtistID, "eventID", job.EventID)
			s.processArtistArtworkJob(ctx, job)
			time.Sleep(100 * time.Millisecond)
		}
	}
}

func (s *LibraryService) processArtistArtworkJob(ctx context.Context, job artistArtworkJob) {
	defer func() {
		s.pendingArtistArtworkMu.Lock()
		delete(s.pendingArtistArtwork, job.ArtistID)
		s.pendingArtistArtworkMu.Unlock()
	}()

	// 1. Check if online artwork is enabled
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		s.logger.Error("Failed to load settings in artwork worker", "error", err)
		return
	}
	if !settings.UseOnlineArtistArtwork {
		s.logger.Info("Online artist artwork disabled in settings, skipping job", "artistID", job.ArtistID)
		return
	}

	// 2. Get artist
	artist, err := s.artistRepo.GetByID(ctx, job.ArtistID)
	if err != nil || artist == nil {
		s.logger.Warn("Artist not found for artwork job", "artistID", job.ArtistID, "error", err)
		return
	}

	// 3. Already have an online image — don't refetch (it's kept even when the
	//    online toggle is later turned off).
	if artist.ArtworkKeyOnline != nil && *artist.ArtworkKeyOnline != "" {
		return
	}

	s.logger.Info("Fetching artwork for artist from Deezer", "artist", artist.Name)

	// 4. Fetch + store the online source. Other sources are untouched.
	artworkURL, _, err := s.writeArtistArtworkSource(ctx, artist.ID, domain.ArtworkSourceOnline,
		func() ([]byte, string, error) {
			return s.fetchArtistArtworkFromDeezer(ctx, artist.Name)
		})
	if err != nil {
		s.logger.Warn("Failed to fetch artist artwork from Deezer", "artist", artist.Name, "artistID", artist.ID, "error", err)
		return
	}
	if artworkURL == "" {
		return
	}

	// 6. Emit the per-request event the waiting frontend card is listening for.
	s.logger.Info("Emitting artist artwork event", "artist", artist.Name, "eventID", job.EventID, "url", artworkURL)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit(job.EventID, artworkURL)
	}
}

// fetchArtistArtworkFromDeezer searches Deezer for the artist and downloads the
// best picture, returning the raw image bytes and mime type. Caching/storage is
// handled by the caller via writeArtistArtwork.
func (s *LibraryService) fetchArtistArtworkFromDeezer(ctx context.Context, name string) ([]byte, string, error) {
	searchURL := fmt.Sprintf("https://api.deezer.com/search/artist?q=%s", url.QueryEscape(name))
	s.logger.Info("Deezer search request", "url", searchURL)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return nil, "", err
	}

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, "", err
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("deezer api returned status %d", resp.StatusCode)
	}

	var searchResult deezerSearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&searchResult); err != nil {
		return nil, "", err
	}

	if len(searchResult.Data) == 0 {
		return nil, "", fmt.Errorf("artist not found on deezer")
	}

	imageURL := searchResult.Data[0].PictureMedium
	s.logger.Info("Deezer found artist artwork", "name", name, "imageURL", imageURL)
	if imageURL == "" {
		return nil, "", fmt.Errorf("no picture found for artist on deezer")
	}

	// Download image
	s.logger.Info("Downloading artist artwork", "url", imageURL)
	imgResp, err := client.Get(imageURL)
	if err != nil {
		return nil, "", err
	}
	defer func() { _ = imgResp.Body.Close() }()

	if imgResp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("failed to download image, status %d", imgResp.StatusCode)
	}

	data, err := io.ReadAll(imgResp.Body)
	if err != nil {
		return nil, "", err
	}

	mimeType := imgResp.Header.Get("Content-Type")
	return data, mimeType, nil
}

func (s *LibraryService) EnqueueArtistArtwork(artistID, eventID string) {
	s.pendingArtistArtworkMu.Lock()
	if _, ok := s.pendingArtistArtwork[artistID]; ok {
		s.pendingArtistArtworkMu.Unlock()
		s.logger.Debug("Artist artwork job already pending, skipping", "artistID", artistID)
		return
	}
	s.pendingArtistArtwork[artistID] = struct{}{}
	s.pendingArtistArtworkMu.Unlock()

	s.logger.Info("Enqueuing artist artwork job", "artistID", artistID, "eventID", eventID)
	select {
	case s.artistArtworkQueue <- artistArtworkJob{ArtistID: artistID, EventID: eventID}:
	default:
		s.pendingArtistArtworkMu.Lock()
		delete(s.pendingArtistArtwork, artistID)
		s.pendingArtistArtworkMu.Unlock()
		s.logger.Warn("Artist artwork queue is full", "artistID", artistID)
	}
}
