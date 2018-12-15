package com.kanedias.dybr.fair

import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.graphics.drawable.DrawerArrowDrawable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.ftinc.scoop.Scoop
import com.kanedias.dybr.fair.dto.Comment
import com.kanedias.dybr.fair.dto.Entry
import com.kanedias.dybr.fair.themes.*
import kotlinx.coroutines.*
import moe.banana.jsonapi2.ArrayDocument

/**
 * Fragment which displays selected entry and its comments below.
 *
 * @author Kanedias
 *
 * Created on 01.04.18
 */
class CommentListFragment : Fragment() {

    @BindView(R.id.comments_toolbar)
    lateinit var toolbar: Toolbar

    @BindView(R.id.comments_list_area)
    lateinit var refresher: SwipeRefreshLayout

    @BindView(R.id.comments_ribbon)
    lateinit var commentRibbon: RecyclerView

    @BindView(R.id.add_comment_button)
    lateinit var addCommentButton: FloatingActionButton

    var entry: Entry? = null

    private val commentAdapter = CommentListAdapter()
    private var nextPage = 1
    private var lastPage = false

    private lateinit var activity: MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        savedInstanceState?.get("entry")?.let { entry = it as Entry }

        val view = inflater.inflate(R.layout.fragment_comment_list, container, false)
        activity = context as MainActivity

        ButterKnife.bind(this, view)
        setupUI(view)
        refreshComments()
        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("entry", entry)
    }

    private fun setupUI(view: View) {
        toolbar.title = entry?.title
        toolbar.navigationIcon = DrawerArrowDrawable(activity).apply { progress = 1.0f }
        toolbar.setNavigationOnClickListener { fragmentManager?.popBackStack() }

        refresher.setOnRefreshListener { refreshComments(reset = true) }
        commentRibbon.layoutManager = LinearLayoutManager(activity)
        commentRibbon.adapter = commentAdapter

        setBlogTheme(view)
    }

    private fun setBlogTheme(view: View) {
        // this is a fullscreen fragment, add new style
        Scoop.getInstance().addStyleLevel(view)
        Scoop.getInstance().bind(TOOLBAR, toolbar)
        Scoop.getInstance().bind(TOOLBAR_TEXT, toolbar, ToolbarTextAdapter())
        Scoop.getInstance().bind(TOOLBAR_TEXT, toolbar, ToolbarIconAdapter())
        Scoop.getInstance().bind(ACCENT, addCommentButton, FabColorAdapter())
        Scoop.getInstance().bind(TEXT, addCommentButton, FabIconAdapter())
        Scoop.getInstance().bind(BACKGROUND, commentRibbon)
        Scoop.getInstance().bindStatusBar(activity, STATUS_BAR)

        entry?.profile?.get(entry?.document)?.let { applyTheme(it, activity) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Scoop.getInstance().popStyleLevel(false)
    }

    /**
     * Refresh comments displayed in fragment. Entry is not touched but as recycler view is refreshed
     * its views are reloaded too.
     *
     * @param reset if true, reset page counting and start from page one
     */
    fun refreshComments(reset: Boolean = false) {
        if (entry == null) { // we don't have an entry, just show empty list
            refresher.isRefreshing = false
            return
        }

        if (reset) {
            commentRibbon.smoothScrollToPosition(0)
            nextPage = 1
            lastPage = false
        }

        GlobalScope.launch(Dispatchers.Main) {
            refresher.isRefreshing = true

            try {
                val entryDemand = withContext(Dispatchers.IO) { Network.loadEntry(entry!!.id) }
                val commentsDemand = withContext(Dispatchers.IO) { Network.loadComments(entry!!, pageNum = nextPage) }
                entry!!.apply { meta = entryDemand.meta } // refresh comment num and participants
                updateRibbonPage(commentsDemand, reset)

                // mark related notifications read
                val markedRead = withContext(Dispatchers.IO) { Network.markNotificationsReadFor(entry!!) }
                if (markedRead) {
                    // we changed notifications, update fragment with them if present
                    val notifPredicate = { it: Fragment -> it is NotificationListFragment }
                    val notifFragment = fragmentManager?.fragments?.find(notifPredicate) as NotificationListFragment?
                    notifFragment?.refreshNotifications(true)
                }
            } catch (ex: Exception) {
                Network.reportErrors(activity, ex)
            }

            refresher.isRefreshing = false
        }
    }

    /**
     * Update recycler view with loaded comments.
     * If reset is called, strip comments and start anew.
     *
     * @param reset if true, reset page counting and start from page one
     */
    private fun updateRibbonPage(loaded: ArrayDocument<Comment>, reset: Boolean) {
        if (reset) {
            commentAdapter.apply {
                comments.clear()
                notifyDataSetChanged()
            }
        }

        if (loaded.isEmpty()) {
            lastPage = true
            commentAdapter.notifyDataSetChanged()
            return
        }

        nextPage += 1
        commentAdapter.apply {
            val oldSize = comments.size
            comments.addAll(loaded)
            comments.included.addAll(loaded.included)
            notifyItemRangeInserted(oldSize, loaded.size)
        }
    }

    @OnClick(R.id.add_comment_button)
    fun addComment() {
        val commentAdd = CreateNewCommentFragment().apply {
            this.entry = this@CommentListFragment.entry!! // at this point we know we have the entry
        }

        fragmentManager!!.beginTransaction()
                .addToBackStack("Showing comment add fragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .add(R.id.main_drawer_layout, commentAdd)
                .commit()
    }

    /**
     * Adapter for comments list. Top item is an entry being viewed.
     * All views below represent comments to this entry.
     *
     * Scrolling to the bottom calls polling of next page or stops in case it's the last one
     */
    inner class CommentListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val comments = ArrayDocument<Comment>()

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (getItemViewType(position)) {
                ITEM_HEADER -> {
                    (holder as EntryViewHolder).apply {
                        setup(entry!!, false)
                        itemView.isClickable = false
                    }
                }
                ITEM_REGULAR -> {
                    val comment = comments[position - 1]
                    val profile = comment.profile.get(comments) // it's included
                    (holder as CommentViewHolder).setup(comment, profile)
                }
                ITEM_LOAD_MORE -> refreshComments()
                // Nothing needed for ITEM_LAST_PAGE
            }
        }

        override fun getItemViewType(position: Int) = when {
            position == 0 -> ITEM_HEADER
            position < comments.size + 1 -> ITEM_REGULAR
            lastPage -> ITEM_LAST_PAGE
            else -> ITEM_LOAD_MORE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(activity)
            return when (viewType) {
                ITEM_HEADER -> {
                    val view = inflater.inflate(R.layout.fragment_entry_list_item, parent, false)
                    EntryViewHolder(view, parent as View, allowSelection = true)
                }
                ITEM_REGULAR -> {
                    val view = inflater.inflate(R.layout.fragment_comment_list_item, parent, false)
                    CommentViewHolder(entry!!, view, parent as View)
                }
                else -> object: RecyclerView.ViewHolder(View(inflater.context)) {}
            }
        }

        override fun getItemCount(): Int {
            if (entry == null) {
                // nothing to show, at all
                return 0
            }

            if (comments.isEmpty()) {
                // seems like we didn't yet load anything in our view, it's probably loading
                // for the first time. Don't show "load more" and entry, let's wait while anything shows up.
                return 1 // only show entry
            }

            return 1 +comments.size + 1 // entry, comments and "load more" item
        }
    }

}