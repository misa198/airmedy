package metadata

import (
	"context"
	"fmt"
	"path/filepath"
	"strconv"
	"strings"

	"changeme/internal/domain"

	"go.senan.xyz/taglib"
)

type taglibExtractor struct{}

func NewTagLibExtractor() domain.MetadataExtractor {
	return &taglibExtractor{}
}

func (e *taglibExtractor) Extract(ctx context.Context, path string) (*domain.TrackDTO, error) {
	tags, err := taglib.ReadTags(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read tags from %s: %w", path, err)
	}

	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read properties from %s: %w", path, err)
	}

	dto := &domain.TrackDTO{
		Track: domain.Track{
			Path:   path,
			Format: strings.TrimPrefix(filepath.Ext(path), "."),
		},
		Album: &domain.Album{},
	}

	// Raw values for display
	dto.Track.RawArtistNames = allTags(tags, "; ", "ARTIST", "TPE1", "©ART")
	dto.Track.RawAlbumArtistNames = allTags(tags, "; ", "ALBUMARTIST", "TPE2", "aART")
	dto.Track.RawGenreNames = allTags(tags, "; ", "GENRE", "TCON", "©gen")
	dto.Track.RawComposerNames = allTags(tags, "; ", "COMPOSER", "TCOM", "©wrt")

	// Split and normalize Artists
	artistNames := splitMultipleTags(tags, "ARTIST", "TPE1", "©ART")
	for _, name := range artistNames {
		dto.Artists = append(dto.Artists, &domain.Artist{
			Name:             name,
			SortName:         domain.NormalizeSort(name),
			NormalizationKey: domain.NormalizationKey(name),
		})
	}

	// Split and normalize Album Artists
	albumArtistNames := splitMultipleTags(tags, "ALBUMARTIST", "TPE2", "aART")
	for _, name := range albumArtistNames {
		dto.AlbumArtists = append(dto.AlbumArtists, &domain.Artist{
			Name:             name,
			SortName:         domain.NormalizeSort(name),
			NormalizationKey: domain.NormalizationKey(name),
		})
	}

	// Split and normalize Genres
	genreNames := splitMultipleTags(tags, "GENRE")
	for _, name := range genreNames {
		dto.Genres = append(dto.Genres, &domain.Genre{
			Name:             name,
			NormalizationKey: domain.NormalizationKey(name),
		})
	}

	// Split and normalize Composers
	composerNames := splitMultipleTags(tags, "COMPOSER")
	for _, name := range composerNames {
		dto.Composers = append(dto.Composers, &domain.Composer{
			Name:             name,
			NormalizationKey: domain.NormalizationKey(name),
		})
	}

	// Basic tags
	dto.Track.Title = firstTag(tags, "TITLE")
	dto.Track.SortTitle = firstTag(tags, "TITLESORT", "TSOT", "sonm")
	if dto.Track.SortTitle == "" {
		dto.Track.SortTitle = domain.NormalizeSort(dto.Track.Title)
	}

	dto.Album.Title = firstTag(tags, "ALBUM")
	dto.Album.SortTitle = firstTag(tags, "ALBUMSORT", "TSOA", "soal")
	if dto.Album.SortTitle == "" {
		dto.Album.SortTitle = domain.NormalizeSort(dto.Album.Title)
	}

	yearStr := firstTag(tags, "DATE", "YEAR")
	if len(yearStr) >= 4 {
		dto.Track.Year, _ = strconv.Atoi(yearStr[:4])
		dto.Album.Year = dto.Track.Year
	}

	dto.Track.TrackNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")[0])
	dto.Track.DiscNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")[0])

	// Audio properties
	dto.Track.Duration = int(props.Length.Seconds())
	dto.Track.Bitrate = int(props.Bitrate)
	dto.Track.SampleRate = int(props.SampleRate)

	totalTracksStr := firstTag(tags, "TRACKTOTAL", "TOTALTRACKS")
	if totalTracksStr == "" {
		parts := strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")
		if len(parts) == 2 {
			totalTracksStr = parts[1]
		}
	}
	dto.Track.TotalTracks, _ = strconv.Atoi(totalTracksStr)

	totalDiscsStr := firstTag(tags, "DISCTOTAL", "TOTALDISCS")
	if totalDiscsStr == "" {
		parts := strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")
		if len(parts) == 2 {
			totalDiscsStr = parts[1]
		}
	}
	dto.Track.TotalDiscs, _ = strconv.Atoi(totalDiscsStr)

	return dto, nil
}

func splitMultipleTags(tags map[string][]string, keys ...string) []string {
	var all []string
	seen := make(map[string]bool)

	for _, key := range keys {
		for _, val := range tags[key] {
			if val == "" {
				continue
			}
			// Split each value further using our splitting logic
			parts := domain.SplitArtists(val)
			for _, p := range parts {
				lower := strings.ToLower(p)
				if !seen[lower] {
					all = append(all, p)
					seen[lower] = true
				}
			}
		}
	}
	return all
}

func (e *taglibExtractor) ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) {
	data, err := taglib.ReadImage(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read image from %s: %w", path, err)
	}

	if data == nil {
		return nil, "", nil
	}

	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read properties for artwork %s: %w", path, err)
	}

	mimeType := "image/jpeg"
	if len(props.Images) > 0 {
		mimeType = props.Images[0].MIMEType
	}

	return data, mimeType, nil
}

func firstTag(tags map[string][]string, keys ...string) string {
	for _, key := range keys {
		if vals, ok := tags[key]; ok && len(vals) > 0 && vals[0] != "" {
			return vals[0]
		}
	}
	return ""
}

func allTags(tags map[string][]string, sep string, keys ...string) string {
	var all []string
	seen := make(map[string]bool)

	for _, key := range keys {
		for _, val := range tags[key] {
			if val != "" && !seen[val] {
				all = append(all, val)
				seen[val] = true
			}
		}
	}
	return strings.Join(all, sep)
}
