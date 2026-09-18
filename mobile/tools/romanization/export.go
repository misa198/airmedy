// Run from the repository root: go run ./mobile/tools/romanization/export.go
// Regeneration only; the Android build consumes the checked-in output.
package main

import (
	"bufio"
	"fmt"
	"os"
	"sort"

	"github.com/mozillazg/go-pinyin"
)

func main() {
	file, err := os.Create("mobile/androidApp/src/main/assets/romanization/characters.tsv")
	if err != nil {
		panic(err)
	}
	writer := bufio.NewWriter(file)
	keys := make([]int, 0, len(pinyin.PinyinDict))
	for key := range pinyin.PinyinDict {
		keys = append(keys, key)
	}
	sort.Ints(keys)
	args := pinyin.NewArgs()
	args.Style = pinyin.Tone
	for _, key := range keys {
		readings := pinyin.SinglePinyin(rune(key), args)
		if len(readings) > 0 {
			if _, err := fmt.Fprintf(writer, "%X\t%s\n", key, readings[0]); err != nil {
				panic(err)
			}
		}
	}
	if err := writer.Flush(); err != nil {
		panic(err)
	}
	if err := file.Close(); err != nil {
		panic(err)
	}
}
