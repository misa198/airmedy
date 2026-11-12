package domain

import "testing"

func TestValidateSidebarItems(t *testing.T) {
	for _, name := range []string{"default", "reordered", "missing", "duplicate", "unknown", "hidden_home"} {
		t.Run(name, func(t *testing.T) {
			items := DefaultSidebarItems()
			switch name {
			case "reordered":
				items[0], items[6] = items[6], items[0]
			case "missing":
				items = items[:6]
			case "duplicate":
				items[0].ID = items[1].ID
			case "unknown":
				items[0].ID = "unknown"
			case "hidden_home":
				items[0].Visible = false
			}
			err := ValidateSidebarItems(items)
			if (name == "default" || name == "reordered") != (err == nil) {
				t.Fatalf("ValidateSidebarItems() error = %v", err)
			}
		})
	}
}
