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
	if e.japanese != nil || e.phrases != nil {
		t.Fatal("inspection loaded dictionaries")
	}
	for _, tc := range []struct {
		input, want string
		japanese    bool
	}{
		{"重庆银行", "chóng qìng yín háng", false},
		{"重慶銀行", "chóng qìng yín háng", false},
		{"Hello 你好!", "Hello nǐ hǎo!", false},
		{"Hello你好", "Hello nǐ hǎo", false},
		{"你好Hello", "nǐ hǎo Hello", false},
		{"東京Hello", "tōkyō Hello", true},
		{"안녕하세요, Hello!", "annyeonghaseyo, Hello!", false},
		{"같이 꽃잎", "gachi kkonnip", false},
		{"東京へ行く", "tōkyō e iku", true},
		{"私は学生です", "watashi wa gakusei desu", true},
		{"Hello 世界!", "Hello sekai!", true},
		{"ﾊﾛｰ", "harō", true},
		{"世界", "shì jiè", false},
		{"Hello 你好、한글!", "Hello nǐ hǎo、hangeul!", false},
	} {
		t.Run(tc.input, func(t *testing.T) {
			got, err := e.Romanize(context.Background(), tc.input, tc.japanese)
			if err != nil || got != tc.want {
				t.Fatalf("got %q (%v), want %q", got, err, tc.want)
			}
		})
	}
	e.Release()
	if e.japanese != nil || e.phrases != nil {
		t.Fatal("release retained dictionaries")
	}
}

func TestCancelledBeforeLoad(t *testing.T) {
	e := New()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	for _, text := range []string{"你好", "かな"} {
		if _, err := e.Romanize(ctx, text, true); err != context.Canceled {
			t.Fatal(err)
		}
	}
	if e.japanese != nil || e.phrases != nil {
		t.Fatal("cancelled request loaded dictionaries")
	}
}

func TestLongLineAndUnsupportedReading(t *testing.T) {
	e := New()
	defer e.Release()
	if _, err := e.Romanize(context.Background(), strings.Repeat("あ", 2048), true); err != nil {
		t.Fatal(err)
	}
	if _, err := e.Romanize(context.Background(), "𰻞", true); err == nil {
		t.Fatal("unknown kanji did not fail for bilingual fallback")
	}
}
