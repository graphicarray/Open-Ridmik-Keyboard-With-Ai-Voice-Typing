# Ai Voice Typer
1. Install Android Studio (Koala or newer). File > Open > this folder. Let Gradle sync.
2. Build > Build APK(s). APK: app/build/outputs/apk/debug/app-debug.apk
3. Install on phone, open app, paste Gemini key (aistudio.google.com), Save.
4. Allow mic.
5. Keyboard: in the app tap Enable, then Choose. Mic key on the keyboard types by voice (Jatio = Bangla, English = English).

## Next-word suggestions (v1.6)
- A thin row above the keys shows up to 3 words. After a space: likely next words. While typing: completions, most used first.
- Learns word counts and word pairs from your typing. Bangla and English are kept apart (it follows the current layout).
- Everything stays on the phone (app-private storage). Never sent to Gemini or anywhere else.
- Not learned: password fields, fields that ask for no personalised learning (incognito), e-mail / web address / address / name / number fields, words with digits, e-mail or web pieces, and nothing while the phone is locked.
- Settings screen: "Suggest next words" switch and "Clear learned words" button.
- Limits: 5000 learned words + 9000 word pairs per language. Counts are halved every 5000 learned words so old and rare entries fade out. A learned word is suggested after it was typed twice.
- Code: `Suggest.kt` (background thread, lock checks), `WordModel.kt` (counts + file), `Tok.kt` (reads the text before the cursor), `Starter.kt` (built-in word lists).
- `tools/SuggestCheck.kt` = plain-JVM checks for Tok / WordModel / Starter (not part of the Gradle build).

## Emoji (v1.5)
- Two smiley keys open the emoji panel: one in the top icon row, one in the bottom row (letter page, where the EN / বাং key used to be).
- Language switch is now only the swipe on the space key.
- Panel: scrollable category tabs (Recent, Smileys, People, Animals, Food, Activity, Travel, Objects, Symbols, Flags), a scrollable grid, and a bottom bar with ABC, space and backspace (hold to repeat).
- Recent = last 32 emojis, newest first, kept in device-protected prefs (not saved in password fields).
- Emoji are not sent to next-word learning.
- Emoji lists live in `Emojis.kt` (plain text). Emojis the phone font cannot draw are hidden (`Paint.hasGlyph`).
- No skin tone picker yet.
