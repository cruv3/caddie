package com.caddie.studymusic

import androidx.annotation.DrawableRes
import java.util.Collections

data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    @get:DrawableRes val coverRes: Int,
)

/** A small, bundled catalog used by the offline study-music demo. */
object MusicCatalog {
    val all: List<MusicItem> = immutable(
        listOf(
            MusicItem("midnight_drive", "Midnight Drive", "Nova Lane", R.drawable.cover_midnight_drive),
            MusicItem("soft_focus", "Soft Focus", "Mira Vale", R.drawable.cover_soft_focus),
            MusicItem("coastal_lines", "Coastal Lines", "The Paper Suns", R.drawable.cover_coastal_lines),
            MusicItem("afterglow", "Afterglow", "June Atlas", R.drawable.cover_afterglow),
            MusicItem("quiet_hours", "Quiet Hours", "North Rooms", R.drawable.cover_quiet_hours),
            MusicItem("neon_rain", "Neon Rain", "Kite Theory", R.drawable.cover_neon_rain),
            MusicItem("open_skies", "Open Skies", "Lena Rivers", R.drawable.cover_open_skies),
            MusicItem("daylight", "Daylight", "Common Ground", R.drawable.cover_daylight),
            MusicItem("slow_motion", "Slow Motion", "Atlas Bloom", R.drawable.cover_slow_motion),
            MusicItem("weekend_radio", "Weekend Radio", "City Static", R.drawable.cover_weekend_radio),
            MusicItem("deep_work", "Deep Work", "Focus State", R.drawable.cover_deep_work),
            MusicItem("night_bus", "Night Bus", "Mono Club", R.drawable.cover_night_bus),
        ),
    )

    val quickAccess: List<MusicItem> = immutable(all.take(6))
    val recent: List<MusicItem> = immutable(all.take(6))
    val madeForYou: List<MusicItem> = immutable(all.slice(4..9))
    val popular: List<MusicItem> = immutable(all.slice(6..11))

    private fun immutable(items: List<MusicItem>): List<MusicItem> =
        Collections.unmodifiableList(items.toList())
}
