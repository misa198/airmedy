package domain

import "context"

type RomanizationInspection struct {
	Supported       bool     `json:"supported"`
	Languages       []string `json:"languages"`
	MandarinDefault bool     `json:"mandarinDefault"`
}

type RomanizedLine struct {
	Text   string `json:"text"`
	Status string `json:"status"` // converted, unsupported, failed
}

// RomanizationEngine is owned exclusively by the service's single worker.
// Release drops dictionaries; returned strings must not retain dictionary storage.
type RomanizationEngine interface {
	Version() string
	Romanize(ctx context.Context, text string, japaneseContext bool) (string, error)
	Release()
}
