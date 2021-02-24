package com.kanedias.dybr.fair

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.ftinc.scoop.Scoop
import com.kanedias.dybr.fair.databinding.FragmentNotificationListBinding
import com.kanedias.dybr.fair.dto.*
import com.kanedias.dybr.fair.scheduling.SyncNotificationsWorker
import com.kanedias.dybr.fair.service.Network
import com.kanedias.dybr.fair.themes.*
import kotlinx.coroutines.*
import moe.banana.jsonapi2.ArrayDocument

/**
 * Fragment which displays list of notifications for current profile.
 *
 * @author Kanedias
 *
 * Created on 14.10.2018
 */
open class NotificationListFragment: UserContentListFragment() {

    override fun getRibbonView() = binding.notifRibbon
    override fun getRefresher() = binding.notifRefresher
    override fun getRibbonAdapter() = notifAdapter
    override fun retrieveData(pageNum: Int, starter: Long): () -> ArrayDocument<Notification> = {
        Network.loadNotifications(pageNum = pageNum)
    }

    private val notifAdapter = NotificationListAdapter()

    lateinit var binding: FragmentNotificationListBinding
    lateinit var activity: MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)

        binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        activity = context as MainActivity

        setupUI()
        setupTheming()
        loadMore()

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.notifications_fragment_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        styleLevel.bind(TOOLBAR_TEXT, activity.binding.toolbar, ToolbarMenuIconsAdapter())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_notifications_mark_all_read -> markAllRead()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun markAllRead() {
        val markRoutine = {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { Network.markAllNotificationsRead() }
                    loadMore(true)
                } catch (ex: Exception) {
                    Network.reportErrors(context, ex)
                }
            }
        }

        MaterialDialog(activity)
                .title(R.string.confirm_action)
                .message(R.string.mark_all_notifications_read)
                .negativeButton(android.R.string.cancel)
                .positiveButton(android.R.string.yes, click = { markRoutine() })
                .showThemed(styleLevel)
    }

    open fun setupUI() {
        binding.notifRefresher.setOnRefreshListener { loadMore(true) }
        binding.notifRibbon.adapter = notifAdapter
    }

    override fun onResume() {
        super.onResume()

        // hide all notifications if this fragment is resumed
        SyncNotificationsWorker.hideNotifications(requireContext())
    }

    open fun setupTheming() {
        styleLevel = Scoop.getInstance().addStyleLevel()

        styleLevel.bind(BACKGROUND, binding.notifRibbon, NoRewriteBgPicAdapter())
        styleLevel.bindBgDrawable(BACKGROUND, binding.notifRibbon)

        Auth.profile?.let { applyTheme(activity, it, styleLevel) }
    }

    /**
     * Loads the next page in notifications listing. If no pages were loaded before, loads first
     * @param reset reset page counter to first
     */
    override fun loadMore(reset: Boolean) {
        if (Auth.profile == null) { // we don't have a profile, just show empty list
            binding.notifRefresher.isRefreshing = false
            return
        }

        super.loadMore(reset)
    }

    /**
     * Main adapter of this fragment's recycler view. Shows notifications and handles
     * refreshing and page loading.
     */
    inner class NotificationListAdapter : UserContentListFragment.LoadMoreAdapter() {

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (getItemViewType(position)) {
                ITEM_REGULAR -> {
                    val entryHolder = holder as NotificationViewHolder
                    entryHolder.setup(items[position] as Notification)
                }
                else -> super.onBindViewHolder(holder, position)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(activity)
            return when (viewType) {
                ITEM_REGULAR -> {
                    val view = inflater.inflate(R.layout.fragment_notification_list_item, parent, false)
                    NotificationViewHolder(view, this@NotificationListFragment)
                }
                else -> super.onCreateViewHolder(parent, viewType)
            }
        }
    }

}