// Package ttml converts TTML (Timed Text Markup Language) lyrics to LRC.
package ttml

import (
	"bytes"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"math"
	"strconv"
	"strings"
	"unicode"
)

// TTML (Timed Text Markup Language) support.
//
// Airmedy stores and renders lyrics as LRC, with optional enhanced word-level
// tags (`[mm:ss.xxx]word<mm:ss.xxx> word<mm:ss.xxx>`). TTML lyrics (Apple
// Music style, as served by lrc.red and others) are converted to that format so
// every consumer (desktop renderer, mobile sync, .lrc export) keeps working.
//
// Mapping:
//   - <p begin end> becomes a line `[begin]`.
//   - <span begin end> becomes a word; a closing `<end>` tag is emitted after
//     every word and, when a word starts after the previous one ended, an
//     opening `<begin>` tag preserves the gap.
//   - Background vocals (`ttm:role="x-bg"`) are appended in parentheses.
//   - Translations (`<text for="key">` in the head) become the secondary line,
//     using the existing ` ^ ` bilingual separator.
//   - Lines without timing yield plain text.

// ErrNotTTML is returned when content is not a TTML document.
var ErrNotTTML = errors.New("not a TTML document")

// IsTTML reports whether content looks like a TTML document.
func IsTTML(content string) bool {
	s := strings.TrimSpace(strings.TrimPrefix(content, "\ufeff"))
	if !strings.HasPrefix(s, "<") {
		return false
	}
	head := s
	if len(head) > 2048 {
		head = head[:2048]
	}
	return strings.Contains(head, "<tt ") || strings.Contains(head, "<tt>") || strings.Contains(head, ":tt ")
}

// IsModelSynced reports whether the TTML root declares its timing as
// machine-generated (`<tt lrc:byModel="true">`, as served by lrc.red).
func IsModelSynced(content string) bool {
	if !IsTTML(content) {
		return false
	}
	dec := xml.NewDecoder(strings.NewReader(strings.TrimPrefix(content, "\ufeff")))
	dec.Strict = false
	for {
		tok, err := dec.Token()
		if err != nil {
			return false
		}
		if el, ok := tok.(xml.StartElement); ok {
			if el.Name.Local != "tt" {
				return false
			}
			for _, a := range el.Attr {
				if a.Name.Local == "byModel" {
					return strings.EqualFold(strings.TrimSpace(a.Value), "true")
				}
			}
			return false
		}
	}
}

type ttmlNode struct {
	name     string
	attrs    map[string]string // keyed by local name
	children []any             // *ttmlNode or string
}

func (n *ttmlNode) attr(local string) string { return n.attrs[local] }

func (n *ttmlNode) hasElementChildren() bool {
	for _, c := range n.children {
		if _, ok := c.(*ttmlNode); ok {
			return true
		}
	}
	return false
}

func (n *ttmlNode) text() string {
	var b strings.Builder
	for _, c := range n.children {
		switch v := c.(type) {
		case string:
			b.WriteString(v)
		case *ttmlNode:
			b.WriteString(v.text())
		}
	}
	return b.String()
}

func (n *ttmlNode) find(name string, out *[]*ttmlNode) {
	for _, c := range n.children {
		if el, ok := c.(*ttmlNode); ok {
			if el.name == name {
				*out = append(*out, el)
			}
			el.find(name, out)
		}
	}
}

func parseTTMLTree(content string) (*ttmlNode, error) {
	dec := xml.NewDecoder(bytes.NewReader([]byte(content)))
	dec.Strict = false
	dec.Entity = xml.HTMLEntity
	root := &ttmlNode{name: "#document"}
	stack := []*ttmlNode{root}
	for {
		tok, err := dec.Token()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("malformed TTML: %w", err)
		}
		cur := stack[len(stack)-1]
		switch t := tok.(type) {
		case xml.StartElement:
			el := &ttmlNode{name: t.Name.Local, attrs: make(map[string]string, len(t.Attr))}
			for _, a := range t.Attr {
				el.attrs[a.Name.Local] = a.Value
			}
			cur.children = append(cur.children, el)
			stack = append(stack, el)
		case xml.EndElement:
			if len(stack) > 1 {
				stack = stack[:len(stack)-1]
			}
		case xml.CharData:
			cur.children = append(cur.children, string(t))
		}
	}
	for _, c := range root.children {
		if el, ok := c.(*ttmlNode); ok && el.name == "tt" {
			return el, nil
		}
	}
	return nil, ErrNotTTML
}

