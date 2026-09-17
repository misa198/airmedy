package romanization

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"strings"
	"unicode"

	"github.com/doxuta/hebon"
	"github.com/ikawaha/kagome-dict/dict"
	"github.com/ikawaha/kagome/v2/tokenizer"
)

func (e *Engine) loadJapanese(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if e.japanese != nil {
		return nil
	}
	file, err := dictionaries.Open("data/ipa.dict")
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }()
	stat, err := file.Stat()
	if err != nil {
		return err
	}
	reader, ok := file.(io.ReaderAt)
	if !ok {
		return fmt.Errorf("embedded IPA archive does not implement ReaderAt")
	}
	archive, err := zip.NewReader(reader, stat.Size())
	if err != nil {
		return err
	}
	// Own the dictionary rather than using ipa.Dict's permanent global singleton.
	dictionary, err := dict.Load(archive, true)
	if err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	t, err := tokenizer.New(dictionary, tokenizer.OmitBosEos())
	if err != nil {
		return err
	}
	e.japanese = t
	e.japaneseData = dictionary
	return ctx.Err()
}

func (e *Engine) romanizeJapanese(ctx context.Context, text string) (string, error) {
	if err := e.loadJapanese(ctx); err != nil {
		return "", err
	}
	var words []string
	for _, token := range e.japanese.Tokenize(text) {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		reading, ok := token.Pronunciation()
		if !ok || reading == "*" {
			reading, ok = token.Reading()
		}
		if !ok || reading == "*" {
			reading = token.Surface
		}
		roman := hebon.Romaji(reading, hebon.Options{Style: hebon.Macron})
		for _, r := range roman {
			if unicode.In(r, unicode.Han, unicode.Hiragana, unicode.Katakana) {
				return "", fmt.Errorf("no Japanese reading for %q", token.Surface)
			}
		}
		words = append(words, roman)
	}
	return strings.Join(words, " "), nil
}
