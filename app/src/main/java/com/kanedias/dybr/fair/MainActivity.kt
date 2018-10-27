package com.kanedias.dybr.fair

import android.app.FragmentTransaction
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.AppBarLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v4.widget.SimpleCursorAdapter
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import butterknife.BindView
import butterknife.OnClick
import butterknife.ButterKnife
import com.afollestad.materialdialogs.MaterialDialog
import com.ftinc.scoop.Scoop
import com.kanedias.dybr.fair.database.DbProvider
import com.kanedias.dybr.fair.database.entities.Account
import com.kanedias.dybr.fair.database.entities.SearchGotoInfo
import com.kanedias.dybr.fair.database.entities.SearchGotoInfo.*
import com.kanedias.dybr.fair.dto.*
import com.kanedias.dybr.fair.scheduling.SyncNotificationsJob
import com.kanedias.dybr.fair.themes.*
import com.kanedias.dybr.fair.ui.Sidebar
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

/**
 * Main activity with drawer and sliding tabs where most of user interaction happens.
 * This activity is what you see when you start Fair app.
 *
 * @author Kanedias
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val MY_DIARY_TAB = 0
        private const val FAV_TAB = 1
        private const val WORLD_TAB = 2
        private const val NOTIFICATIONS_TAB = 3

        var onScreen = false
    }


    /**
     * AppBar layout with toolbar and tabs
     */
    @BindView(R.id.header)
    lateinit var appBar: AppBarLayout

    /**
     * Actionbar header (where app title is written)
     */
    @BindView(R.id.toolbar)
    lateinit var toolbar: Toolbar

    /**
     * Main drawer, i.e. two panes - content and sidebar
     */
    @BindView(R.id.main_drawer_layout)
    lateinit var drawer: DrawerLayout

    @BindView(R.id.content_view)
    lateinit var pager: ViewPager
    /**
     * Tabs with favorites that are placed under actionbar
     */
    @BindView(R.id.sliding_tabs)
    lateinit var tabs: TabLayout

    /**
     * Top-level sidebar content list view
     */
    @BindView(R.id.main_sidebar_area)
    lateinit var sidebarArea: LinearLayout

    /**
     * Floating button
     */
    @BindView(R.id.floating_button)
    lateinit var actionButton: FloatingActionButton

    /**
     * Sidebar that opens from the left (the second part of drawer)
     */
    private lateinit var sidebar: Sidebar

    /**
     * App-global shared preferences for small config changes not suitable for database
     * (seen-marks, first launch options etc.)
     */
    private lateinit var preferences: SharedPreferences

    private lateinit var donateHelper: DonateHelper

    /**
     * Tab adapter for [pager] <-> [tabs] synchronisation
     */
    private val tabAdapter = TabAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        // init preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        donateHelper = DonateHelper(this)

        // set app bar
        setSupportActionBar(toolbar)
        // setup click listeners, adapters etc.
        setupUI()
        // load user profile and initialize tabs
        reLogin(Auth.user)
    }

    override fun onResume() {
        super.onResume()
        onScreen = true

        // cancel all notifications
        val nm = NotificationManagerCompat.from(this)
        nm.cancelAll()
    }

    override fun onPause() {
        super.onPause()
        onScreen = false
    }

    private fun setupUI() {
        // init drawer and sidebar
        sidebar = Sidebar(drawer, this)

        // cross-join drawer and menu item in header
        val drawerToggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.open, R.string.close)
        drawer.addDrawerListener(drawerToggle)
        drawer.addDrawerListener(object: DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                // without this buttons are non-clickable after activity layout changes
                sidebarArea.bringToFront()
            }
        })
        drawerToggle.syncState()

        // setup tabs
        tabs.setupWithViewPager(pager, true)
        pager.adapter = tabAdapter

        // handle first launch
        if (preferences.getBoolean("first-app-launch", true)) {
            drawer.openDrawer(GravityCompat.START)
            preferences.edit().putBoolean("first-app-launch", false).apply()
        }

        setupTheming()
    }

    private fun setupTheming() {
        Scoop.getInstance().bind(TOOLBAR, toolbar)
        Scoop.getInstance().bind(TOOLBAR_TEXT, toolbar, ToolbarTextAdapter())
        Scoop.getInstance().bind(TOOLBAR, tabs)
        Scoop.getInstance().bind(TOOLBAR_TEXT, tabs, TabLayoutTextAdapter())
        Scoop.getInstance().bind(ACCENT, tabs, TabLayoutLineAdapter())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_action_bar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        setupTopSearch(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupTopSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.menu_search)
        val searchView = searchItem.actionView as SearchView
        val initialSuggestions = constructSuggestions("")
        val searchAdapter = SimpleCursorAdapter(this, R.layout.activity_main_search_row, initialSuggestions,
                arrayOf("name", "type", "source"),
                intArrayOf(R.id.search_name, R.id.search_type, R.id.search_source), 0)

        // initialize adapter, text listener and click handler
        searchView.queryHint = getString(R.string.go_to)
        searchView.suggestionsAdapter = searchAdapter
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            private fun handle(query: String?): Boolean {
                if (query.isNullOrEmpty())
                    return true

                searchAdapter.changeCursor(constructSuggestions(query!!))
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                return handle(query)
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return handle(newText)
            }

        })
        searchView.setOnSuggestionListener(object: SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                searchItem.collapseActionView()

                val cursor = searchAdapter.getItem(position) as Cursor
                val name = cursor.getString(cursor.getColumnIndex("name"))
                val type = EntityType.valueOf(cursor.getString(cursor.getColumnIndex("type")))

                // jump to respective blog or profile if they exist
                launch(UI) {
                    try {
                        when (type) {
                            EntityType.PROFILE -> {
                                val prof = async { Network.loadProfileByNickname(name) }.await()
                                val fragment = ProfileFragment().apply { profile = prof }
                                fragment.show(supportFragmentManager, "Showing search-requested profile fragment")
                            }
                            EntityType.BLOG -> {
                                val blog = async { Network.loadBlogBySlug(name) }.await()
                                val fragment = EntryListFragmentFull().apply { this.blog = blog }
                                supportFragmentManager.beginTransaction()
                                        .addToBackStack("Showing search-requested address")
                                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                                        .add(R.id.main_drawer_layout, fragment)
                                        .commit()
                            }
                        }
                        persistSearchSuggestion(type, name)
                    } catch (ex: Exception) {
                        Network.reportErrors(this@MainActivity, ex, mapOf(404 to R.string.not_found))
                    }
                }

                return true
            }

            private fun persistSearchSuggestion(type: EntityType, name: String) {
                // persist to saved searches if unique index does not object
                val dao = DbProvider.helper.gotoDao
                val unique = dao.queryBuilder().apply { where().eq("type", type).and().eq("name", name) }.query()
                if (unique.isEmpty()) { // don't know how to make this check better, like "replace-on-conflict"
                    dao.createOrUpdate(SearchGotoInfo(type, name))
                }
            }
        })
    }

    private fun constructSuggestions(prefix: String): Cursor {
        // first, collect suggestions from the favorites if we have them
        val favProfiles = Auth.profile?.favorites?.get(Auth.profile?.document) ?: emptyList()
        val suitableFavs = favProfiles.filter { it.nickname.startsWith(prefix) }

        // second, collect suggestions that we already searched for
        val suitableSaved = DbProvider.helper.gotoDao.queryBuilder()
                .where().like("name", "$prefix%").query()

        // we need a counter as MatrixCursor requires _id field unfortunately
        var counter = 0
        val cursor = MatrixCursor(arrayOf("_id", "name", "type", "source"), suitableFavs.size)
        if (prefix.isNotEmpty()) {
            // add two service rows with just prefix
            cursor.addRow(arrayOf(++counter, prefix, EntityType.BLOG.name, getString(R.string.direct_jump)))
            cursor.addRow(arrayOf(++counter, prefix, EntityType.PROFILE.name, getString(R.string.direct_jump)))
        }

        // add suitable addresses from favorites and database
        suitableFavs.forEach { cursor.addRow(arrayOf(++counter, it.nickname, EntityType.PROFILE.name, getString(R.string.from_profile_favorites))) }
        suitableSaved.forEach { cursor.addRow(arrayOf(++counter, it.name, it.type, getString(R.string.from_saved_searches))) }

        return cursor
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_donate -> donateHelper.donate()
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.menu_refresh -> refreshCurrentTab()
            else -> return super.onOptionsItemSelected(item)
        }

        // it was handled in `when` block or we wouldn't be at this point
        // confirm it
        return true
    }

    /**
     * Find current tab and try to refresh its contents
     */
    private fun refreshCurrentTab() {
        val plPredicate = { it: Fragment -> it is EntryListFragment && it.userVisibleHint }
        val currentTab = supportFragmentManager.fragments.find(plPredicate) as EntryListFragment?
        currentTab?.refreshEntries()
    }

    override fun onBackPressed() {
        // close drawer if it's open and stop processing further back actions
        if (drawer.isDrawerOpen(GravityCompat.START) || drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawers()
            return
        }

        super.onBackPressed()
    }

    /**
     * Logs in with specified account. This must be the very first action after opening the app.
     *
     * @param acc account to be logged in with
     */
    fun reLogin(acc: Account) {
        if (acc === Auth.guest) {
            // we're logging in as guest, skip auth
            Auth.updateCurrentUser(Auth.guest)
            refresh()
            return
        }

        launch(UI) {
            val progressDialog = MaterialDialog.Builder(this@MainActivity)
                    .progress(true, 0)
                    .cancelable(false)
                    .title(R.string.please_wait)
                    .content(R.string.logging_in)
                    .build()
            progressDialog.show()

            try {
                // login with this account and reset profile/blog links
                async { Network.login(acc) }.await()

                if (Auth.user.lastProfileId == null) {
                    // first time we're loading this account, select profile
                    startProfileSelector()
                } else {
                    // we already have loaded profile
                    // all went well, report if we should
                    Toast.makeText(this@MainActivity, R.string.login_successful, Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Network.reportErrors(this@MainActivity, ex)
                becomeGuest()
            }

            progressDialog.dismiss()
            refresh()
        }
    }

    override fun onNewIntent(received: Intent) {
        super.onNewIntent(received)
        handleIntent(received)
    }

    /**
     * Handle the passed intent. This is invoked whenever we need to actually react to the intent that was
     * passed to this activity, this can be just activity start from the app manager, click on a link or
     * on a notification belonging to this app
     * @param cause the passed intent. It will not be modified within this function.
     */
    private fun handleIntent(cause: Intent?) {
        if (cause == null)
            return

        when (cause.action) {
            ACTION_NOTIF_OPEN -> {
                for (i in 0..supportFragmentManager.backStackEntryCount) {
                    supportFragmentManager.popBackStack()
                }
                pager.setCurrentItem(NOTIFICATIONS_TAB, true)
            }

            Intent.ACTION_VIEW -> launch(UI) {
                // try to detect if it's someone trying to open the dybr.ru link with us
                if (cause.data?.authority?.contains("dybr.ru") == true) {
                    consumeCallingUrl(cause)
                }
            }
        }
    }

    /**
     * Take URI from the activity's intent, try to shape it into something usable
     * and handle the action user requested in it if possible. E.g. clicking on link
     * https://dybr.ru/blog/... should open that blog or entry inside the app so try
     * to guess what user wanted with it as much as possible.
     */
    private suspend fun consumeCallingUrl(cause: Intent) {
        try {
            val address = cause.data.pathSegments // it's in the form of /blog/<slug>/[<entry>]
            when(address[0]) {
                "blog" -> {
                    val fragment = when (address.size) {
                        2 -> {  // the case for /blog/<slug>
                            val blog = async { Network.loadBlogBySlug(address[1]) }.await()
                            EntryListFragmentFull().apply { this.blog = blog }
                        }
                        3 -> { // the case for /blog/<slug>/<entry>
                            val entry = async { Network.loadEntry(address[2]) }.await()
                            CommentListFragment().apply { this.entry = entry }
                        }
                        else -> return
                    }

                    supportFragmentManager.beginTransaction()
                            .addToBackStack("Showing intent-requested address")
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                            .add(R.id.main_drawer_layout, fragment)
                            .commit()
                }
                "profile" -> {
                    val fragment = when (address.size) {
                        2 -> { // the case for /profile/<id>
                            val profile = async { Network.loadProfile(address[1]) }.await()
                            ProfileFragment().apply { this.profile = profile }
                        }
                        else -> return
                    }

                    fragment.show(supportFragmentManager, "Showing intent-requested profile fragment")
                }
                else -> return
            }
        } catch (ex: Exception) {
            Network.reportErrors(this@MainActivity, ex)
        }
    }

    /**
     * Load profiles for logged in user and show selector dialog to user.
     * If there's just one profile, select it right away.
     * If there are no any profiles for this user, suggest to create it.
     *
     * This should be invoked after [Auth.user] is populated
     *
     * @param showIfOne whether to show dialog if only one profile is available
     */
    suspend fun startProfileSelector(showIfOne: Boolean = false) {
        // retrieve profiles from server
        val profiles = async { Network.loadUserProfiles() }.await()

        // predefine what to do if new profile is needed

        if (profiles.isEmpty()) {
            // suggest user to create profile
            drawer.closeDrawers()
            MaterialDialog.Builder(this)
                    .title(R.string.switch_profile)
                    .content(R.string.no_profiles_create_one)
                    .negativeText(R.string.no_profile)
                    .onNegative { _, _ -> Auth.user.lastProfileId = "0"; refresh() } // set to null so it won't ask next time
                    .positiveText(R.string.create_new)
                    .onPositive { _, _ -> addProfile() }
                    .show()
            return
        }

        if (profiles.size == 1 && !showIfOne) {
            // we have only one profile, use it
            Auth.updateCurrentProfile(profiles[0])
            refresh()
            return
        }

        // we have multiple accounts to select from
        val profAdapter = ProfileListAdapter(profiles.toMutableList())
        val dialog = MaterialDialog.Builder(this)
                .title(R.string.switch_profile)
                .adapter(profAdapter, LinearLayoutManager(this))
                .negativeText(R.string.no_profile)
                .onNegative { _, _ -> Auth.user.lastProfileId = "0"; refresh() }
                .positiveText(R.string.create_new)
                .onPositive { _, _ -> addProfile() }
                .show()
        profAdapter.toDismiss = dialog
    }

    /**
     * Called when user selects profile from a dialog
     */
    private fun selectProfile(prof: OwnProfile) {
        // need to retrieve selected profile fully, i.e. with favorites and stuff
        launch(UI) {
            try {
                async { Network.makeProfileActive(prof) }.await()
                val fullProf = async { Network.loadProfile(prof.id) }.await()
                Auth.updateCurrentProfile(fullProf)
                refresh()
            } catch (ex: Exception) {
                Network.reportErrors(this@MainActivity, ex)
                becomeGuest()
            }
        }
    }

    /**
     * Show "Add profile" fragment
     */
    fun addProfile() {
        supportFragmentManager.beginTransaction()
                .addToBackStack("Showing profile creation fragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(R.id.main_drawer_layout, AddProfileFragment())
                .commit()
    }

    /**
     * Show "Add blog" fragment. Only one blog can belong to specific profile
     */
    fun createBlog() {
        supportFragmentManager.beginTransaction()
                .addToBackStack("Showing blog creation fragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(R.id.main_drawer_layout, AddBlogFragment())
                .commit()
    }


    /**
     * Delete specified profile and report it
     * @param prof profile to remove
     */
    private fun deleteProfile(prof: OwnProfile) {
        launch(UI) {
            try {
                async { Network.removeProfile(prof) }.await()
                Toast.makeText(this@MainActivity, R.string.profile_deleted, Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                Network.reportErrors(this@MainActivity, ex)
            }

            if (Auth.profile == prof) {
                // we deleted current profile, need to become guest
                becomeGuest()
            }
        }
    }

    /**
     * What to do if auth failed/current account/profile was deleted
     */
    private fun becomeGuest() {
        // couldn't log in, become guest
        Auth.updateCurrentUser(Auth.guest)
        refresh()
        drawer.openDrawer(GravityCompat.START)
    }

    /**
     * Refresh sliding tabs, sidebar (usually after auth/settings change)
     */
    fun refresh() {

        // load current blog and favorites
        launch(UI) {

            if (Auth.profile != null && Auth.blog == null) {
                // we have loaded profile, try to load our blog
                try {
                    async { Network.populateBlog() }.await()
                } catch (ex: Exception) {
                    Network.reportErrors(this@MainActivity, ex)
                }
            }

            sidebar.updateSidebar()
            refreshTabs()

            // the whole application may be started because of link click
            handleIntent(intent)
            intent = null // reset intent of the activity
        }
    }

    private fun refreshTabs() {
        tabAdapter.apply {
            account = Auth.user
            blog = Auth.blog ?: Auth.emptyBlogMarker
        }
        tabAdapter.notifyDataSetChanged()
    }

    /**
     * Add entry handler. Shows header view for adding diary entry.
     * @see Entry
     */
    @OnClick(R.id.floating_button)
    fun addEntry() {
        // use `instantiate` here because getItem returns new item with each invocation
        // we know that fragment is already present so it will return cached one
        val currFragment = tabAdapter.instantiateItem(pager, pager.currentItem) as EntryListFragment
        if (currFragment.refresher.isRefreshing) {
            // diary is not loaded yet
            Toast.makeText(this, R.string.still_loading, Toast.LENGTH_SHORT).show()
            return
        }

        currFragment.addCreateNewEntryForm()
    }

    inner class TabAdapter: FragmentStatePagerAdapter(supportFragmentManager) {

        var account: Account? = null
        var blog: Blog? = null

        override fun getCount(): Int {
            if (account == null) {
                // not initialized yet
                return 0
            }

            if (account === Auth.guest) {
                // guest can't see anything without logging in yet
                return 0
            }

            return 4 // favorites, world and own blog
        }

        override fun getItemPosition(fragment: Any): Int {
            val entryList = fragment as EntryListFragment
            if (entryList.blog != blog) {
                // needed to kill old fragment that is shown when auth is switched
                entryList.userVisibleHint = false
                return POSITION_NONE
            }

            return super.getItemPosition(fragment)
        }

        override fun getItem(position: Int) = when(position) {
            MY_DIARY_TAB -> EntryListFragment().apply { blog = this@TabAdapter.blog }
            FAV_TAB  -> EntryListFragment().apply { blog = Auth.favoritesMarker }
            WORLD_TAB -> EntryListFragment().apply { blog = Auth.worldMarker }
            NOTIFICATIONS_TAB -> NotificationListFragment()
            else -> EntryListFragment().apply { blog = null }
        }

        override fun getPageTitle(position: Int): CharSequence? = when (position) {
            MY_DIARY_TAB -> getString(R.string.my_diary)
            FAV_TAB -> getString(R.string.favorite)
            WORLD_TAB -> getString(R.string.world)
            NOTIFICATIONS_TAB -> getString(R.string.notifications)
            else -> ""
        }
    }

    /**
     * Profile selector adapter. We can't use standard one because we need a way to delete profile or add another.
     */
    inner class ProfileListAdapter(private val profiles: MutableList<OwnProfile>) : RecyclerView.Adapter<ProfileListAdapter.ProfileViewHolder>() {

        /**
         * Dismiss this if item is selected
         */
        lateinit var toDismiss: MaterialDialog

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val inflater = LayoutInflater.from(this@MainActivity)
            val v = inflater.inflate(R.layout.activity_main_profile_selection_row, parent, false)
            return ProfileViewHolder(v)
        }

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) = holder.setup(position)

        override fun getItemCount() = profiles.size

        inner class ProfileViewHolder(v: View): RecyclerView.ViewHolder(v) {

            @BindView(R.id.profile_name)
            lateinit var profileName: TextView

            @BindView(R.id.profile_remove)
            lateinit var profileRemove: ImageView

            init {
                ButterKnife.bind(this, v)
            }

            fun setup(pos: Int) {
                val prof = profiles[pos]
                profileName.text = prof.nickname

                profileRemove.setOnClickListener {
                    MaterialDialog.Builder(this@MainActivity)
                            .title(R.string.confirm_action)
                            .content(R.string.confirm_profile_deletion)
                            .negativeText(android.R.string.no)
                            .positiveText(R.string.confirm)
                            .onPositive {_, _ ->
                                deleteProfile(prof)
                                profiles.remove(prof)
                                this@ProfileListAdapter.notifyItemRemoved(pos)
                            }.show()
                }

                itemView.setOnClickListener {
                    selectProfile(prof)
                    toDismiss.dismiss()
                }
            }
        }
    }
}
