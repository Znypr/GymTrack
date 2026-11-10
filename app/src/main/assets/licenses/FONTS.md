# Bundled fonts

Both fonts are licensed under the SIL Open Font License 1.1. The full licence texts ship next
to this file and travel inside the APK with the fonts.

| File in `res/font/` | Font | Copyright | Licence |
|---|---|---|---|
| `caveat_semibold.ttf` | Caveat (weight 600) | Copyright 2014 The Caveat Project Authors (https://github.com/googlefonts/caveat) | `Caveat-OFL.txt` |
| `patrick_hand_regular.ttf` | Patrick Hand Regular | Copyright (c) 2010-2012 Patrick Wagesreiter | `PatrickHand-OFL.txt` |

Source: https://github.com/google/fonts (`ofl/caveat/Caveat[wght].ttf`, `ofl/patrickhand/PatrickHand-Regular.ttf`),
fetched 2026-11-10.

Modifications (allowed by OFL 1.1; neither font declares a Reserved Font Name):
- Caveat: the variable font was instanced at weight 600 (static file).
- Both: subset to Latin, Latin-1 and common punctuation (fontTools `pyftsubset`, all layout features kept)
  to keep the APK small.

The fonts are bundled for offline use; the app does not use downloadable fonts.
