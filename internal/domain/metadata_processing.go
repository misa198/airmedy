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

	// This regex matches:
	// 1. Hard delimiters: , ; | / \ (one or more)
	// 2. Keywords: ft, feat, featuring, with, vs, &, and (case-insensitive)
	// All with optional surrounding whitespace.

	// Note: We use \b at the start and (?:\.|\b) at the end to handle optional dots
	// and ensure we don't split names like "Andrey" or "Brand".
	re := regexp.MustCompile(`(?i)\s*(?:[,;|\\]+|(?:\s+/\s+)+|\b(?:ft|feat|featuring|with|vs|and)(?:\.|\b)|&)\s*`)

	parts := re.Split(s, -1)
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
