# Offline romanization data and engines

All runtime data is embedded in the application. Conversion never downloads data.

| Source                                                                | Pinned revision                                                                     | License                                                               |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| [go-pinyin](https://github.com/mozillazg/go-pinyin)                   | v0.21.0                                                                             | MIT                                                                   |
| [phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data) | `cee0ed6e6e4898580cafd2bd5e3723e20b214aa0` (`pinyin.txt`, version 0.19.0)           | [MIT](data/PINYIN-LICENSE)                                            |
| [OpenCC](https://github.com/BYVoid/OpenCC)                            | `5c764a5a886f46eb365656ed91a0410d7162fac6` (`TSCharacters.txt`, phrase lookup only) | [Apache-2.0](data/OPENCC-LICENSE)                                     |
| [Kagome](https://github.com/ikawaha/kagome)                           | v2.11.0                                                                             | Apache-2.0                                                            |
| [kagome-dict IPA](https://github.com/ikawaha/kagome-dict)             | `a5b2073b8f66980d8b14e9e7aa433da06a2e732d` (`ipa/ipa.dict`)                         | [MIT wrapper](data/IPA-LICENSE), [IPADIC terms](data/IPA-NOTICE.txt)  |
| [hebon](https://github.com/doxuta/hebon)                              | `b6abdeeaa6e8` (2026-09-16)                                                         | MIT                                                                   |
| [koroman](https://github.com/gerosyab/koroman)                        | `e529729b923f8a9d9acc62b2c9c0887b00e9bd88`                                          | [MIT](https://github.com/misa198/koreanromanizer/blob/v1.0.0/LICENSE) |

The IPA archive is copied unchanged and loaded through `embed.FS.Open`'s
`io.ReaderAt`; no `ipa.Dict()` singleton or archive-sized heap copy is used.
The phrase file is copied unchanged and parsed lazily. Updating either data
file or an algorithm requires bumping `Engine.Version()` to invalidate results.
