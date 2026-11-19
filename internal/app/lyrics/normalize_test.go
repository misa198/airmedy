package lyrics

import "testing"

func TestNormalizeLyricContent(t *testing.T) {
	ttmlDoc := `<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1.5" end="2">line</p></div></body></tt>`
	if got := normalizeLyricContent(ttmlDoc); got != "[00:01.500]line" {
		t.Fatalf("TTML not converted: %q", got)
	}
	if got := normalizeLyricContent("[00:01.00]lrc"); got != "[00:01.00]lrc" {
		t.Fatalf("LRC changed: %q", got)
	}
	bad := "<tt><body><p>"
	if got := normalizeLyricContent(bad); got != bad {
		t.Fatalf("malformed TTML should pass through, got %q", got)
	}
}
