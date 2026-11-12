package domain

import "fmt"

func DefaultSidebarItems() []SidebarItem {
	return []SidebarItem{
		{"home", true}, {"recently_added", true}, {"artists", true},
		{"albums", true}, {"tracks", true}, {"genres", true}, {"composers", true},
	}
}

func ValidateSidebarItems(items []SidebarItem) error {
	if len(items) != 7 {
		return fmt.Errorf("expected seven sidebar items")
	}
	seen := make(map[string]bool, 7)
	for _, item := range items {
		switch item.ID {
		case "home", "recently_added", "artists", "albums", "tracks", "genres", "composers":
		default:
			return fmt.Errorf("unknown sidebar item: %q", item.ID)
		}
		if seen[item.ID] {
			return fmt.Errorf("duplicate sidebar item: %q", item.ID)
		}
		if item.ID == "home" && !item.Visible {
			return fmt.Errorf("home sidebar item must be visible")
		}
		seen[item.ID] = true
	}
	return nil
}
