package domain

import (
	"fmt"
	"regexp"
	"strings"
	"unicode"

	"golang.org/x/text/runes"
	"golang.org/x/text/transform"
	"golang.org/x/text/unicode/norm"
)

var (
	articlesRegexp = regexp.MustCompile(`^(?i)(the|a|an)\s+`)
	numberRegexp   = regexp.MustCompile(`\d+`)
)

// NormalizeSort creates a string suitable for alphabetical sorting.
func NormalizeSort(s string) string {
	if s == "" {
		return ""
	}

	// 1. Article Stripping
	res := articlesRegexp.ReplaceAllString(s, "")

	// 2. Unicode Folding
	res = FoldUnicode(res)

	// 3. Sanitization: Remove leading punctuation and symbols only
	res = strings.TrimLeftFunc(res, func(r rune) bool {
		return unicode.IsPunct(r) || unicode.IsSymbol(r) || unicode.IsSpace(r)
	})

	// 4. Numeric Padding
	res = numberRegexp.ReplaceAllStringFunc(res, func(n string) string {
		return fmt.Sprintf("%04s", n)
	})

	return strings.TrimSpace(res)
}

// NormalizationKey creates a key for deduplication.
// It follows these rules:
// 1. Convert to lowercase.
// 2. Trim extra spaces.
// 3. Remove Vietnamese diacritics and other accents.
func NormalizationKey(s string) string {
	if s == "" {
		return ""
	}

	res := strings.ToLower(s)
	res = strings.Join(strings.Fields(res), " ") // Trim and collapse spaces
	res = FoldUnicode(res)

	return res
}

// FoldUnicode removes accents and diacritics from a string.
func FoldUnicode(s string) string {
	// NFKD normalization breaks characters into base + combining marks
	t := transform.Chain(norm.NFD, runes.Remove(runes.In(unicode.Mn)), norm.NFC)
	res, _, _ := transform.String(t, s)

	// Special handling for Vietnamese characters that don't fold well with Mn removal
	// e.g., 'đ' -> 'd'
	res = strings.ReplaceAll(res, "đ", "d")
	res = strings.ReplaceAll(res, "Đ", "D")

	return res
}

// SplitArtists breaks down concatenated artist names into individual artists.
// It uses hard delimiters and keywords, prioritizing hard delimiters.
func SplitArtists(s string) []string {
	if s == "" {
		return nil
	}

	// Hard delimiters: , ; |
	// Keywords: ft., feat., featuring, with, vs., &, and
	
	// We use a multi-stage split approach.
	// First, replace all delimiters/keywords with a unique marker.
	
	delimiters := []string{",", ";", "|"}
	keywords := []string{" ft. ", " feat. ", " featuring ", " with ", " vs. ", " & ", " and "}

	res := s
	marker := "___ARTIST_SEP___"

	for _, d := range delimiters {
		res = strings.ReplaceAll(res, d, marker)
	}

	// For keywords, we need case-insensitive replacement
	for _, k := range keywords {
		re := regexp.MustCompile(`(?i)` + regexp.QuoteMeta(k))
		res = re.ReplaceAllString(res, marker)
	}
	
	// Also handle keywords at the end of words like "Artist feat.Artist" (no spaces)
	// But usually they have spaces. Let's be careful.
	// feat. without space after it
	extraKeywords := []string{"ft.", "feat."}
	for _, k := range extraKeywords {
		re := regexp.MustCompile(`(?i)\s+` + regexp.QuoteMeta(k))
		res = re.ReplaceAllString(res, marker)
	}

	parts := strings.Split(res, marker)
	var final []string
	seen := make(map[string]bool)

	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" && !seen[strings.ToLower(p)] {
			final = append(final, p)
			seen[strings.ToLower(p)] = true
		}
	}

	return final
}
