package metadata

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"airmedy/internal/domain"

	taglib "github.com/misa198/go-taglib"
)

var lrcPattern = regexp.MustCompile(`\[\d+:\d+\.\d+\]`)

func NewTagLibWriter() domain.MetadataWriter {
	return &taglibExtractor{}
}

type taglibExtractor struct{}

func NewTagLibExtractor() domain.MetadataExtractor {
	return &taglibExtractor{}
}

func (e *taglibExtractor) Extract(ctx context.Context, path string) (*domain.TrackDTO, error) {
	tags, err := taglib.ReadTags(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read tags: %w", err)
	}

	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read properties: %w", err)
	}

	dto := &domain.TrackDTO{
		Track: domain.Track{
			Path:   path,
			Format: strings.TrimPrefix(filepath.Ext(path), "."),
		},
		Album: &domain.Album{},
	}

	// Capture all metadata as JSON for migration/fallback
	if metadataJSON, err := json.Marshal(tags); err == nil {
		dto.OtherMetadata = string(metadataJSON)
	}

	// Raw values for display. Splitting into individual artist/genre/composer
	// entities happens in the library layer (LibraryService.buildEntitiesFromRaw)
	// using the user-configured delimiters, so import and resync share one path.
	dto.RawArtistNames = allTags(tags, domain.RawTagSeparator, "ARTIST", "TPE1", "©ART")
	dto.RawAlbumArtistNames = allTags(tags, domain.RawTagSeparator, "ALBUMARTIST", "ALBUM ARTIST", "TPE2", "aART", "BAND")
	dto.RawGenreNames = allTags(tags, domain.RawTagSeparator, "GENRE", "TCON", "©gen")
	dto.RawComposerNames = allTags(tags, domain.RawTagSeparator, "COMPOSER", "TCOM", "©wrt")

	// Basic tags
	dto.Title = firstTag(tags, "TITLE")
	dto.SortTitle = firstTag(tags, "TITLESORT", "TSOT", "sonm")
	if dto.SortTitle == "" {
		dto.SortTitle = domain.NormalizeSort(dto.Title)
	}

	dto.Copyright = firstTag(tags, "COPYRIGHT", "TCOP", "cprt", "©cpr")
	dto.Album.Copyright = dto.Copyright

	dto.BPM, _ = strconv.Atoi(firstTag(tags, "BPM", "TBPM", "tmpo"))
	dto.Label = firstTag(tags, "LABEL", "PUBLISHER", "TPUB", "pub ")
	dto.ISRC = firstTag(tags, "ISRC", "TSRC")

	dto.Album.Title = firstTag(tags, "ALBUM")
	dto.Album.SortTitle = firstTag(tags, "ALBUMSORT", "TSOA", "soal")
	if dto.Album.SortTitle == "" {
		dto.Album.SortTitle = domain.NormalizeSort(dto.Album.Title)
	}

	yearStr := firstTag(tags, "DATE", "YEAR")
	if len(yearStr) >= 4 {
		dto.Year, _ = strconv.Atoi(yearStr[:4])
		dto.Album.Year = dto.Year
	}

	dto.TrackNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")[0])
	dto.DiscNumber, _ = strconv.Atoi(strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")[0])

	// Audio properties
	dto.Duration = int(props.Length.Seconds())
	dto.Bitrate = int(props.BitRate)
	dto.SampleRate = int(props.SampleRate)
	dto.BitDepth = int(props.BitDepth)
	dto.Codec = props.InnerCodec

	totalTracksStr := firstTag(tags, "TRACKTOTAL", "TOTALTRACKS")
	if totalTracksStr == "" {
		parts := strings.Split(firstTag(tags, "TRACKNUMBER", "TRACK"), "/")
		if len(parts) == 2 {
			totalTracksStr = parts[1]
		}
	}
	dto.TotalTracks, _ = strconv.Atoi(totalTracksStr)

	totalDiscsStr := firstTag(tags, "DISCTOTAL", "TOTALDISCS")
	if totalDiscsStr == "" {
		parts := strings.Split(firstTag(tags, "DISCNUMBER", "DISC"), "/")
		if len(parts) == 2 {
			totalDiscsStr = parts[1]
		}
	}
	dto.TotalDiscs, _ = strconv.Atoi(totalDiscsStr)

	return dto, nil
}

func (e *taglibExtractor) ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) {
	data, err := taglib.ReadImage(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read image: %w", err)
	}

	if data == nil {
		return nil, "", nil
	}

	props, err := taglib.ReadProperties(path)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read properties for artwork: %w", err)
	}

	mimeType := "image/jpeg"
	if len(props.Images) > 0 {
		mimeType = props.Images[0].MIMEType
	}

	return data, mimeType, nil
}

func (e *taglibExtractor) ExtractLyrics(_ context.Context, path string) (string, bool, error) {
	tags, err := taglib.ReadTags(path)
	if err != nil {
		return "", false, fmt.Errorf("failed to read tags: %w", err)
	}
	content := firstTag(tags, "LYRICS")
	if content == "" {
		return "", false, nil
	}
	return content, lrcPattern.MatchString(content), nil
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

func (e *taglibExtractor) WriteMetadata(_ context.Context, path string, fields domain.MetadataUpdate) error {
	tags := map[string][]string{
		"TITLE":       {fields.Title},
		"ARTIST":      {fields.Artist},
		"ALBUMARTIST": {fields.AlbumArtist},
		"ALBUM":       {fields.AlbumTitle},
		"DATE":        {strconv.Itoa(fields.Year)},
		"TRACKNUMBER": {fmt.Sprintf("%d/%d", fields.TrackNumber, fields.TotalTracks)},
		"DISCNUMBER":  {fmt.Sprintf("%d/%d", fields.DiscNumber, fields.TotalDiscs)},
		"GENRE":       {fields.Genre},
		"COMPOSER":    {fields.Composer},
		"BPM":         {strconv.Itoa(fields.BPM)},
		"LABEL":       {fields.Label},
		"ISRC":        {fields.ISRC},
		"LYRICS":      {fields.Lyrics},
	}
	if err := taglib.WriteTags(path, tags, taglib.Clear); err != nil {
		return fmt.Errorf("failed to write metadata to %s: %w", path, err)
	}

	if len(fields.ArtworkData) > 0 {
		if err := taglib.WriteImage(path, fields.ArtworkData); err != nil {
			return fmt.Errorf("failed to write artwork to %s: %w", path, err)
		}
	}

	return nil
}
