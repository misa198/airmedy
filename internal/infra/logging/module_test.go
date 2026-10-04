package logging

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"airmedy/internal/app/config"
)

func TestPanicIsWrittenToCrashLog(t *testing.T) {
	const marker = "airmedy crash log test panic"
	if dir := os.Getenv("AIRMEDY_TEST_CRASH_DIR"); dir != "" {
		if _, _, err := NewFileLogger(&config.Config{DataDir: dir}); err != nil {
			t.Fatal(err)
		}
		go func() { panic(marker) }()
		select {}
	}

	dir := t.TempDir()
	cmd := exec.Command(os.Args[0], "-test.run=^TestPanicIsWrittenToCrashLog$")
	cmd.Env = append(os.Environ(), "AIRMEDY_TEST_CRASH_DIR="+dir)
	if output, err := cmd.CombinedOutput(); err == nil {
		t.Fatalf("expected subprocess to crash, got: %s", output)
	}
	crash, err := os.ReadFile(filepath.Join(dir, "logs", "crash.log"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(crash), marker) || !strings.Contains(string(crash), "TestPanicIsWrittenToCrashLog") {
		t.Fatalf("crash log missing panic or stack: %s", crash)
	}
}
