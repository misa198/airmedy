package playlist

import (
	"fmt"
	"strconv"
	"strings"

	"airmedy/internal/domain"
)

// fieldKind determines how a rule field is translated to SQL: a direct column
// on tracks, or a relation that must be matched via an EXISTS subquery
// against a many-to-many join table (genres, artists).
type fieldKind int

const (
	fieldKindColumn fieldKind = iota
	fieldKindRelation
	fieldKindAddedAt
)

type valueType int

const (
	valueTypeString valueType = iota
	valueTypeNumber
	valueTypeBool
)

type fieldSpec struct {
	kind      fieldKind
	valueType valueType
	// column is the `t.<col>` expression, used when kind == fieldKindColumn.
	column string
	// joinTable/nameTable/nameCol describe the relation used when
	// kind == fieldKindRelation, e.g. track_genres -> genres.name.
	joinTable string
	nameTable string
	nameCol   string
	ops       map[string]bool
}

// smartPlaylistFields is the explicit allowlist of field -> SQL translation.
// Rule input comes straight from the user (via the frontend rule builder), so
// field/operator names are only ever used to look up entries here — they are
// never interpolated into SQL directly. This is what prevents SQL injection
// through a crafted "field" or "op" value.
var smartPlaylistFields = map[string]fieldSpec{
	"genre": {
		kind: fieldKindRelation, valueType: valueTypeString,
		joinTable: "track_genres", nameTable: "genres", nameCol: "name",
		ops: map[string]bool{"is": true, "is_not": true, "contains": true},
	},
	"artist": {
		kind: fieldKindRelation, valueType: valueTypeString,
		joinTable: "track_artists", nameTable: "artists", nameCol: "name",
		ops: map[string]bool{"is": true, "is_not": true, "contains": true},
	},
	"year": {
		kind: fieldKindColumn, valueType: valueTypeNumber, column: "t.year",
		ops: map[string]bool{"gt": true, "lt": true, "gte": true, "lte": true, "between": true},
	},
	"bpm": {
		kind: fieldKindColumn, valueType: valueTypeNumber, column: "t.bpm",
		ops: map[string]bool{"gt": true, "lt": true, "gte": true, "lte": true, "between": true},
	},
	"play_count": {
		kind: fieldKindColumn, valueType: valueTypeNumber, column: "t.play_count",
		ops: map[string]bool{"gt": true, "lt": true, "gte": true, "lte": true, "between": true},
	},
	"duration": {
		kind: fieldKindColumn, valueType: valueTypeNumber, column: "t.duration",
		ops: map[string]bool{"gt": true, "lt": true, "gte": true, "lte": true, "between": true},
	},
	"bitrate": {
		kind: fieldKindColumn, valueType: valueTypeNumber, column: "t.bitrate",
		ops: map[string]bool{"gt": true, "lt": true, "gte": true, "lte": true, "between": true},
	},
	"is_favorite": {
		kind: fieldKindColumn, valueType: valueTypeBool, column: "t.is_favorite",
		ops: map[string]bool{"is": true},
	},
	"added_at": {
		kind: fieldKindAddedAt, valueType: valueTypeNumber,
		ops: map[string]bool{"in_last_days": true},
	},
}

// BuildWhereClause translates a smart playlist's rule tree into a SQL WHERE
// clause (without the "WHERE" keyword) plus its bound args. Each group's own
// rules and its nested groups are all joined by that group's Match ("all" =
// AND, "any" = OR), recursively, which is what lets a playlist combine
// multiple AND/OR blocks instead of just one flat list. Returns an error if
// any rule references a field or operator outside the allowlist above, a
// group has an invalid Match, or a rule value is malformed — this is the
// sole gate protecting the raw-SQL query builder used by
// track_repository.GetByRules from injection.
func BuildWhereClause(group domain.SmartRuleGroup) (string, []any, error) {
	joiner, err := joinerForMatch(group.Match)
	if err != nil {
		return "", nil, err
	}

	var parts []string
	var args []any
	for _, rule := range group.Rules {
		spec, ok := smartPlaylistFields[rule.Field]
		if !ok {
			return "", nil, fmt.Errorf("unknown smart playlist field: %q", rule.Field)
		}
		if !spec.ops[rule.Op] {
			return "", nil, fmt.Errorf("operator %q not allowed for field %q", rule.Op, rule.Field)
		}

		frag, fragArgs, err := buildRuleFragment(rule, spec)
		if err != nil {
			return "", nil, fmt.Errorf("rule %q %q: %w", rule.Field, rule.Op, err)
		}
		parts = append(parts, frag)
		args = append(args, fragArgs...)
	}

	for _, sub := range group.Groups {
		frag, fragArgs, err := BuildWhereClause(sub)
		if err != nil {
			return "", nil, err
		}
		parts = append(parts, frag)
		args = append(args, fragArgs...)
	}

	if len(parts) == 0 {
		return "1=1", nil, nil
	}

	return "(" + strings.Join(parts, joiner) + ")", args, nil
}

// OrderBySQL maps a SmartPlaylistLimit.By value to the SQL ORDER BY
// expression track_repository.GetByRules sorts by before truncating to the
// limit count. Like the field/operator allowlist above, this is a switch
// over known values rather than interpolating `by` into SQL directly, so an
// unrecognized value is rejected instead of reaching the query.
func OrderBySQL(by string) (string, error) {
	switch by {
	case "", "title":
		return "t.sort_title", nil
	case "random":
		return "RANDOM()", nil
	case "album":
		return "a.title, t.disc_number, t.track_number", nil
	case "artist":
		return "t.raw_artist_names", nil
	case "genre":
		return "t.raw_genre_names", nil
	case "most_played":
		return "t.play_count DESC", nil
	default:
		return "", fmt.Errorf("unknown limit-by value: %q", by)
	}
}

