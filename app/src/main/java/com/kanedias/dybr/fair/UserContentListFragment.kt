package com.kanedias.dybr.fair

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.banana.jsonapi2.ArrayDocument
import moe.banana.jsonapi2.Resource

/**
 * @author Kanedias
 *
 * Created on 12.01.19
 */
abstract class UserContentListFragment : Fragment() {

    abstract fun getRibbonView(): RecyclerView
    abstract fun getRefresher(): SwipeRefreshLayout
    abstract fun getRibbonAdapter(): UserContentListFragment.LoadMoreAdapter
    abstract fun retrieveData(pageNum: Int) : () -> ArrayDocument<out Resource>

    protected var allLoaded = false
    protected var currentPage = 1

    override fun onStart() {
        super.onStart()

        loadMore()
    }

    /**
     * Loads more data into the recycler view
     * @param reset if true, clear current items and current/last page load status
     */
    open fun loadMore(reset: Boolean = false) {
        if (reset) {
            getRibbonView().scrollTo(0, 0)
            getRibbonAdapter().clearItems()

            allLoaded = false
            currentPage = 1
        }

        getRefresher().isRefreshing = true

        GlobalScope.launch(Dispatchers.Main) {

            try {
                val success = withContext(Dispatchers.IO) { retrieveData(pageNum = currentPage).invoke() }
                onMoreDataLoaded(success)
            } catch (ex: Exception) {
                Network.reportErrors(context!!, ex)
            }

            getRefresher().isRefreshing = false
        }
    }

    /**
     * Update notification ribbon with newly loaded values.
     * @param loaded document with notifications for active profile and links to pages that was loaded
     */
    open fun onMoreDataLoaded(loaded: ArrayDocument<out Resource>) {
        val currentCnt = getRibbonAdapter().itemCount

        if (loaded.isEmpty()) {
            allLoaded = true
            return
        }

        currentPage += 1
        getRibbonAdapter().apply {
            items.addAll(loaded)
            notifyItemRangeInserted(currentCnt, loaded.size)
        }
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
            val type = getItemViewType(position)

            val item = when (type) {
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

        fun removeItem(position: Int) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }

        fun addItem(item: Resource) {
            items.add(item)
            notifyItemInserted(headers.size + items.size)
        }

        fun clearItems() {
            val itemsCleared = items.size
            items.clear()
            notifyItemRangeRemoved(headers.size, itemsCleared)
        }

    }
}