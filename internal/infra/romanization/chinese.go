package romanization

import (
	"bufio"
	"context"
	"embed"
	"fmt"
	"strings"
	"unicode/utf8"

	"github.com/mozillazg/go-pinyin"
)

//go:embed data/*
var dictionaries embed.FS

func (e *Engine) loadChinese(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if e.phrases != nil {
		return nil
	}
	file, err := dictionaries.Open("data/pinyin.txt")
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }()
	phrases := make(map[string]string)
	maxLength := 0
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		if err := ctx.Err(); err != nil {
			return err
		}
		line := scanner.Text()
		if strings.HasPrefix(line, "#") {
			continue
		}
		key, value, ok := strings.Cut(line, ": ")
		if !ok {
			continue
		}
		phrases[key] = strings.TrimSpace(value)
		maxLength = max(maxLength, utf8.RuneCountInString(key))
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	variants, err := dictionaries.Open("data/TSCharacters.txt")
	if err != nil {
		return err
	}
	defer func() { _ = variants.Close() }()
	simplified := make(map[rune]rune)
	scanner = bufio.NewScanner(variants)
	for scanner.Scan() {
		if err := ctx.Err(); err != nil {
			return err
		}
		fields := strings.Fields(scanner.Text())
		if len(fields) < 2 || strings.HasPrefix(fields[0], "#") {
			continue
		}
		from, _ := utf8.DecodeRuneInString(fields[0])
		to, _ := utf8.DecodeRuneInString(fields[1])
		simplified[from] = to
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	e.phrases, e.maxPhrase = phrases, maxLength
	e.simplified = simplified
	return nil
}

func (e *Engine) romanizeChinese(ctx context.Context, text string) (string, error) {
	if err := e.loadChinese(ctx); err != nil {
		return "", err
	}
	runes := []rune(text)
	canonical := append([]rune(nil), runes...)
	for i, r := range canonical {
		if simplified, ok := e.simplified[r]; ok {
			canonical[i] = simplified
		}
	}
	words := make([]string, 0, len(runes))
	args := pinyin.NewArgs()
	args.Style = pinyin.Tone
	for i := 0; i < len(runes); {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		n := min(e.maxPhrase, len(runes)-i)
		for ; n > 1; n-- {
			value, ok := e.phrases[string(runes[i:i+n])]
			if !ok {
				value, ok = e.phrases[string(canonical[i:i+n])]
			}
			if ok {
				words = append(words, value)
				break
			}
		}
		if n <= 1 {
			n = 1
			value := pinyin.SinglePinyin(runes[i], args)
			if len(value) == 0 {
				return "", fmt.Errorf("no reading for %U", runes[i])
			}
			words = append(words, value[0])
		}
		i += n
	}
	return strings.Join(words, " "), nil
}
