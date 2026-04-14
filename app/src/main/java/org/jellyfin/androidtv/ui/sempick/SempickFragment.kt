package org.jellyfin.androidtv.ui.sempick

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.PlaybackHelper
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.UUID

// Scrollable list layout constants.
// Display order is spatial (matches D-pad positions): Up at top, Left/Right in middle, Down at bottom.
private val SCROLLABLE_DISPLAY_ORDER = listOf(1, 0, 2, 3)
// Group slot background colors indexed by direction (0=Left, 1=Up, 2=Right, 3=Down).
// Up and Down are scroll-only so they use the scroll background; these values cover the rare
// case where slot 1 or 3 holds a content group instead of a scroll token.
private val SCROLLABLE_GROUP_BG = listOf(
    Color(0xFFCCEE00),  // 0 Left  — chartreuse (dark text)
    Color(0xFF117766),  // 1 Up    — teal (light text)
    Color(0xFF771199),  // 2 Right — purple (light text)
    Color(0xFF885500),  // 3 Down  — amber (light text)
)
private val SCROLLABLE_GROUP_TEXT = listOf(
    Color(0xFF111111),  // 0 Left  — dark on chartreuse
    Color(0xFFDDFFDD),  // 1 Up
    Color(0xFFEEEEEE),  // 2 Right — light on purple
    Color(0xFFFFEEAA),  // 3 Down
)
private val SCROLLABLE_SCROLL_BG = Color(0xFF111111)

private val KEY_BACKGROUND = Color(0xFFEEEEEE)
private val KEY_BACKGROUND_INACTIVE = Color(0xFF888888)
private val KEY_BORDER = Color(0xFF555555)
private val KEY_CHAR_COLOR = Color(0xFF111111)
private val KEY_CHAR_COLOR_INACTIVE = Color(0xFFAAAAAA)
private val SELECTION_BAR_BACKGROUND = Color(0xFF1E3A5F)  // dark navy — high contrast with light text
private val SELECTION_BAR_TEXT = Color(0xFFEEEEEE)
private val KEYBOARD_ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
private val KEY_SHAPE = RoundedCornerShape(4.dp)

class SempickFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			val viewModel = koinViewModel<SempickViewModel>()
			val state by viewModel.state.collectAsState()
			val focusRequester = remember { FocusRequester() }
			val navigationRepository = koinInject<NavigationRepository>()

			LaunchedEffect(Unit) { focusRequester.requestFocus() }

			val context = LocalContext.current
			val playbackHelper = koinInject<PlaybackHelper>()

			// When the server signals completion, navigate to the appropriate Jellyfin
			// page for the selected item using replace=true so Sempick is removed from
			// the back stack — pressing back from the item page goes to home, not search.
			LaunchedEffect(state.isCompleted) {
				if (state.isCompleted) {
					val jelly = (state.selectedResult ?: state.filteredResults.firstOrNull())
						?.fragment?.Jelly ?: return@LaunchedEffect
					val itemId = UUID.fromString(jelly.Id)
					when (jelly.Item?.Type) {
						// Individual audio tracks (Audio=1): start playback directly, then go home
						1 -> {
							playbackHelper.retrieveAndPlay(itemId, false, context)
							navigationRepository.navigate(Destinations.home, replace = true)
						}
						// Albums (MusicAlbum=16) and playlists (Playlist=23): show track list + Play All
						16, 23 -> navigationRepository.navigate(Destinations.itemList(itemId), replace = true)
						// Everything else (Movie=13, Episode=9, Series=28, MusicArtist=17, …):
						// FullDetailsFragment shows the item with a Play button
						else -> navigationRepository.navigate(Destinations.itemDetails(itemId), replace = true)
					}
				}
			}

			// Intercept Back only while actively searching. Once completed (navigating
			// to item page) or when there is nothing left to undo, release back so the
			// system handles it normally.
			val canUndo = !state.isCompleted &&
				(state.picks.isNotEmpty() || state.undoHistory.isNotEmpty())
			BackHandler(enabled = canUndo) {
				viewModel.onUndo()
			}

			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.Sempick)

				Box(
					modifier = Modifier
						.fillMaxSize()
						.focusRequester(focusRequester)
						.focusable()
						.onKeyEvent { event ->
							if (event.type == KeyEventType.KeyDown) when (event.key) {
								Key.DirectionLeft -> { viewModel.onDirectionPressed(0); true }
								Key.DirectionUp -> { viewModel.onDirectionPressed(1); true }
								Key.DirectionRight -> { viewModel.onDirectionPressed(2); true }
								Key.DirectionDown -> { viewModel.onDirectionPressed(3); true }
								else -> false
							} else false
						},
				) {
					when {
						state.loading -> Text(
							"Loading...",
							modifier = Modifier.align(Alignment.Center),
							color = JellyfinTheme.colorScheme.onBackground,
						)
						state.error != null -> Text(
							"Error: ${state.error}",
							modifier = Modifier.align(Alignment.Center),
							color = Color(0xFFFF6B6B),
						)
						// Dispatch on state first (reliable), then StrategyName within list states.
						// Keyboard mode is the default — it covers Initial, AllWordAndKb, and any
						// unrecognised StrategyName so the screen is never accidentally blank.
						else -> when (state.response?.state) {
							PickState.Completed -> { /* LaunchedEffect handles navigation; render nothing */ }
							PickState.InterimList, PickState.HeadList ->
								when (state.response?.StrategyName) {
									StrategyNames.ScrollableList -> SempickScrollableListContent(state = state)
									else -> SempickListContent(state = state)
								}
							// Initial, AllWordAndKb, null, or any future state — keyboard mode
							else -> SempickKeyboardContent(state = state)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SempickKeyboardContent(state: SempickUiState) {
	val seqOffset = (state.response?.NumberOfPickedPartitions ?: 0) + state.picks.length
	val resultsByChar = state.filteredResults.associateBy { it.fragment.fragment }
	val previousText = state.response?.PreviousSelections
		?.joinToString("  ") { it.selection }.orEmpty()
	val remaining = state.response?.Remaining ?: 0

	// ControlTokens.KeyboardEndOfWord (\x00) — WinnowingStrategyKeyboard.EndOfWordsString
	//   Keyboard layer: typed characters form an exact word for some candidates ("home" vs "homes").
	//   Pressing its direction commits the current character prefix as the exact word.
	val kbEowResult = state.filteredResults.find { it.fragment.fragment == ControlTokens.KeyboardEndOfWord }
	val kbEowSequence = kbEowResult?.semSequence?.trim()?.drop(seqOffset).orEmpty()

	// ControlTokens.WordEndOfWord (\x01) — AllWordSearch.EndOfWordsString
	//   Word-search layer: current word selection fully matches some items ("sweet home" vs "home sweet home").
	//   Pressing its direction selects those exact-match items and moves forward.
	val wsEowResult = state.filteredResults.find { it.fragment.fragment == ControlTokens.WordEndOfWord }
	val wsEowSequence = wsEowResult?.semSequence?.trim()?.drop(seqOffset).orEmpty()

	val eowRowCount = (if (kbEowSequence.isNotEmpty()) 1 else 0) + (if (wsEowSequence.isNotEmpty()) 1 else 0)

	// One BoxWithConstraints owns all sizing so everything is computed once.
	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val keyWidth = maxWidth / 11
		val halfKey = keyWidth / 2
		val eowRowHeight = if (eowRowCount > 0) maxWidth * 0.045f else 0.dp
		val keyHeight = (maxHeight - halfKey - eowRowHeight * eowRowCount) / 5.10f
		val gap = keyHeight * 0.10f
		val charFontSp = with(LocalDensity.current) { (keyHeight * 0.38f).toSp() }

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = halfKey, end = halfKey, bottom = halfKey),
		) {
			// Selected-characters bar — spans full keyboard width, same char size as keys
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.size(keyWidth * 10, keyHeight)
					.background(SELECTION_BAR_BACKGROUND),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = previousText.uppercase().ifEmpty { "\u00A0" },
					color = SELECTION_BAR_TEXT,
					fontSize = charFontSp,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 12.dp),
				)
				if (remaining > 0) {
					Text(
						text = "$remaining",
						color = SELECTION_BAR_TEXT.copy(alpha = 0.7f),
						fontSize = charFontSp * 0.6f,
						modifier = Modifier.padding(end = 12.dp),
					)
				}
			}

			// \x00 affordance: commit current typed characters as the exact word.
			if (kbEowSequence.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.height(eowRowHeight)
						.background(Color(0xFF2E5E2E)),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "  ✓  Use current word",
						color = Color(0xFFCCFFCC),
						fontSize = with(LocalDensity.current) { (eowRowHeight * 0.5f).toSp() },
						modifier = Modifier.weight(1f),
					)
					SempickArrowSequence(
						sequence = kbEowSequence,
						arrowSize = eowRowHeight * 0.6f,
						rowSize = 4,
						modifier = Modifier.padding(end = 8.dp),
					)
				}
			}
			// \x01 affordance: current word-selection fully identifies some items.
			if (wsEowSequence.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.height(eowRowHeight)
						.background(Color(0xFF3A3A1E)),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "  ✓  Select current match",
						color = Color(0xFFFFFFCC),
						fontSize = with(LocalDensity.current) { (eowRowHeight * 0.5f).toSp() },
						modifier = Modifier.weight(1f),
					)
					SempickArrowSequence(
						sequence = wsEowSequence,
						arrowSize = eowRowHeight * 0.6f,
						rowSize = 4,
						modifier = Modifier.padding(end = 8.dp),
					)
				}
			}

			Spacer(modifier = Modifier.height(gap))

			// Four keyboard rows, each key sized identically
			KEYBOARD_ROWS.forEach { row ->
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.size(keyWidth * 10, keyHeight),
					horizontalArrangement = Arrangement.Center,
				) {
					row.forEach { char ->
						val result = resultsByChar[char.toString()]
						val remainingSeq = result?.semSequence?.trim()
							?.drop(seqOffset).orEmpty()
						SempickKey(
							char = char.uppercaseChar(),
							arrowSequence = remainingSeq,
							active = result != null,
							modifier = Modifier
								.size(keyWidth, keyHeight)
								.padding(2.dp),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun SempickKey(
	char: Char,
	arrowSequence: String,
	active: Boolean,
	modifier: Modifier = Modifier,
) {
	BoxWithConstraints(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.clip(KEY_SHAPE)
			.border(1.dp, KEY_BORDER, KEY_SHAPE)
			.background(
				if (active) KEY_BACKGROUND else KEY_BACKGROUND_INACTIVE,
				KEY_SHAPE,
			),
	) {
		val arrowSize: Dp = maxHeight * 0.22f
		val charFontSp = with(LocalDensity.current) { (maxHeight * 0.38f).toSp() }

		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
		) {
			Text(
				text = char.toString(),
				color = if (active) KEY_CHAR_COLOR else KEY_CHAR_COLOR_INACTIVE,
				fontSize = charFontSp,
				fontWeight = FontWeight.SemiBold,
				textAlign = TextAlign.Center,
			)
			if (active && arrowSequence.isNotEmpty()) {
				SempickArrowSequence(
					sequence = arrowSequence,
					arrowSize = arrowSize,
					rowSize = 3,
				)
			}
		}
	}
}

@Composable
private fun SempickListContent(state: SempickUiState) {
	val colorScheme = JellyfinTheme.colorScheme
	val remaining = state.response?.Remaining ?: 0

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			val previousText = state.response?.PreviousSelections
				?.joinToString("  ") { it.selection }.orEmpty()
			Text(
				text = previousText.ifEmpty { "\u00A0" },
				color = colorScheme.onBackground,
				fontSize = 16.sp,
			)
			if (remaining > 0) {
				Text(
					text = "$remaining items",
					color = colorScheme.onButtonDisabled,
					fontSize = 14.sp,
				)
			}
		}

		val seqOffset = (state.response?.NumberOfPickedPartitions ?: 0) + state.picks.length
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			items(state.filteredResults) { result ->
				val remainingSeq = result.semSequence.trim().drop(seqOffset)
				if (ControlTokens.isGroupFragment(result.fragment.fragment)) {
					// Scrollable list group slot — pressing opens a HeadList sub-selection, not a final pick.
					val previews = result.fragment.GroupItems.orEmpty()
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(colorScheme.button, RoundedCornerShape(4.dp))
							.padding(horizontal = 16.dp, vertical = 8.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.Top,
					) {
						Column(modifier = Modifier.weight(1f)) {
							previews.take(3).forEach { item ->
								Text(
									text = item.Jelly?.getDisplayName() ?: item.fragment,
									color = colorScheme.onButton,
									fontSize = 16.sp,
								)
							}
							if (previews.size > 3) {
								Text(
									text = "  +${previews.size - 3} more…",
									color = colorScheme.onButtonDisabled,
									fontSize = 14.sp,
								)
							}
						}
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("›", color = colorScheme.onButtonDisabled, fontSize = 18.sp)
							Spacer(modifier = Modifier.size(4.dp))
							SempickArrowSequence(
								sequence = remainingSeq,
								arrowSize = 20.dp,
								rowSize = 3,
							)
						}
					}
				} else {
					val name = when (result.fragment.fragment) {
						ControlTokens.KeyboardEndOfWord -> "✓  Use current word"
						ControlTokens.WordEndOfWord     -> "✓  Select current match"
						ControlTokens.ScrollUp          -> "↑  Scroll up"
						ControlTokens.ScrollDown        -> "↓  Scroll down"
						else -> result.fragment.Jelly?.getDisplayName() ?: result.fragment.fragment
					}
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(colorScheme.button, RoundedCornerShape(4.dp))
							.padding(horizontal = 16.dp, vertical = 10.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = name,
							color = colorScheme.onButton,
							fontSize = 18.sp,
							modifier = Modifier.weight(1f),
						)
						SempickArrowSequence(
							sequence = remainingSeq,
							arrowSize = 20.dp,
							rowSize = 3,
						)
					}
				}
			}
		}
	}
}

