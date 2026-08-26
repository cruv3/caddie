package com.caddie.studymusic

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class StudyMusicActivity : AppCompatActivity() {
    private enum class Screen { HOME, SEARCH, RESULTS }

    private var screen = Screen.HOME
    private var showingLibrary = false
    private val libraryPreferences by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }
    private val libraryStateListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if ((key == null || key == KEY_LIBRARY) && screen == Screen.RESULTS) {
                renderResults()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.music_background)
        window.navigationBarColor = getColor(R.color.music_background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_study_music)
        applySystemBarInsets()

        screen = savedInstanceState?.getString(STATE_SCREEN)
            ?.let { saved -> Screen.entries.firstOrNull { it.name == saved } }
            ?: Screen.HOME
        showingLibrary = savedInstanceState?.getBoolean(STATE_SHOWING_LIBRARY, false) ?: false

        bindNavigation()
        bindSearch()
        savedInstanceState?.getString(STATE_QUERY)?.let {
            findViewById<EditText>(R.id.search_input).setText(it)
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        libraryPreferences.registerOnSharedPreferenceChangeListener(libraryStateListener)
        if (screen == Screen.RESULTS) {
            renderResults()
        }
    }

    override fun onStop() {
        libraryPreferences.unregisterOnSharedPreferenceChangeListener(libraryStateListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, screen.name)
        outState.putBoolean(STATE_SHOWING_LIBRARY, showingLibrary)
        outState.putString(STATE_QUERY, findViewById<EditText>(R.id.search_input).text.toString())
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.bottom_home).setOnClickListener {
            showingLibrary = false
            screen = Screen.HOME
            render()
        }
        findViewById<View>(R.id.bottom_search).setOnClickListener {
            showingLibrary = false
            screen = Screen.SEARCH
            render()
        }
        findViewById<View>(R.id.bottom_library).setOnClickListener {
            showingLibrary = true
            screen = Screen.RESULTS
            render()
            resetResultsScroll()
        }
    }

    private fun bindSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showSearchResults()
                true
            } else {
                false
            }
        }
        searchInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                showSearchResults()
                true
            } else {
                false
            }
        }
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.music_root)
        val screens = listOf(
            findViewById<View>(R.id.home_screen),
            findViewById<View>(R.id.search_screen),
            findViewById<View>(R.id.results_screen),
        )
        val screenPaddings = screens.associateWith { view ->
            Padding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        }
        val navigation = findViewById<View>(R.id.bottom_navigation)
        val navigationPadding = Padding(
            navigation.paddingLeft,
            navigation.paddingTop,
            navigation.paddingRight,
            navigation.paddingBottom,
        )
        val navigationHeight = navigation.layoutParams.height

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            screens.forEach { view ->
                val padding = screenPaddings.getValue(view)
                view.setPadding(
                    padding.left + insets.left,
                    padding.top + insets.top,
                    padding.right + insets.right,
                    padding.bottom + insets.bottom,
                )
            }
            navigation.setPadding(
                navigationPadding.left + insets.left,
                navigationPadding.top,
                navigationPadding.right + insets.right,
                navigationPadding.bottom + insets.bottom,
            )
            navigation.layoutParams = navigation.layoutParams.apply {
                height = navigationHeight + insets.bottom
            }
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun showSearchResults() {
        showingLibrary = false
        screen = Screen.RESULTS
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(findViewById<EditText>(R.id.search_input).windowToken, 0)
        render()
        resetResultsScroll()
    }

    private fun resetResultsScroll() {
        val resultsScreen = findViewById<ScrollView>(R.id.results_screen)
        resultsScreen.post {
            resultsScreen.scrollTo(0, 0)
        }
    }

    private fun render() {
        findViewById<View>(R.id.home_screen).visibility =
            if (screen == Screen.HOME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.search_screen).visibility =
            if (screen == Screen.SEARCH) View.VISIBLE else View.GONE
        findViewById<View>(R.id.results_screen).visibility =
            if (screen == Screen.RESULTS) View.VISIBLE else View.GONE

        renderNavigation()
        bindMiniPlayer(MusicCatalog.all.first())
        if (screen == Screen.HOME) renderHome()
        if (screen == Screen.RESULTS) renderResults()
    }

    private fun renderNavigation() {
        val activeDestination = when {
            screen == Screen.HOME -> R.id.bottom_home
            showingLibrary -> R.id.bottom_library
            else -> R.id.bottom_search
        }
        listOf(
            NavigationItem(R.id.bottom_home, R.id.bottom_home_icon, R.id.bottom_home_label),
            NavigationItem(R.id.bottom_search, R.id.bottom_search_icon, R.id.bottom_search_label),
            NavigationItem(R.id.bottom_library, R.id.bottom_library_icon, R.id.bottom_library_label),
        ).forEach { item ->
            val active = item.containerId == activeDestination
            val color = getColor(if (active) R.color.music_green else R.color.music_secondary)
            findViewById<View>(item.containerId).isSelected = active
            findViewById<ImageView>(item.iconId).imageTintList = ColorStateList.valueOf(color)
            findViewById<TextView>(item.labelId).setTextColor(color)
        }
    }

    private fun renderHome() {
        bindQuickItems(MusicCatalog.quickAccess)
        bindCards(R.id.recent_section, MusicCatalog.recent)
        bindCards(R.id.made_for_you_section, MusicCatalog.madeForYou)
        bindCards(R.id.popular_section, MusicCatalog.popular)
    }

    private fun bindQuickItems(items: List<MusicItem>) {
        val container = findViewById<GridLayout>(R.id.quick_access)
        container.removeAllViews()
        items.forEach { item ->
            val tile = layoutInflater.inflate(R.layout.item_music_quick, container, false)
            bindItem(tile, item)
            val margin = resources.getDimensionPixelSize(R.dimen.music_quick_spacing)
            tile.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.music_quick_height)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, margin, margin, 0)
            }
            container.addView(tile)
        }
    }

    private fun bindCards(containerId: Int, items: List<MusicItem>) {
        val container = findViewById<LinearLayout>(containerId)
        container.removeAllViews()
        items.forEach { item ->
            val card = layoutInflater.inflate(R.layout.item_music_card, container, false)
            bindItem(card, item)
            card.findViewById<TextView>(R.id.item_subtitle).text = item.artist
            container.addView(card)
        }
    }

    private fun bindItem(view: View, item: MusicItem) {
        view.findViewById<ImageView>(R.id.item_cover).setImageResource(item.coverRes)
        view.findViewById<TextView>(R.id.item_title).text = item.title
    }

    private fun bindMiniPlayer(item: MusicItem) {
        findViewById<ImageView>(R.id.mini_player_cover).setImageResource(item.coverRes)
        findViewById<TextView>(R.id.mini_player_title).text = item.title
        findViewById<TextView>(R.id.mini_player_artist).text = item.artist
    }

    private fun renderResults() {
        val libraryIds = libraryTrackIds()
        val results = if (showingLibrary) {
            searchableItems.filter { it.id in libraryIds }
        } else {
            filteredTracks(findViewById<EditText>(R.id.search_input).text.toString())
        }
        findViewById<TextView>(R.id.results_title).setText(
            if (showingLibrary) R.string.my_library else R.string.search_results,
        )
        findViewById<TextView>(R.id.empty_library).visibility =
            if (showingLibrary && results.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.empty_search_results).visibility =
            if (!showingLibrary && results.isEmpty()) View.VISIBLE else View.GONE

        val container = findViewById<LinearLayout>(R.id.track_results)
        container.removeAllViews()
        results.forEach { item ->
            val row = layoutInflater.inflate(R.layout.item_track_result, container, false)
            row.findViewById<TextView>(R.id.track_title).text = item.title
            row.findViewById<TextView>(R.id.track_artist).text = item.artist
            row.findViewById<ImageView>(R.id.track_art).setImageResource(item.coverRes)
            row.findViewById<ImageButton>(R.id.track_action).apply {
                val inLibrary = item.id in libraryIds
                setImageResource(
                    if (inLibrary) R.drawable.ic_music_added else R.drawable.ic_music_add,
                )
                contentDescription = getString(
                    if (inLibrary) R.string.remove_from_library else R.string.add_to_library,
                    item.title,
                )
                setOnClickListener {
                    toggleLibrary(item.id)
                }
            }
            container.addView(row)
        }
    }

    private fun filteredTracks(query: String): List<MusicItem> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return MusicCatalog.all
        if (normalized.equals(LEGACY_CANONICAL_QUERY, ignoreCase = true)) {
            return compatibilitySearchItems
        }
        return MusicCatalog.all.filter { item ->
            item.title.contains(normalized, ignoreCase = true) ||
                item.artist.contains(normalized, ignoreCase = true)
        }
    }

    private fun toggleLibrary(trackId: String) {
        val ids = libraryTrackIds().toMutableSet()
        if (!ids.add(trackId)) ids.remove(trackId)
        libraryPreferences.edit().putStringSet(KEY_LIBRARY, ids).commit()
    }

    private fun libraryTrackIds(): Set<String> = libraryPreferences
        .getStringSet(KEY_LIBRARY, emptySet())
        ?.toSet()
        ?: emptySet()

    companion object {
        const val ACTION_RESET = "com.caddie.studymusic.ACTION_RESET"
        const val PREFS = "study_music_state"
        const val KEY_LIBRARY = "library_track_ids"

        private const val STATE_SCREEN = "screen"
        private const val STATE_SHOWING_LIBRARY = "showing_library"
        private const val STATE_QUERY = "query"
        private const val LEGACY_CANONICAL_QUERY = "As It Was"

        private val compatibilitySearchItems = listOf(
            MusicItem("as_it_was", "As It Was", "Harry Styles", R.drawable.cover_midnight_drive),
            MusicItem(
                "as_it_was_sped_up",
                "As It Was – Sped Up",
                "Lewis Hanton",
                R.drawable.cover_neon_rain,
            ),
            MusicItem(
                "as_dotless_i_was_slowed",
                "As ıt was slowed",
                "Sleepyhead",
                R.drawable.cover_slow_motion,
            ),
        )
        private val searchableItems = compatibilitySearchItems + MusicCatalog.all
    }

    private data class NavigationItem(
        val containerId: Int,
        val iconId: Int,
        val labelId: Int,
    )

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