// parseTTMLTime parses TTML clock values ("1:02.345", "00:01:02.345") and
// offset values ("62.345s", "62345ms", "1.5m", "1h"). Returns NaN when empty or
// invalid.
func parseTTMLTime(v string) float64 {
	v = strings.TrimSpace(v)
	if v == "" {
		return math.NaN()
	}
	parse := func(s string) float64 {
		f, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return math.NaN()
		}
		return f
	}
	switch {
	case strings.HasSuffix(v, "ms"):
		return parse(strings.TrimSuffix(v, "ms")) / 1000
	case strings.HasSuffix(v, "s"):
		return parse(strings.TrimSuffix(v, "s"))
	case strings.HasSuffix(v, "m"):
		return parse(strings.TrimSuffix(v, "m")) * 60
	case strings.HasSuffix(v, "h"):
		return parse(strings.TrimSuffix(v, "h")) * 3600
	}
	total := 0.0
	for _, part := range strings.Split(v, ":") {
		total = total*60 + parse(part)
	}
	return total
}

type ttmlWord struct {
	text     string
	tail     string // trailing whitespace ("" or " ")
	begin    float64
	end      float64
	explicit bool // span carried its own timing
}

type ttmlLine struct {
	begin       float64
	end         float64
	words       []ttmlWord
	bg          []ttmlWord
	translation string
}

func collectTTMLWords(n *ttmlNode, words, bg *[]ttmlWord, begin, end float64, explicit bool) {
	for _, c := range n.children {
		switch v := c.(type) {
		case string:
			core := strings.TrimSpace(v)
			if core == "" {
				if v != "" && len(*words) > 0 {
					(*words)[len(*words)-1].tail = " "
				}
				continue
			}
			if unicode.IsSpace(rune(v[0])) && len(*words) > 0 {
				(*words)[len(*words)-1].tail = " "
			}
			tail := ""
			if unicode.IsSpace(rune(v[len(v)-1])) {
				tail = " "
			}
			*words = append(*words, ttmlWord{text: core, tail: tail, begin: begin, end: end, explicit: explicit})
		case *ttmlNode:
			if v.name != "span" {
				continue
			}
			b, e := parseTTMLTime(v.attr("begin")), parseTTMLTime(v.attr("end"))
			own := !math.IsNaN(b)
			if math.IsNaN(b) {
				b = begin
			}
			if math.IsNaN(e) {
				e = end
			}
			switch {
			case v.attr("role") == "x-bg":
				collectTTMLWords(v, bg, bg, b, e, own || explicit)
			case v.hasElementChildren():
				collectTTMLWords(v, words, bg, b, e, own || explicit)
			default:
				raw := v.text()
				core := strings.TrimSpace(raw)
				if core == "" {
					if raw != "" && len(*words) > 0 {
						(*words)[len(*words)-1].tail = " "
					}
					continue
				}
				if unicode.IsSpace(rune(raw[0])) && len(*words) > 0 {
					(*words)[len(*words)-1].tail = " "
				}
				tail := ""
				if unicode.IsSpace(rune(raw[len(raw)-1])) {
					tail = " "
				}
				*words = append(*words, ttmlWord{text: core, tail: tail, begin: b, end: e, explicit: own})
			}
		}
	}
}

func joinTTMLWords(ws []ttmlWord) string {
	var b strings.Builder
	for _, w := range ws {
		b.WriteString(w.text)
		b.WriteString(w.tail)
	}
	return strings.TrimSpace(b.String())
}

func wrapBackground(s string) string {
	if strings.HasPrefix(s, "(") && strings.HasSuffix(s, ")") {
		return s
	}
	return "(" + s + ")"
}

func formatLRCTime(sec float64) string {
	if sec < 0 || math.IsNaN(sec) {
		sec = 0
	}
	ms := int64(math.Round(sec * 1000))
	return fmt.Sprintf("%02d:%02d.%03d", ms/60000, (ms/1000)%60, ms%1000)
}