/**
 * Scrollable list layout for WinnowingStrategyScrollableList.
 *
 * Each D-pad direction maps to a full-width horizontal band laid out spatially:
 *   Up (scroll-up)  — thin dark band at the top
 *   Left (group 0)  — tall chartreuse band, items listed to the right of the arrow
 *   Right (group 1) — tall purple band, items listed to the right of the arrow
 *   Down (scroll-down) — thin dark band at the bottom
 *
 * The Results buckets from the server response are indexed by direction (0=Left … 3=Down),
 * so Results[0] is the Left group, Results[1] is scroll-up, etc.
 */
@Composable
private fun SempickScrollableListContent(state: SempickUiState) {
	val results = state.response?.Results ?: return

	Column(modifier = Modifier.fillMaxSize()) {
		SCROLLABLE_DISPLAY_ORDER.forEach { dir ->
			val item = results.getOrElse(dir) { emptyList() }.firstOrNull() ?: return@forEach
			val isScrollToken = item.fragment.fragment == ControlTokens.ScrollUp ||
				item.fragment.fragment == ControlTokens.ScrollDown
			val isGroup = ControlTokens.isGroupFragment(item.fragment.fragment)
			val bg = if (isScrollToken) SCROLLABLE_SCROLL_BG else SCROLLABLE_GROUP_BG[dir]

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.weight(if (isScrollToken) 1f else 3f)
					.background(bg),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// Direction arrow in a fixed-width column on the left edge of every band
				Box(
					modifier = Modifier
						.width(56.dp)
						.fillMaxHeight(),
					contentAlignment = Alignment.Center,
				) {
					SempickArrow(
						direction = dir,
						size = if (isScrollToken) 32.dp else 40.dp,
					)
				}

				// Content area: item names for group/direct slots; empty for scroll tokens
				if (!isScrollToken) {
					Column(
						modifier = Modifier
							.fillMaxSize()
							.padding(end = 16.dp, top = 6.dp, bottom = 6.dp),
						verticalArrangement = Arrangement.Center,
					) {
						if (isGroup) {
							item.fragment.GroupItems.orEmpty().forEach { groupItem ->
								Text(
									text = groupItem.Jelly?.getDisplayName() ?: groupItem.fragment,
									color = SCROLLABLE_GROUP_TEXT[dir],
									fontSize = 16.sp,
								)
							}
						} else {
							// Direct mode (count ≤ N): single plain item per slot
							Text(
								text = item.fragment.Jelly?.getDisplayName() ?: item.fragment.fragment,
								color = SCROLLABLE_GROUP_TEXT[dir],
								fontSize = 18.sp,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SempickCompletedContent(
	state: SempickUiState,
	onPlay: (String) -> Unit,
) {
	val colorScheme = JellyfinTheme.colorScheme
	val result = state.selectedResult ?: state.filteredResults.firstOrNull()
	val jelly = result?.fragment?.Jelly
	val jellies = result?.fragment?.Jellies
	val displayName = jelly?.getDisplayName() ?: result?.fragment?.fragment ?: ""

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(horizontal = 48.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(
			text = "You Selected: $displayName",
			color = colorScheme.onBackground,
			fontSize = 22.sp,
			fontWeight = FontWeight.Bold,
		)

		if (jelly != null) {
			Button(onClick = { onPlay(jelly.Id) }) {
				Text("Play")
			}
		}

		if (!jellies.isNullOrEmpty()) {
			Text(
				text = "Remaining items (${jellies.size}):",
				color = colorScheme.onBackground,
				fontSize = 16.sp,
				modifier = Modifier.padding(top = 8.dp),
			)
			LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				items(jellies) { item ->
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(
							text = item.getDisplayName(),
							color = colorScheme.onBackground,
							modifier = Modifier.weight(1f),
						)
						Button(onClick = { onPlay(item.Id) }) {
							Text("Play")
						}
					}
				}
			}
		}
	}
}