// countRules returns the total number of leaf rules across a group and all
// its nested groups.
func countRules(group domain.SmartRuleGroup) int {
	n := len(group.Rules)
	for _, sub := range group.Groups {
		n += countRules(sub)
	}
	return n
}

func joinerForMatch(matchType string) (string, error) {
	switch matchType {
	case "all", "":
		return " AND ", nil
	case "any":
		return " OR ", nil
	default:
		return "", fmt.Errorf("invalid match type: %q", matchType)
	}
}

func buildRuleFragment(rule domain.SmartRule, spec fieldSpec) (string, []any, error) {
	switch spec.kind {
	case fieldKindRelation:
		return buildRelationFragment(rule, spec)
	case fieldKindAddedAt:
		return buildAddedAtFragment(rule)
	default:
		return buildColumnFragment(rule, spec)
	}
}

func buildColumnFragment(rule domain.SmartRule, spec fieldSpec) (string, []any, error) {
	switch spec.valueType {
	case valueTypeNumber:
		return buildNumberFragment(spec.column, rule.Op, rule.Value)
	case valueTypeBool:
		b, ok := rule.Value.(bool)
		if !ok {
			return "", nil, fmt.Errorf("expected bool value")
		}
		val := 0
		if b {
			val = 1
		}
		return spec.column + " = ?", []any{val}, nil
	default: // string
		s, ok := rule.Value.(string)
		if !ok {
			return "", nil, fmt.Errorf("expected string value")
		}
		switch rule.Op {
		case "is":
			return spec.column + " = ?", []any{s}, nil
		case "is_not":
			return spec.column + " != ?", []any{s}, nil
		case "contains":
			return spec.column + " LIKE ?", []any{"%" + s + "%"}, nil
		}
		return "", nil, fmt.Errorf("unsupported operator")
	}
}

func buildRelationFragment(rule domain.SmartRule, spec fieldSpec) (string, []any, error) {
	s, ok := rule.Value.(string)
	if !ok {
		return "", nil, fmt.Errorf("expected string value")
	}

	var nameCmp string
	var arg any = s
	switch rule.Op {
	case "is":
		nameCmp = "n." + spec.nameCol + " = ?"
	case "contains":
		nameCmp = "n." + spec.nameCol + " LIKE ?"
		arg = "%" + s + "%"
	case "is_not":
		// NOT EXISTS a matching row, rather than negating inside EXISTS.
		frag := fmt.Sprintf(
			"NOT EXISTS (SELECT 1 FROM %s j JOIN %s n ON j.%s = n.id WHERE j.track_id = t.id AND n.%s = ?)",
			spec.joinTable, spec.nameTable, relationIDCol(spec), spec.nameCol,
		)
		return frag, []any{s}, nil
	default:
		return "", nil, fmt.Errorf("unsupported operator")
	}

	frag := fmt.Sprintf(
		"EXISTS (SELECT 1 FROM %s j JOIN %s n ON j.%s = n.id WHERE j.track_id = t.id AND %s)",
		spec.joinTable, spec.nameTable, relationIDCol(spec), nameCmp,
	)
	return frag, []any{arg}, nil
}

// relationIDCol maps a join table to its foreign key column name.
func relationIDCol(spec fieldSpec) string {
	if spec.joinTable == "track_genres" {
		return "genre_id"
	}
	return "artist_id"
}

func buildAddedAtFragment(rule domain.SmartRule) (string, []any, error) {
	days, err := toFloat(rule.Value)
	if err != nil {
		return "", nil, err
	}
	return "(julianday('now') - julianday(t.created_at)) <= ?", []any{days}, nil
}

func buildNumberFragment(column, op string, value any) (string, []any, error) {
	if op == "between" {
		vals, ok := value.([]any)
		if !ok || len(vals) != 2 {
			return "", nil, fmt.Errorf("expected [min, max] value for between")
		}
		lo, err := toFloat(vals[0])
		if err != nil {
			return "", nil, err
		}
		hi, err := toFloat(vals[1])
		if err != nil {
			return "", nil, err
		}
		return column + " BETWEEN ? AND ?", []any{lo, hi}, nil
	}

	n, err := toFloat(value)
	if err != nil {
		return "", nil, err
	}
	switch op {
	case "gt":
		return column + " > ?", []any{n}, nil
	case "lt":
		return column + " < ?", []any{n}, nil
	case "gte":
		return column + " >= ?", []any{n}, nil
	case "lte":
		return column + " <= ?", []any{n}, nil
	}
	return "", nil, fmt.Errorf("unsupported operator")
}

// toFloat accepts the numeric shapes that survive a JSON round-trip
// (float64 from json.Unmarshal into `any`, or a string from form input).
func toFloat(v any) (float64, error) {
	switch n := v.(type) {
	case float64:
		return n, nil
	case int:
		return float64(n), nil
	case string:
		f, err := strconv.ParseFloat(n, 64)
		if err != nil {
			return 0, fmt.Errorf("invalid number %q", n)
		}
		return f, nil
	default:
		return 0, fmt.Errorf("expected numeric value")
	}
}
