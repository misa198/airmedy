package romanization

import (
	"airmedy/internal/app/romanization"
	"context"
	"strings"
	"testing"
)

func TestOfflineReadings(t *testing.T) {
	e := New()
	defer e.Release()
	if _, err := romanization.Inspect([]string{"世界は美しい", "重庆", "한글"}); err != nil {
		t.Fatal(err)
	}
	if e.phrases != nil {
		t.Fatal("inspection loaded dictionaries")
	}
	for _, tc := range []struct {
		input, want string
	}{
		{"重庆银行", "chóng qìng yín háng"},
		{"重慶銀行", "chóng qìng yín háng"},
		{"Hello 你好!", "Hello nǐ hǎo!"},
		{"Hello你好", "Hello nǐ hǎo"},
		{"你好Hello", "nǐ hǎo Hello"},
		{"안녕하세요, Hello!", "annyeonghaseyo, Hello!"},
		{"같이 꽃잎", "gachi kkonnip"},
		{"世界", "shì jiè"},
		{"Hello 你好、한글!", "Hello nǐ hǎo、hangeul!"},
	} {
		t.Run(tc.input, func(t *testing.T) {
			got, err := e.Romanize(context.Background(), tc.input)
			if err != nil || got != tc.want {
				t.Fatalf("got %q (%v), want %q", got, err, tc.want)
			}
		})
	}
	e.Release()
	if e.phrases != nil {
		t.Fatal("release retained dictionaries")
	}
}

func TestCancelledBeforeLoad(t *testing.T) {
	e := New()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	for _, text := range []string{"你好", "한글"} {
		if _, err := e.Romanize(ctx, text); err != context.Canceled {
			t.Fatal(err)
		}
	}
	if e.phrases != nil {
		t.Fatal("cancelled request loaded dictionaries")
	}
}

func TestLongLine(t *testing.T) {
	e := New()
	defer e.Release()
	if _, err := e.Romanize(context.Background(), strings.Repeat("中", 2048)); err != nil {
		t.Fatal(err)
	}
}