func allExplicit(ws []ttmlWord) bool {
	for _, w := range ws {
		if !w.explicit || math.IsNaN(w.begin) || math.IsNaN(w.end) {
			return false
		}
	}
	return len(ws) > 0
}

// renderWordTimed writes `text<end>` pairs, inserting `<begin>` when a word
// starts after the cursor. Times are clamped to be non-decreasing, which the
// frontend parser requires.
func renderWordTimed(b *strings.Builder, ws []ttmlWord, cursor float64, prefix, suffix string) float64 {
	for i, w := range ws {
		begin := math.Max(w.begin, cursor)
		if begin-cursor > 0.001 {
			b.WriteString("<" + formatLRCTime(begin) + ">")
		}
		text := w.text
		if i == 0 {
			text = prefix + text
		}
		if i == len(ws)-1 {
			text += suffix
		}
		end := math.Max(w.end, begin)
		b.WriteString(text)
		b.WriteString("<" + formatLRCTime(end) + ">")
		if i < len(ws)-1 {
			b.WriteString(w.tail)
		}
		cursor = end
	}
	return cursor
}

// ToLRC converts a TTML document into LRC text. synced reports
// whether the output carries timestamps; when false the output is plain text.
func ToLRC(content string) (lrc string, synced bool, err error) {
	if !IsTTML(content) {
		return "", false, ErrNotTTML
	}
	tt, err := parseTTMLTree(strings.TrimPrefix(content, "\ufeff"))
	if err != nil {
		return "", false, err
	}

	translations := map[string]string{}
	var texts []*ttmlNode
	tt.find("text", &texts)
	for _, t := range texts {
		if key := t.attr("for"); key != "" {
			if _, seen := translations[key]; !seen {
				translations[key] = strings.TrimSpace(t.text())
			}
		}
	}

	var bodies []*ttmlNode
	tt.find("body", &bodies)
	if len(bodies) == 0 {
		return "", false, nil
	}
	var paragraphs []*ttmlNode
	bodies[0].find("p", &paragraphs)

	var lines []ttmlLine
	for _, p := range paragraphs {
		l := ttmlLine{begin: parseTTMLTime(p.attr("begin")), end: parseTTMLTime(p.attr("end"))}
		collectTTMLWords(p, &l.words, &l.bg, l.begin, l.end, false)
		if len(l.words) == 0 && len(l.bg) == 0 {
			continue
		}
		if key := p.attr("key"); key != "" {
			l.translation = translations[key]
		}
		lines = append(lines, l)
	}
	if len(lines) == 0 {
		return "", false, nil
	}

	for _, l := range lines {
		if !math.IsNaN(l.begin) {
			synced = true
			break
		}
	}

	var out strings.Builder
	for _, l := range lines {
		var b strings.Builder
		if !synced || math.IsNaN(l.begin) {
			text := joinTTMLWords(l.words)
			if len(l.bg) > 0 {
				text = strings.TrimSpace(text + " " + wrapBackground(joinTTMLWords(l.bg)))
			}
			if !synced {
				out.WriteString(text)
				if l.translation != "" {
					out.WriteString(" ^ " + l.translation)
				}
				out.WriteString("\n")
			}
			continue
		}

		b.WriteString("[" + formatLRCTime(l.begin) + "]")
		if allExplicit(l.words) && (len(l.bg) == 0 || allExplicit(l.bg)) {
			cursor := renderWordTimed(&b, l.words, l.begin, "", "")
			if len(l.bg) > 0 {
				if len(l.words) > 0 {
					b.WriteString(" ")
				}
				bgText := joinTTMLWords(l.bg)
				prefix, suffix := "(", ")"
				if strings.HasPrefix(bgText, "(") && strings.HasSuffix(bgText, ")") {
					prefix, suffix = "", ""
				}
				renderWordTimed(&b, l.bg, cursor, prefix, suffix)
			}
		} else {
			text := joinTTMLWords(l.words)
			if len(l.bg) > 0 {
				text = strings.TrimSpace(text + " " + wrapBackground(joinTTMLWords(l.bg)))
			}
			b.WriteString(text)
		}
		if l.translation != "" {
			b.WriteString(" ^ " + l.translation)
		}
		out.WriteString(b.String())
		out.WriteString("\n")
	}
	return strings.TrimRight(out.String(), "\n"), synced, nil
}
