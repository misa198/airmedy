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

func (e *taglibExtractor) Extract(ctx context.Context, path string) (*domain.Track, error) {
	tags, err := taglib.ReadTags(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read tags from %s: %w", path, err)
	}

	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read properties from %s: %w", path, err)
	}

	track := &domain.Track{
		Path:   path,
		Format: strings.TrimPrefix(filepath.Ext(path), "."),
	}

	// Basic tags
	track.Title = firstTag(tags, "TITLE")
	track.ArtistName = firstTag(tags, "ARTIST")
	track.AlbumName = firstTag(tags, "ALBUM")
	track.GenreName = firstTag(tags, "GENRE")
	track.ComposerName = firstTag(tags, "COMPOSER")

	yearStr := firstTag(tags, "DATE", "YEAR")
	if len(yearStr) >= 4 {
		track.Year, _ = strconv.Atoi(yearStr[:4])
	}

	track.TrackNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")[0])
	track.DiscNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")[0])

	// Audio properties
	track.Duration = int(props.Length.Seconds())
	track.Bitrate = int(props.Bitrate)
	track.SampleRate = int(props.SampleRate)

	// Extra tags / Sort fields
	track.SortTitle = firstTag(tags, "TITLESORT", "TSOT", "sonm")
	track.SortArtistName = firstTag(tags, "ARTISTSORT", "TSOP", "soar")
	track.SortAlbumName = firstTag(tags, "ALBUMSORT", "TSOA", "soal")
	track.AlbumArtistName = firstTag(tags, "ALBUMARTIST", "TPE2", "aART")
	track.SortAlbumArtistName = firstTag(tags, "ALBUMARTISTSORT", "TSO2", "soaa")

	totalTracksStr := firstTag(tags, "TRACKTOTAL", "TOTALTRACKS")
	if totalTracksStr == "" {
		parts := strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")
		if len(parts) == 2 {
			totalTracksStr = parts[1]
		}
	}
	track.TotalTracks, _ = strconv.Atoi(totalTracksStr)

	totalDiscsStr := firstTag(tags, "DISCTOTAL", "TOTALDISCS")
	if totalDiscsStr == "" {
		parts := strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")
		if len(parts) == 2 {
			totalDiscsStr = parts[1]
		}
	}
	track.TotalDiscs, _ = strconv.Atoi(totalDiscsStr)

	// Apply Fallbacks
	applySortFallbacks(track)

	return track, nil
}

func (e *taglibExtractor) ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) {
	// taglib.ReadImage returns the first image data
	data, err := taglib.ReadImage(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read image from %s: %w", path, err)
	}

	if data == nil {
		return nil, "", nil
	}

	// We need mime type but ReadImage only returns data.
	// We might need ReadProperties to get Images info.
	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read properties for artwork %s: %w", path, err)
	}

	if len(props.Images) == 0 {
		return data, "image/jpeg", nil // fallback mime
	}

	return data, props.Images[0].MIMEType, nil
}

func firstTag(tags map[string][]string, keys ...string) string {
	for _, key := range keys {
		if vals, ok := tags[key]; ok && len(vals) > 0 && vals[0] != "" {
			return vals[0]
		}
	}
	return ""
}

func applySortFallbacks(track *domain.Track) {
	if track.SortTitle == "" {
		track.SortTitle = cleanSortString(track.Title)
	}
	if track.SortArtistName == "" {
		track.SortArtistName = cleanSortString(track.ArtistName)
	}
	if track.SortAlbumName == "" {
		track.SortAlbumName = cleanSortString(track.AlbumName)
	}
	if track.AlbumArtistName == "" {
		track.AlbumArtistName = track.ArtistName
	}
	if track.SortAlbumArtistName == "" {
		track.SortAlbumArtistName = cleanSortString(track.AlbumArtistName)
	}
}

func cleanSortString(s string) string {
	lower := strings.ToLower(s)
	prefixes := []string{"the ", "a ", "an "}
	for _, prefix := range prefixes {
		if strings.HasPrefix(lower, prefix) {
			return s[len(prefix):]
		}
	}
	return s
}
