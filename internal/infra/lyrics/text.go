package lyrics

import (
	"regexp"
	"strings"
)

var noisePatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)\s*\(feat\.?[^)]*\)`),
	regexp.MustCompile(`(?i)\s*\[feat\.?[^]]*\]`),
	regexp.MustCompile(`(?i)\s*\(ft\.?[^)]*\)`),
	regexp.MustCompile(`(?i)\s*\((official\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\)`),
	regexp.MustCompile(`(?i)\s*\[(official\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\]`),
}

var featuredRe = regexp.MustCompile(`(?i)\s*[\(\[]fe?a?t\.?\s*([^\)\]]+)[\)\]]`)

func normalizeText(s string) string {
	for _, re := range noisePatterns {
		s = re.ReplaceAllString(s, "")
	}
	s = strings.ToLower(strings.TrimSpace(s))
	return strings.Join(strings.Fields(s), " ")
}

func extractFeatured(title string) (cleanTitle, featured string) {
	m := featuredRe.FindStringSubmatch(title)
	if m == nil {
		return title, ""
	}
	return strings.TrimSpace(featuredRe.ReplaceAllString(title, "")), strings.TrimSpace(m[1])
}
