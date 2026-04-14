package org.jellyfin.androidtv.ui.sempick

import kotlinx.serialization.Serializable

/** Strategy name strings returned in EngineResult.StrategyName. */
object StrategyNames {
    const val AllWordAndKb   = "AllWordAndKb"
    const val List           = "WinnowingStrategyList"
    const val ScrollableList = "WinnowingStrategyScrollableList"
    /** Older server builds that completed without setting state="Completed". */
    const val Single         = "WinnowingStrategySingle"
}

/** PickState strings returned in EngineResult.state. */
object PickState {
    const val Initial      = "Initial"
    const val InterimList  = "InterimList"
    const val HeadList     = "HeadList"
    const val Completed    = "Completed"
}

/**
 * Named constants for SemPick engine control tokens that appear in fragment.fragment.
 * These are internal routing signals, not display strings — handle each explicitly.
 */
object ControlTokens {
    /** Character keyboard layer: typed prefix forms a complete word for some candidates but not all.
     *  Selecting this commits the prefix as an exact word, discarding longer variants. */
    const val KeyboardEndOfWord = "\u0000"
    /** Word search layer: current word selection fully identifies some items, others have more words.
     *  Selecting this resolves to the exact-match items. */
    const val WordEndOfWord     = "\u0001"
    /** Scrollable list: navigate to the previous page. */
    const val ScrollUp          = "\u0002"
    /** Scrollable list: navigate to the next page. */
    const val ScrollDown        = "\u0003"
    /** Scrollable list group slot prefix (followed by decimal group index, e.g. "\u00040", "\u00041").
     *  Selecting a group opens a HeadList sub-selection — it does NOT complete immediately. */
    const val GroupPrefix       = "\u0004"

    /** Returns true when [fragment] is a group slot (starts with GroupPrefix).
     *  Use this instead of a raw startsWith check — the prefix is a Unicode control character. */
    fun isGroupFragment(fragment: String) = fragment.startsWith(GroupPrefix)
}

@Serializable
data class SempickResponse(
	val Results: List<List<SemPickResult>> = emptyList(),
	val PreviousSelections: List<PreviousSelection> = emptyList(),
	val Remaining: Int = 0,
	val NumberOfPickedPartitions: Int = 0,
	val StrategyName: String? = null,
	val state: String? = null,
) {
	val allResults: List<SemPickResult> get() = Results.flatten()
}

@Serializable
data class SemPickResult(
	val fragment: FragmentData,
	val semSequence: String,
)

@Serializable
data class FragmentData(
	val fragment: String,
	val Count: Int = 0,
	val Index: Int = 0,
	val Jelly: JellyItem? = null,
	val Jellies: List<JellyItem>? = null,
	// Populated on ScrollGroupFragment slots in WinnowingStrategyScrollableList.
	// Each entry is a preview item inside the group; pressing the group button
	// opens a HeadList sub-selection containing these items.
	val GroupItems: List<GroupItemFragment>? = null,
)

@Serializable
data class GroupItemFragment(
	val fragment: String,
	val Count: Int = 0,
	val Index: Int = 0,
	val Jelly: JellyItem? = null,
)

@Serializable
data class JellyItem(
	val Id: String,
	val Name: String,
	val Item: BaseItemDto? = null,
) {
    fun getDisplayName(): String {
        val item = this.Item ?: return this.Name
        val symbol = when (item.Type) {
            1, 16, 17 -> "🎵" // Audio, MusicAlbum, MusicArtist
            9, 28 -> "📺"    // Episode, Series
            13 -> "🎬"       // Movie
            else -> ""
        }

        val label = when (item.Type) {
            1 -> { // Audio (Music)
                val artist = item.Artists?.firstOrNull() ?: ""
                if (artist.isNotEmpty()) "${item.Name ?: this.Name} - $artist" else (item.Name ?: this.Name)
            }
            9 -> { // Episode (Show)
                val s = item.ParentIndexNumber?.let { "S${it.toString().padStart(2, '0')}" } ?: ""
                val e = item.IndexNumber?.let { "E${it.toString().padStart(2, '0')}" } ?: ""
                val se = if (s.isNotEmpty() || e.isNotEmpty()) "$s$e " else ""
                val series = item.SeriesName ?: ""
                val episode = item.Name ?: this.Name
                if (series.isNotEmpty()) "$se$series - $episode" else "$se$episode"
            }
            13 -> { // Movie
                val name = item.Name ?: this.Name
                val year = item.ProductionYear?.toString() ?: ""
                if (year.isNotEmpty()) "$name ($year)" else name
            }
            else -> item.Name ?: this.Name
        }

        return if (symbol.isNotEmpty()) "$symbol $label" else label
    }
}

@Serializable
data class BaseItemDto(
	val Id: String? = null,
	val Name: String? = null,
	val Type: Int? = null,  // C# BaseItemKind enum: 1=Audio, 16=MusicAlbum, 23=Playlist, 13=Movie, 9=Episode, 28=Series
    val ProductionYear: Int? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val SeriesName: String? = null,
    val Artists: List<String>? = null,
)

@Serializable
data class PreviousSelection(
	val selection: String,
)
