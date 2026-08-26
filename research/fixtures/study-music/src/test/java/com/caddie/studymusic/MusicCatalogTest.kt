package com.caddie.studymusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCatalogTest {
    @Test
    fun exposedListsRejectSetAddAndRemove() {
        val failures = mutableListOf<String>()

        exposedLists().forEach { (name, items) ->
            @Suppress("UNCHECKED_CAST")
            val mutableItems = items as MutableList<MusicItem>
            val first = items.first()
            val mutations = listOf<Pair<String, () -> Unit>>(
                "set" to { mutableItems[0] = first },
                "add" to { mutableItems.add(first) },
                "remove" to { mutableItems.removeAt(0) },
            )

            mutations.forEach { (operation, mutation) ->
                val before = mutableItems.toList()
                val thrown = runCatching(mutation).exceptionOrNull()
                if (mutableItems != before) {
                    mutableItems.clear()
                    mutableItems.addAll(before)
                }
                if (thrown !is UnsupportedOperationException) {
                    failures += "$name.$operation did not throw UnsupportedOperationException"
                }
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun groupsHaveExactDeterministicMembership() {
        assertEquals(
            listOf(
                "midnight_drive",
                "soft_focus",
                "coastal_lines",
                "afterglow",
                "quiet_hours",
                "neon_rain",
                "open_skies",
                "daylight",
                "slow_motion",
                "weekend_radio",
                "deep_work",
                "night_bus",
            ),
            MusicCatalog.all.map(MusicItem::id),
        )
        assertEquals(
            listOf(
                "midnight_drive",
                "soft_focus",
                "coastal_lines",
                "afterglow",
                "quiet_hours",
                "neon_rain",
            ),
            MusicCatalog.quickAccess.map(MusicItem::id),
        )
        assertEquals(MusicCatalog.quickAccess.map(MusicItem::id), MusicCatalog.recent.map(MusicItem::id))
        assertEquals(
            listOf(
                "quiet_hours",
                "neon_rain",
                "open_skies",
                "daylight",
                "slow_motion",
                "weekend_radio",
            ),
            MusicCatalog.madeForYou.map(MusicItem::id),
        )
        assertEquals(
            listOf(
                "open_skies",
                "daylight",
                "slow_motion",
                "weekend_radio",
                "deep_work",
                "night_bus",
            ),
            MusicCatalog.popular.map(MusicItem::id),
        )
    }

    private fun exposedLists(): List<Pair<String, List<MusicItem>>> = listOf(
        "all" to MusicCatalog.all,
        "quickAccess" to MusicCatalog.quickAccess,
        "recent" to MusicCatalog.recent,
        "madeForYou" to MusicCatalog.madeForYou,
        "popular" to MusicCatalog.popular,
    )
}
