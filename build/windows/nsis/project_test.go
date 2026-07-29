package nsis

import (
	"os"
	"strings"
	"testing"
)

func TestProjectCleansShortcutsAcrossShellContexts(t *testing.T) {
	project, err := os.ReadFile("project.nsi")
	if err != nil {
		t.Fatalf("read project.nsi: %v", err)
	}

	source := string(project)
	cleanup := "!macro RemoveAirmedyShortcuts"
	cleanupAt := strings.Index(source, cleanup)
	if cleanupAt < 0 {
		t.Fatal("installer must define shared shortcut cleanup")
	}
	cleanupEnd := strings.Index(source[cleanupAt:], "!macroend")
	if cleanupEnd < 0 {
		t.Fatal("shortcut cleanup function must end")
	}
	cleanupSource := source[cleanupAt : cleanupAt+cleanupEnd]

	for _, want := range []string{
		"SetShellVarContext current",
		"SetShellVarContext all",
		"Delete \"$SMPROGRAMS\\${INFO_PRODUCTNAME}.lnk\"",
		"Delete \"$DESKTOP\\${INFO_PRODUCTNAME}.lnk\"",
	} {
		if !strings.Contains(cleanupSource, want) {
			t.Errorf("shortcut cleanup missing %q", want)
		}
	}

	installCleanup := strings.Index(source, "!insertmacro RemoveAirmedyShortcuts")
	installShortcut := strings.Index(source, "CreateShortcut \"$SMPROGRAMS\\${INFO_PRODUCTNAME}.lnk\"")
	if installCleanup < 0 || installShortcut < 0 || installCleanup > installShortcut {
		t.Error("installer must remove existing shortcuts before creating the Start Menu shortcut")
	}

	if strings.Count(source, "!insertmacro RemoveAirmedyShortcuts") != 2 {
		t.Error("install and uninstall must both clean shortcuts across shell contexts")
	}
}
