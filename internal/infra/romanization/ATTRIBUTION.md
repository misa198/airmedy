# Offline romanization data and engines

All runtime data is embedded in the application. Conversion never downloads data.

| Source                                                                | Pinned revision                                                                     | License                                                               |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| [go-pinyin](https://github.com/mozillazg/go-pinyin)                   | v0.21.0                                                                             | MIT                                                                   |
| [phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data) | `cee0ed6e6e4898580cafd2bd5e3723e20b214aa0` (`pinyin.txt`, version 0.19.0)           | MIT                                                                   |
| [OpenCC](https://github.com/BYVoid/OpenCC)                            | `5c764a5a886f46eb365656ed91a0410d7162fac6` (`TSCharacters.txt`, phrase lookup only) | Apache-2.0                                                            |
| [koroman](https://github.com/gerosyab/koroman)                        | `e529729b923f8a9d9acc62b2c9c0887b00e9bd88`                                          | [MIT](https://github.com/misa198/koreanromanizer/blob/v1.0.0/LICENSE) |

The phrase file is copied unchanged and parsed lazily. Updating it or an
algorithm requires bumping `Engine.Version()` to invalidate results.
