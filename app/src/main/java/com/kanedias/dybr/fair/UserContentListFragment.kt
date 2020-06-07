package com.kanedias.dybr.fair

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ftinc.scoop.StyleLevel
import com.kanedias.dybr.fair.dto.Authored
import kotlinx.coroutines.*
import moe.banana.jsonapi2.Resource

/**
 * Base fragment for any page with refreshable [RecyclerView] which
 * can load more items when scrolled to the bottom.
 *
 * Subclasses should provide data retrieval steps, view references
 * and [LoadMoreAdapter] extensions for data representation.
 *
 * Loading more items, refreshing and last page detection is then
 * handled by this fragment.
 *
 * @author Kanedias
 *
 * Created on 12.01.19
 */
abstract class UserContentListFragment : Fragment() {

    abstract fun getRibbonView(): RecyclerView
    abstract fun getRefresher(): SwipeRefreshLayout
    abstract fun getRibbonAdapter(): LoadMoreAdapter
    abstract fun retrieveData(pageNum: Int, starter: Long) : () -> List<Resource>

    protected var pageStarter = System.currentTimeMillis() / 1000
    protected var allLoaded = false

    lateinit var styleLevel: StyleLevel

    /**
     * Detect conditions in which loading of entries should be skipped
     * and handle them.
     *
     * @return true if loading should be skipped, false otherwise
     */
    open fun handleLoadSkip() : Boolean = false

    /**
     * Loads more data into the recycler view
     * @param reset if true, clear current items and current/last page load status
     */
    open fun loadMore(reset: Boolean = false) {
        if (handleLoadSkip()) {
            return
        }

        if (reset) {
            getRibbonView().scrollTo(0, 0)
            getRibbonAdapter().clearItems()

            pageStarter = System.currentTimeMillis() / 1000
            allLoaded = false
        }

        getRefresher().isRefreshing = true
        val nextPage = getRibbonAdapter().items.size / PAGE_SIZE + 1

        lifecycleScope.launch {

            try {
                val success = withContext(Dispatchers.IO) {
                    retrieveData(pageNum = nextPage, starter = pageStarter).invoke()
                }
                onMoreDataLoaded(success)
            } catch (ex: Exception) {
                Network.reportErrors(context, ex)
            }

            getRefresher().isRefreshing = false
        }
    }

    /**
     * Update notification ribbon with newly loaded values.
     * @param loaded document with notifications for active profile and links to pages that was loaded
     */
    open fun onMoreDataLoaded(loaded: List<Resource>) {
        if (loaded.size < PAGE_SIZE) {
            allLoaded = true
        }

        if (loaded.isEmpty()) {
            return
        }

        // exclude items already on this page
        val alreadyLoaded = getRibbonAdapter().items.size % PAGE_SIZE
        val freshItems = loaded.drop(alreadyLoaded)

        getRibbonAdapter().addItems(freshItems)
    }

    /**
     * An adapter that loads more data if recycler view is scrolled to the bottom, e.g. the last item is visible.
     * Also has support for headers and adding/removing/clearing item list.
     */
    abstract inner class LoadMoreAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var items: MutableList<Resource> = ArrayList()
        var headers: MutableList<Resource> = ArrayList()

        init {
            this.setHasStableIds(true)
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            val layoutMgr = getRibbonView().layoutManager as LinearLayoutManager

            getRibbonView().addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val total = layoutMgr.itemCount
                    val lastVisible = layoutMgr.findLastVisibleItemPosition()
                    if (!allLoaded && !getRefresher().isRefreshing && total <= (lastVisible + 1)) {
                        loadMore()
                    }
                }
            })
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        }

        override fun getItemViewType(position: Int): Int {
            val headCnt = headers.size
            val itemCnt = items.size

            return when {
                position < headCnt -> ITEM_HEADER
                position < headCnt + itemCnt -> ITEM_REGULAR
                else -> ITEM_UNKNOWN
            }
        }

        override fun getItemId(position: Int): Long {
            val item = when (getItemViewType(position)) {
                ITEM_HEADER -> headers[position]
                ITEM_REGULAR -> items[position - headers.size]
                else -> null
            }
            return item.hashCode().toLong()
        }

        override fun getItemCount() = headers.size + items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(activity)
            val pbar = inflater.inflate(R.layout.view_load_more, parent, false)
            return object: RecyclerView.ViewHolder(pbar) {}
        }

        open fun removeItem(position: Int) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }

        open fun addItem(item: Resource) {
            val insertionPoint = itemCount
            items.add(item)
            notifyItemInserted(insertionPoint)
        }

        open fun addItems(list: List<Resource>) {
            val insertionPoint = itemCount
            items.addAll(list)
            notifyItemRangeInserted(insertionPoint, list.size)
        }

        open fun clearItems() {
            val itemsCleared = items.size
            items.clear()
            notifyItemRangeRemoved(headers.size, itemsCleared)
        }

        open fun replaceHeader(pos: Int, entity: Authored) {
            headers[pos] = entity
            notifyItemChanged(pos)
        }

    }
}