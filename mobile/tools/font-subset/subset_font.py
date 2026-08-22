#!/usr/bin/env python3
"""
Subset Material Symbols Rounded font to only the icons used in MaterialSymbols.kt.

Strategy
--------
Material Symbols renders icons via GSUB ligature substitution: the character
sequence "play_arrow" is substituted with a single icon glyph.  A naive text-
based subset keeps all 6 500 glyphs (layout_closure follows a→z through every
icon), while layout_closure=False strips the actual icon glyphs entirely.

The correct approach is to:
  1. Parse the GSUB Extension-Ligature lookups to find the *output* glyph name
     for each requested icon (e.g. "home" → glyph "home").
  2. Subset by explicit glyph names (input chars a-z/_ + 63 output icon glyphs)
     with layout_closure=False so no extra glyphs are pulled in.
  3. Retain all layout features so Android's text shaper still applies the
     surviving ligature rules.

Result: ~14 MB → ~30–60 KB (input chars + 63 icon outlines).

Usage:
    python3 subset_font.py <MaterialSymbols.kt> <source.ttf> <output.ttf>

Dependencies:
    pip install fonttools brotli
"""

import re
import sys
import os


# ---------------------------------------------------------------------------
# Parse MaterialSymbols.kt
# ---------------------------------------------------------------------------

def parse_icons(symbols_kt_path: str) -> list[str]:
    """Return sorted, deduplicated list of icon name strings."""
    pattern = re.compile(r'const val \w+ = "([^"]+)"')
    seen: set[str] = set()
    icons: list[str] = []
    with open(symbols_kt_path, encoding="utf-8") as f:
        for line in f:
            m = pattern.search(line)
            if m:
                icon = m.group(1)
                if icon not in seen:
                    seen.add(icon)
                    icons.append(icon)
    return sorted(icons)


# ---------------------------------------------------------------------------
# GSUB ligature tracing
# ---------------------------------------------------------------------------

def _search_ext_ligature(subtable, first_glyph: str, rest: list[str]) -> str | None:
    """Search a LookupType-4 (Ligature) subtable for an exact sequence match."""
    ligatures = getattr(subtable, "ligatures", {})
    for lig in ligatures.get(first_glyph, []):
        if lig.Component == rest:
            return lig.LigGlyph
    return None


def find_gsub_output(gsub, sequence: list[str]) -> str | None:
    """Return the output glyph name for *sequence* by tracing GSUB lookups."""
    first, rest = sequence[0], sequence[1:]
    for lookup in gsub.LookupList.Lookup:
        ltype = lookup.LookupType
        for sub in lookup.SubTable:
            if ltype == 4:
                result = _search_ext_ligature(sub, first, rest)
            elif ltype == 7 and sub.ExtensionLookupType == 4:
                result = _search_ext_ligature(sub.ExtSubTable, first, rest)
            else:
                result = None
            if result:
                return result
    return None


def resolve_icon_glyphs(font, icons: list[str]) -> tuple[list[str], list[str]]:
    """
    Return (found_glyph_names, missing_icons).

    found_glyph_names — glyph names for all icons that exist in the font's
                        GSUB ligature table.
    missing_icons     — icon names for which no ligature was found (logged).
    """
    cmap = font.getBestCmap()
    char_to_glyph: dict[str, str] = {chr(cp): name for cp, name in cmap.items()}
    gsub = font["GSUB"].table

    found: list[str] = []
    missing: list[str] = []

    for icon in icons:
        try:
            sequence = [char_to_glyph[c] for c in icon]
        except KeyError as exc:
            missing.append(icon)
            print(f"  Warning: character {exc} not in font cmap, skipping '{icon}'")
            continue

        glyph = find_gsub_output(gsub, sequence)
        if glyph:
            found.append(glyph)
        else:
            missing.append(icon)
            print(f"  Warning: no GSUB ligature found for '{icon}'")

    return found, missing


# ---------------------------------------------------------------------------
# Subsetting
# ---------------------------------------------------------------------------

def subset_font(source_ttf: str, output_ttf: str, icons: list[str]) -> None:
    try:
        from fontTools.ttLib import TTFont
        from fontTools import subset as ft_subset
    except ImportError:
        sys.exit("fonttools not installed. Run: pip install fonttools brotli")

    os.makedirs(os.path.dirname(output_ttf) or ".", exist_ok=True)

    # --- Step 1: resolve icon → output glyph name via GSUB ---
    discovery_font = TTFont(source_ttf)
    icon_glyphs, missing = resolve_icon_glyphs(discovery_font, icons)
    discovery_font.close()

    if not icon_glyphs:
        sys.exit("No icon glyphs could be resolved — aborting.")

    # --- Step 2: build explicit glyph set to keep ---
    # Input characters: every unique character that appears in any icon name
    # (a-z + underscore). These must be present so the ligature input sequences
    # are available to the shaper at runtime.
    input_chars = sorted({c for icon in icons for c in icon})
    input_text = "".join(input_chars)  # used to seed codepoint retention

    # --- Step 3: subset ---
    options = ft_subset.Options()
    options.layout_features = ["*"]   # keep all OpenType features (GSUB etc.)
    options.hinting = False           # not needed on Android
    options.desubroutinize = True
    options.layout_closure = False    # don't pull in extra glyphs via layout

    font = ft_subset.load_font(source_ttf, options)
    subsetter = ft_subset.Subsetter(options=options)
    # Seed with input codepoints AND explicit icon glyph names
    subsetter.populate(text=input_text, glyphs=icon_glyphs)
    subsetter.subset(font)
    ft_subset.save_font(font, output_ttf, options)
    font.close()


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    if len(sys.argv) != 4:
        print(
            "Usage: subset_font.py <MaterialSymbols.kt> <source.ttf> <output.ttf>",
            file=sys.stderr,
        )
        sys.exit(1)

    symbols_kt, source_ttf, output_ttf = sys.argv[1], sys.argv[2], sys.argv[3]

    icons = parse_icons(symbols_kt)
    if not icons:
        sys.exit(f"No icons found in {symbols_kt}")

    print(f"Found {len(icons)} icons: {', '.join(icons)}")

    subset_font(source_ttf, output_ttf, icons)

    src_size = os.path.getsize(source_ttf)
    out_size = os.path.getsize(output_ttf)
    reduction = 100 * (1 - out_size / src_size)
    print(
        f"Font subsetted: {src_size / 1024 / 1024:.1f} MB"
        f" → {out_size / 1024:.0f} KB"
        f"  ({reduction:.0f}% reduction)"
    )


if __name__ == "__main__":
    main()
