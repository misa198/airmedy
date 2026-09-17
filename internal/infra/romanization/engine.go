// Package romanization supplies offline, worker-owned romanization engines.
package romanization

import (
	"context"
	"fmt"
	"strings"
	"unicode"

	"github.com/ikawaha/kagome-dict/dict"
	"github.com/ikawaha/kagome/v2/tokenizer"
	"github.com/misa198/koreanromanizer"
	"golang.org/x/text/unicode/norm"
)

type Engine struct {
	japanese     *tokenizer.Tokenizer
	japaneseData *dict.Dict
	phrases      map[string]string
	simplified   map[rune]rune
	maxPhrase    int
}

func New() *Engine { return &Engine{} }
func (*Engine) Version() string {
	return "romanization-v1:kagome-2.11.0:ipa-a5b2073:hebon-b6abdee:pinyin-0.21.0:phrase-cee0ed6:opencc-5c764a5:koroman-e529729"
}
func (e *Engine) Release() {
	// Kagome's pooled lattice retains its dictionary pointer after Free.
	// We exclusively own this dictionary; clear its heavy fields before dropping
	// our references so even a pooled lattice cannot keep the data alive.
	if e.japaneseData != nil {
		*e.japaneseData = dict.Dict{}
	}
	e.japaneseData = nil
	e.japanese = nil
	e.phrases = nil
	e.simplified = nil
	e.maxPhrase = 0
}

// script returns the engine for a run, leaving all other characters untouched.
func script(r rune, japanese bool) byte {
	if r >= 0xAC00 && r <= 0xD7A3 || r >= 0x1100 && r <= 0x1112 || r >= 0x1161 && r <= 0x1175 || r >= 0x11A8 && r <= 0x11C2 {
		return 'k'
	}
	if unicode.In(r, unicode.Hiragana, unicode.Katakana) || r == 'ー' || r == 'ｰ' || r == '゙' || r == '゚' || r == 'ﾞ' || r == 'ﾟ' {
		return 'j'
	}
	if unicode.Is(unicode.Han, r) {
		if japanese {
			return 'j'
		}
		return 'c'
	}
	return 0
}

func needsRomanizationSpace(previous, current rune, japanese bool) bool {
	previousCJK := script(previous, japanese) != 0
	currentCJK := script(current, japanese) != 0
	return (unicode.Is(unicode.Latin, previous) && currentCJK) || (previousCJK && unicode.Is(unicode.Latin, current))
}

func (e *Engine) Romanize(ctx context.Context, text string, japanese bool) (string, error) {
	var out strings.Builder
	runes := []rune(text)
	for start := 0; start < len(runes); {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		kind := script(runes[start], japanese)
		end := start + 1
		for end < len(runes) && script(runes[end], japanese) == kind {
			end++
		}
		run := string(runes[start:end])
		var converted string
		var err error
		switch kind {
		case 'k':
			converted = koreanromanizer.Romanize(norm.NFC.String(run))
		case 'j':
			converted, err = e.romanizeJapanese(ctx, norm.NFKC.String(run))
		case 'c':
			converted, err = e.romanizeChinese(ctx, norm.NFC.String(run))
		default:
			converted = run
		}
		if err != nil {
			return "", fmt.Errorf("romanize run: %w", err)
		}
		if start > 0 && needsRomanizationSpace(runes[start-1], runes[start], japanese) {
			out.WriteByte(' ')
		}
		out.WriteString(converted)
		start = end
	}
	return out.String(), ctx.Err()
}
