package ttml

import (
	"math"
	"strings"
	"testing"
)

const wordTTML = `<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:lrc="http://lrc.red/lyric-ttml-internal" lrc:timing="Word">
  <head><metadata><translations><translation lang="fr">
    <text for="L1">Bonjour le monde</text>
  </translation></translations></metadata></head>
  <body>
    <div lrc:songPart="Verse">
      <p begin="1.000" end="3.000" lrc:key="L1"><span begin="1.000" end="1.500">Hello</span> <span begin="2.000" end="3.000">world</span></p>
      <p begin="0:04.000" end="0:06.500"><span begin="4.000" end="4.400">beau</span><span begin="4.400" end="5.000">tiful</span> <span ttm:role="x-bg"><span begin="5.200" end="6.000">ooh</span></span></p>
    </div>
  </body>
</tt>`

func TestToLRC_WordTimed(t *testing.T) {
	got, synced, err := ToLRC(wordTTML)
	if err != nil {
		t.Fatal(err)
	}
	if !synced {
		t.Fatal("expected synced output")
	}
	want := "[00:01.000]Hello<00:01.500> <00:02.000>world<00:03.000> ^ Bonjour le monde\n" +
		"[00:04.000]beau<00:04.400>tiful<00:05.000> <00:05.200>(ooh)<00:06.000>"
	if got != want {
		t.Fatalf("unexpected LRC\n got: %q\nwant: %q", got, want)
	}
}

func TestToLRC_LineTimed(t *testing.T) {
	in := `<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
<p begin="00:00:12.5" end="00:00:15">First line</p>
<p begin="75s" end="80s">Second &amp; last</p>
</div></body></tt>`
	got, synced, err := ToLRC(in)
	if err != nil || !synced {
		t.Fatalf("err=%v synced=%v", err, synced)
	}
	want := "[00:12.500]First line\n[01:15.000]Second & last"
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestToLRC_Untimed(t *testing.T) {
	in := `<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p>Just words</p><p>More words</p></div></body></tt>`
	got, synced, err := ToLRC(in)
	if err != nil || synced {
		t.Fatalf("err=%v synced=%v", err, synced)
	}
	if got != "Just words\nMore words" {
		t.Fatalf("got %q", got)
	}
}

func TestToLRC_NotTTML(t *testing.T) {
	if _, _, err := ToLRC("[00:01.00]plain lrc"); err != ErrNotTTML {
		t.Fatalf("expected ErrNotTTML, got %v", err)
	}
}

func TestIsModelSynced(t *testing.T) {
	cases := map[string]bool{
		`<tt xmlns:lrc="http://lrc.red/lyric-ttml-internal" lrc:byModel="true"><body/></tt>`:  true,
		`<tt xmlns:lrc="http://lrc.red/lyric-ttml-internal" lrc:byModel="false"><body/></tt>`: false,
		`<?xml version="1.0"?><tt lrc:byModel="true"><body/></tt>`:                            true,
		`<tt><body/></tt>`:                        false,
		`[00:01.00]not ttml byModel="true"`:       false,
		"\ufeff<tt byModel=\"TRUE\"><body/></tt>": true,
	}
	for in, want := range cases {
		if got := IsModelSynced(in); got != want {
			t.Errorf("IsModelSynced(%q) = %v, want %v", in, got, want)
		}
	}
}

func TestParseTTMLTime(t *testing.T) {
	cases := map[string]float64{
		"1.5":         1.5,
		"1:02.5":      62.5,
		"01:00:00.25": 3600.25,
		"250ms":       0.25,
		"2.5s":        2.5,
		"1.5m":        90,
		"1h":          3600,
	}
	for in, want := range cases {
		if got := parseTTMLTime(in); math.Abs(got-want) > 1e-9 {
			t.Errorf("parseTTMLTime(%q) = %v, want %v", in, got, want)
		}
	}
	if !math.IsNaN(parseTTMLTime("")) {
		t.Error("empty time should be NaN")
	}
}

func TestToLRC_MonotonicTags(t *testing.T) {
	// Background vocals overlapping the main line must not produce
	// decreasing timestamps (the frontend parser rejects those).
	in := `<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
<p begin="1" end="5"><span begin="1" end="4">long</span> <span ttm:role="x-bg"><span begin="2" end="3">bg</span></span></p>
</div></body></tt>`
	got, _, err := ToLRC(in)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(got, "long<00:04.000> (bg)<00:04.000>") {
		t.Fatalf("expected clamped bg timing, got %q", got)
	}
}
