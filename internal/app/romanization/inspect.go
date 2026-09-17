package romanization

import (
	"airmedy/internal/domain"
	"fmt"
	"unicode"
	"unicode/utf8"

	"golang.org/x/text/unicode/norm"
)

func validate(lines []string) error {
	if len(lines) > 2000 {
		return fmt.Errorf("romanization: maximum 2000 lines")
	}
	bytes := 0
	for _, line := range lines {
		bytes += len(line)
		if bytes > 128*1024 {
			return fmt.Errorf("romanization: maximum 128 KiB")
		}
		if !utf8.ValidString(line) || utf8.RuneCountInString(line) > 2048 {
			return fmt.Errorf("romanization: invalid UTF-8 or more than 2048 runes in a line")
		}
	}
	return nil
}

// Inspect never accesses an engine or loads a dictionary.
func Inspect(lines []string) (domain.RomanizationInspection, error) {
	result := domain.RomanizationInspection{Languages: []string{}}
	if err := validate(lines); err != nil {
		return result, err
	}
	var kana, hangul, han bool
	for _, line := range lines {
		for _, r := range norm.NFKC.String(line) {
			kana = kana || unicode.In(r, unicode.Hiragana, unicode.Katakana)
			hangul = hangul || (r >= 0xAC00 && r <= 0xD7A3)
			han = han || unicode.Is(unicode.Han, r)
		}
	}
	if kana {
		result.Languages = append(result.Languages, "ja")
	}
	if hangul {
		result.Languages = append(result.Languages, "ko")
	}
	if han && !kana {
		result.Languages = append(result.Languages, "zh")
		result.MandarinDefault = true
	}
	result.Supported = len(result.Languages) > 0
	return result, nil
}
