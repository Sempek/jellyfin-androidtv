package org.jellyfin.androidtv.ui.sempick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.SempickRepository

data class SempickUiState(
	val response: SempickResponse? = null,
	val picks: String = "",
	val filteredResults: List<SemPickResult> = emptyList(),
	val userSelections: List<String> = emptyList(),
	val undoHistory: List<UndoEntry> = emptyList(),
	val error: String? = null,
	val loading: Boolean = false,
) {
	val isCompleted: Boolean
		get() = response?.state == PickState.Completed ||
			response?.StrategyName == StrategyNames.Single  // fallback for older server builds

	/** True for any list strategy. Used by input handling to choose between
	 *  direct server round-trip (list) and local narrowing (keyboard). */
	val isList: Boolean
		get() = response?.StrategyName == StrategyNames.List ||
			response?.StrategyName == StrategyNames.ScrollableList

	val selectedResult: SemPickResult?
		get() = if (filteredResults.size == 1) filteredResults.first() else null
}

data class UndoEntry(
	val userSelections: List<String>,
	val picks: String,
)

class SempickViewModel(
	private val sempickRepository: SempickRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(SempickUiState())
	val state = _state.asStateFlow()

	init {
		loadItems()
	}

	fun loadItems(userSelections: List<String> = emptyList(), restorePicks: String = "") {
		_state.update { it.copy(loading = true, error = null) }
		viewModelScope.launch {
			sempickRepository.getItems(userSelections).fold(
				onSuccess = { response ->
					val allResults = response.allResults
					val filteredResults = if (restorePicks.isEmpty()) allResults
						else allResults.filter { it.semSequence.trim().startsWith(restorePicks) }
					_state.update { it.copy(
						response = response,
						picks = restorePicks,
						filteredResults = filteredResults,
						userSelections = userSelections,
						loading = false,
					) }
				},
				onFailure = { err ->
					_state.update { it.copy(loading = false, error = err.message ?: "Failed to load") }
				},
			)
		}
	}

	// Called when the user presses a D-pad direction. directionIndex: 0=Left, 1=Up, 2=Right, 3=Down
	fun onDirectionPressed(directionIndex: Int) {
		val current = _state.value
		if (current.loading || current.isCompleted) return

		if (current.isList) {
			// List mode (plain or scrollable): every button press is a direct server round-trip.
			// There is no local narrowing — the direction index is the entire pick.
			// This covers scroll-token presses (server advances the page), group-slot presses
			// (server opens the sub-HeadList), and direct item presses (server completes).
			val newPicks = directionIndex.toString()
			val newHistory = current.undoHistory + UndoEntry(current.userSelections, current.picks)
			val newSelections = current.userSelections + newPicks
			_state.update { it.copy(undoHistory = newHistory) }
			loadItems(newSelections)
			return
		}

		// Keyboard mode: narrow locally until a single candidate remains, then commit.
		val newPicks = current.picks + directionIndex.toString()
		val newFiltered = current.filteredResults.filter { it.semSequence.trim().startsWith(newPicks) }

		if (newFiltered.isEmpty()) return // invalid direction, ignore

		if (newFiltered.size == 1) {
			// Single candidate — commit this pick and round-trip to the server
			val newHistory = current.undoHistory + UndoEntry(current.userSelections, current.picks)
			val newSelections = current.userSelections + newPicks
			_state.update { it.copy(undoHistory = newHistory) }
			loadItems(newSelections)
		} else {
			_state.update { it.copy(picks = newPicks, filteredResults = newFiltered) }
		}
	}

	fun onUndo() {
		val current = _state.value
		if (!current.isList && current.picks.isNotEmpty()) {
			// Keyboard mode only: undo one locally-accumulated pick character
			val shorterPicks = current.picks.dropLast(1)
			val allResults = current.response?.allResults ?: emptyList()
			val refiltered = if (shorterPicks.isEmpty()) allResults
			else allResults.filter { it.semSequence.trim().startsWith(shorterPicks) }
			_state.update { it.copy(picks = shorterPicks, filteredResults = refiltered) }
		} else if (current.undoHistory.isNotEmpty()) {
			// Undo a committed pick (always the path in list mode; fallback in keyboard mode
			// when no local picks remain) — go back to the previous server state
			val lastEntry = current.undoHistory.last()
			val newHistory = current.undoHistory.dropLast(1)
			_state.update { it.copy(undoHistory = newHistory) }
			loadItems(lastEntry.userSelections, restorePicks = lastEntry.picks)
		}
		// No history and no local picks: safe no-op (matches spec §5e)
	}
}
