package playlist

import (
	"strings"
	"testing"

	"airmedy/internal/domain"
)

func TestBuildWhereClause_EmptyRules(t *testing.T) {
	where, args, err := BuildWhereClause(domain.SmartRuleGroup{Match: "all"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if where != "1=1" || len(args) != 0 {
		t.Fatalf("expected 1=1 with no args, got %q %v", where, args)
	}
}

func TestBuildWhereClause_ColumnRules(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{
			{Field: "year", Op: "between", Value: []any{1990.0, 1999.0}},
			{Field: "is_favorite", Op: "is", Value: true},
		},
	}
	where, args, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(where, "t.year BETWEEN ? AND ?") {
		t.Errorf("expected year between clause, got %q", where)
	}
	if !strings.Contains(where, "t.is_favorite = ?") {
		t.Errorf("expected is_favorite clause, got %q", where)
	}
	if !strings.Contains(where, " AND ") {
		t.Errorf("expected AND join for match=all, got %q", where)
	}
	if len(args) != 3 {
		t.Fatalf("expected 3 args, got %d: %v", len(args), args)
	}
}

func TestBuildWhereClause_BitrateField(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "bitrate", Op: "gte", Value: 320.0}},
	}
	where, args, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(where, "t.bitrate >= ?") {
		t.Errorf("expected bitrate clause, got %q", where)
	}
	if len(args) != 1 || args[0] != 320.0 {
		t.Fatalf("expected [320] args, got %v", args)
	}
}

func TestBuildWhereClause_MatchAny(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "any",
		Rules: []domain.SmartRule{
			{Field: "bpm", Op: "gt", Value: 120.0},
			{Field: "bpm", Op: "lt", Value: 90.0},
		},
	}
	where, _, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(where, " OR ") {
		t.Errorf("expected OR join for match=any, got %q", where)
	}
}

func TestBuildWhereClause_RelationField(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "genre", Op: "is", Value: "Rock"}},
	}
	where, args, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(where, "EXISTS (SELECT 1 FROM track_genres j JOIN genres n ON j.genre_id = n.id") {
		t.Errorf("expected genre EXISTS subquery, got %q", where)
	}
	if len(args) != 1 || args[0] != "Rock" {
		t.Fatalf("expected [Rock] args, got %v", args)
	}
}

func TestBuildWhereClause_AddedAt(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "added_at", Op: "in_last_days", Value: 30.0}},
	}
	where, args, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(where, "julianday('now') - julianday(t.created_at)") {
		t.Errorf("expected julianday clause, got %q", where)
	}
	if len(args) != 1 || args[0] != 30.0 {
		t.Fatalf("expected [30] args, got %v", args)
	}
}

func TestBuildWhereClause_RejectsUnknownField(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "path; DROP TABLE tracks;--", Op: "is", Value: "x"}},
	}
	if _, _, err := BuildWhereClause(group); err == nil {
		t.Fatal("expected error for unknown field, got nil")
	}
}

func TestBuildWhereClause_RejectsUnknownOperator(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "year", Op: "is", Value: 2000.0}},
	}
	if _, _, err := BuildWhereClause(group); err == nil {
		t.Fatal("expected error for disallowed operator, got nil")
	}
}

func TestBuildWhereClause_RejectsBadMatchType(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "xor",
		Rules: []domain.SmartRule{{Field: "year", Op: "gt", Value: 2000.0}},
	}
	if _, _, err := BuildWhereClause(group); err == nil {
		t.Fatal("expected error for invalid match type, got nil")
	}
}

func TestBuildWhereClause_RejectsBadValueType(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "year", Op: "gt", Value: "not-a-number"}},
	}
	if _, _, err := BuildWhereClause(group); err == nil {
		t.Fatal("expected error for non-numeric value, got nil")
	}
}

func TestBuildWhereClause_NestedGroups(t *testing.T) {
	// (genre is Rock AND year between 1990-1999) OR (genre is Jazz AND bpm < 100)
	group := domain.SmartRuleGroup{
		Match: "any",
		Groups: []domain.SmartRuleGroup{
			{
				Match: "all",
				Rules: []domain.SmartRule{
					{Field: "genre", Op: "is", Value: "Rock"},
					{Field: "year", Op: "between", Value: []any{1990.0, 1999.0}},
				},
			},
			{
				Match: "all",
				Rules: []domain.SmartRule{
					{Field: "genre", Op: "is", Value: "Jazz"},
					{Field: "bpm", Op: "lt", Value: 100.0},
				},
			},
		},
	}
	where, args, err := BuildWhereClause(group)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Count(where, " OR ") != 1 {
		t.Errorf("expected exactly one top-level OR join, got %q", where)
	}
	if !strings.HasPrefix(where, "((") {
		t.Errorf("expected two nested subgroups wrapped in the outer group, got %q", where)
	}
	if len(args) != 5 {
		t.Fatalf("expected 5 args, got %d: %v", len(args), args)
	}
}

func TestBuildWhereClause_NestedGroupPropagatesError(t *testing.T) {
	group := domain.SmartRuleGroup{
		Match: "all",
		Groups: []domain.SmartRuleGroup{
			{Match: "all", Rules: []domain.SmartRule{{Field: "nope", Op: "is", Value: "x"}}},
		},
	}
	if _, _, err := BuildWhereClause(group); err == nil {
		t.Fatal("expected error to propagate from nested group, got nil")
	}
}

func TestOrderBySQL(t *testing.T) {
	cases := map[string]string{
		"":            "t.sort_title",
		"title":       "t.sort_title",
		"random":      "RANDOM()",
		"album":       "a.title, t.disc_number, t.track_number",
		"artist":      "t.raw_artist_names",
		"genre":       "t.raw_genre_names",
		"most_played": "t.play_count DESC",
	}
	for by, want := range cases {
		got, err := OrderBySQL(by)
		if err != nil {
			t.Fatalf("OrderBySQL(%q): unexpected error: %v", by, err)
		}
		if got != want {
			t.Errorf("OrderBySQL(%q) = %q, want %q", by, got, want)
		}
	}
}

func TestOrderBySQL_RejectsUnknown(t *testing.T) {
	if _, err := OrderBySQL("t.id; DROP TABLE tracks;--"); err == nil {
		t.Fatal("expected error for unknown limit-by value, got nil")
	}
}
