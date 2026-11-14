# Changelog

Every version's section becomes its GitHub release notes, which the app's "What's new"
sheet shows. Put unreleased changes under "## Unreleased" as `- ` bullets.

## Unreleased

- The mini-player closes by swiping it to the right, like the swipe down it already had.

## 1.0.4

- Swipe the player to the right to close it: it follows your finger and collapses past a quarter of the screen, or with a quick flick.

## 1.0.3

- The loading screen shows the YUTU wordmark: black letters on the light theme, white on the dark one.
- The header keeps the play mark next to the YUTU wordmark. The home-screen icon stays the black Y.

## 1.0.2

- The home-screen icon is black with the Y.
- Inside the app, the splash screen, the header mark and the notification glyph go back to the play mark.

## 1.0.1

- New icon: black with the Y, plus matching splash screen and status-bar glyph.
- YUTU is made by trn1 (fork author and security review); Settings › About now credits it.

## 1.0.0

### New
- First YUTU release, forked from Yuno 1.0.0.
- Own name, icon, wordmark and package (`com.yutu`): it installs next to Yuno instead of replacing it.
- Material Icons (Apache-2.0) replace Apple's SF Symbols, and Inter (SIL OFL) replaces SF Pro Display.
- The updater only *notices* new versions: it opens the release page in the browser and never downloads or installs anything. The install permissions are gone from the manifest.
- Settings › About links to this repository.
