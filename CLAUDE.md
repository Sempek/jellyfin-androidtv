# Jellyfin Android TV — Project Notes

## Sempick Search Feature

Sempick is a D-pad-driven search/selection UI that lets users navigate to a media item (movie, episode, album, audio track, playlist) using only the four directional arrow keys — no on-screen keyboard typing required.

### How It Works

**Core concept — arrow sequences:** The server (`/Sempick/Items`) assigns each candidate result a unique `semSequence` — a string of digits `0–3` (Left/Up/Right/Down). The user presses D-pad directions to progressively narrow the candidate set until one item remains.

`semSequence` values are right-padded to a fixed width. Always strip whitespace and offset by `NumberOfPickedPartitions` before using or displaying: `displaySequence = semSequence.trim().drop(NumberOfPickedPartitions)`.

**`PickState` values (server `state` field):**

| State | Meaning |
|---|---|
| `Initial` | First render, no picks made yet |
| `InterimList` | Show a list of **words** for the user to pick |
| `HeadList` | Show a list of **original items** for direct selection |
| `Completed` | Selection is final; navigate to the item |

**Strategy names (`StrategyName` field):** `AllWordAndKb` (keyboard mode), `WinnowingStrategyList` (plain list), `WinnowingStrategyScrollableList` (paged list with groups).

**Always branch on the current `PickState` after each pick — never infer state from the previous state.**

---

### Display Modes

**1. Keyboard mode (`AllWordAndKb`)**

`Results` is a jagged array — each inner array is one button's partition of words. The current QWERTY layout shows each active key with the arrow sequence needed to reach it. When `PickState` transitions to `HeadList` or `InterimList`, stop driving the keyboard loop; `InterimFragments` will be empty.

**Special control tokens in keyboard mode** (both can appear simultaneously — render both, visually distinct):

| Token | Affordance | Style |
|---|---|---|
| `\x00` (`KeyboardEndOfWordString`) | "✓ Use current word" — commits the typed character prefix as an exact word, discarding longer variants | Green background |
| `\x01` (`WordEndOfWordString`) | "✓ Select current match" — resolves to items whose word set is fully matched | Amber/gold background |

**2. Word list (`InterimList`)**

A small number of candidate words remain. Show them as a selectable list. Selecting a word returns to keyboard mode or advances to `HeadList`/`Completed`.

**3. Item list (`HeadList`)**

Show the remaining original items for direct selection. Occurs when:
- The candidate count falls below `listThreshold` during keyboard narrowing.
- Terminal ambiguity: multiple items share the same distinct-word set (e.g. "sweet home" and "home sweet home" both reduce to `{home, sweet}`), so no further keyboard narrowing is possible.

Do not send further keyboard input once `HeadList` is active.

**4. Scrollable list (`WinnowingStrategyScrollableList`)**

Used when the item count exceeds `listThreshold`. `PickState` is the same (`InterimList` or `HeadList`) — only `StrategyName` changes. Results contain three fragment types per bucket:

| Token | Meaning |
|---|---|
| `\x02` (`ScrollUpString`) | Scroll up / previous page |
| `\x03` (`ScrollDownString`) | Scroll down / next page |
| `ScrollGroupFragment` | A group of items — pressing its button opens a `HeadList` sub-selection |

Detect groups via `fragment is ScrollGroupFragment` (not string comparison — `\x04` prefix is a Unicode control character that confuses culture-sensitive `StartsWith`). Render group preview lines from `group.GroupItems`. Pressing a group button is **not** a final selection; it opens a sub-`HeadList`.

To detect whether paging is active at runtime, check for scroll token presence (not just `StrategyName`, because direct mode uses the same strategy when count ≤ N).

---

### Input Mapping

| D-pad | `KeyInput` index |
|---|---|
| Left | 0 |
| Up | 1 |
| Right | 2 |
| Down | 3 |
| Back/Undo | 4 |
| Reset | 5 |

**Undo:** A single back press may jump back multiple logical steps because the engine skips over any auto-selected steps in its history. Undo at initial state is a safe no-op.

---

### Navigation on Completion

When the server returns `Completed`, the app navigates based on item type (`Jelly.Item.Type`):

| Type value | Item | Action |
|---|---|---|
| `1` | Audio track | Start playback directly, then go home |
| `16` | Music album | Open track list (`itemList`) |
| `23` | Playlist | Open track list (`itemList`) |
| anything else | Movie, episode, series, artist, … | Open detail page (`itemDetails`) |

Navigation uses `replace = true` so Sempick is removed from the back stack.

---

### Architecture

| File | Role |
|------|------|
| `ui/sempick/SempickFragment.kt` | Compose-based UI fragment; keyboard and list layouts |
| `ui/sempick/SempickViewModel.kt` | Holds `UiState`, dispatches D-pad events, manages undo history |
| `ui/sempick/SempickData.kt` | Serialization models for the server response |
| `ui/sempick/SempickArrow.kt` | Canvas-drawn colored arrow composables |
| `data/repository/SempickRepository.kt` | HTTP client calling the custom `/Sempick/Items` endpoint |

### Arrow Color Convention
Matches the web version: Left=Red, Up=Green, Right=Blue, Down=Yellow.
