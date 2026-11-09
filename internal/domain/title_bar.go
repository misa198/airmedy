package domain

import "fmt"

func DefaultTitleBarActions() []TitleBarAction {
	return []TitleBarAction{{"mobile", true}, {"library", true}, {"settings", true}, {"playback", false}, {"remote", false}}
}

func ValidateTitleBarActions(actions []TitleBarAction) error {
	if len(actions) != 5 {
		return fmt.Errorf("expected five title bar actions")
	}
	seen := make(map[string]bool, 5)
	for _, action := range actions {
		switch action.ID {
		case "mobile", "library", "settings", "playback", "remote":
		default:
			return fmt.Errorf("unknown title bar action: %q", action.ID)
		}
		if seen[action.ID] {
			return fmt.Errorf("duplicate title bar action: %q", action.ID)
		}
		seen[action.ID] = true
	}
	return nil
}
