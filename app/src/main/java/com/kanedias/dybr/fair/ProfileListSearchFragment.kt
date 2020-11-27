package com.kanedias.dybr.fair

import android.os.Bundle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import butterknife.BindView
import butterknife.ButterKnife
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.ftinc.scoop.Scoop
import com.ftinc.scoop.adapters.DefaultColorAdapter
import com.ftinc.scoop.adapters.ImageViewColorAdapter
import com.ftinc.scoop.adapters.TextViewColorAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.kanedias.dybr.fair.databinding.FragmentProfileListItemBinding
import com.kanedias.dybr.fair.dto.*
import com.kanedias.dybr.fair.misc.idMatches
import com.kanedias.dybr.fair.misc.showFullscreenFragment
import com.kanedias.dybr.fair.service.Network
import com.kanedias.dybr.fair.themes.*
import com.kanedias.dybr.fair.ui.showToastAtView
import kotlinx.coroutines.launch
import moe.banana.jsonapi2.ArrayDocument
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment which displays profile lists. Needed mostly for showing search results
 *
 * @author Kanedias
 *
 * Created on 2020-04-20
 */
open class ProfileListSearchFragment : UserContentListFragment() {

    @BindView(R.id.profile_list_toolbar)
    lateinit var toolbar: MaterialToolbar

    @BindView(R.id.profile_list_area)
    lateinit var ribbonRefresher: SwipeRefreshLayout

    @BindView(R.id.profile_ribbon)
    lateinit var profileRibbon: RecyclerView

    override fun getRibbonView() = profileRibbon
    override fun getRefresher() = ribbonRefresher
    override fun getRibbonAdapter() = profileAdapter
    override fun retrieveData(pageNum: Int, starter: Long): () -> ArrayDocument<OwnProfile> = {
        Network.searchProfiles(filters = filters, pageNum = pageNum)
    }

    /**
     * Filters for search query
     */
    lateinit var filters: Map<String, String>

    private lateinit var profileAdapter: LoadMoreAdapter

    private lateinit var activity: MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        filters = requireArguments().getSerializable("filters") as Map<String, String>

        val view = inflater.inflate(R.layout.fragment_profile_list_fullscreen, container, false)
        activity = context as MainActivity
        profileAdapter = ProfileListAdapter()

        ButterKnife.bind(this, view)
        setupUI()
        setupTheming()
        loadMore()

        return view
    }

    private fun setupUI() {
        toolbar.title = requireArguments().getString("title", getString(R.string.search))
        toolbar.subtitle = requireArguments().getString("subtitle", "")

        toolbar.navigationIcon = DrawerArrowDrawable(activity).apply { progress = 1.0f }
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        ribbonRefresher.setOnRefreshListener { loadMore(reset = true) }
        profileRibbon.adapter = profileAdapter
    }

    private fun setupTheming() {
        // this is a fullscreen fragment, add new style
        styleLevel = Scoop.getInstance().addStyleLevel()
        lifecycle.addObserver(styleLevel)

        styleLevel.bind(TOOLBAR, toolbar, DefaultColorAdapter())
        styleLevel.bind(TOOLBAR_TEXT, toolbar, ToolbarTextAdapter())
        styleLevel.bind(TOOLBAR_TEXT, toolbar, ToolbarIconsAdapter())

        styleLevel.bind(BACKGROUND, profileRibbon, NoRewriteBgPicAdapter())
        styleLevel.bindStatusBar(activity, STATUS_BAR)

        val backgrounds = mapOf<View, Int>(profileRibbon to BACKGROUND/*, toolbar to TOOLBAR*/)
        Auth.profile?.let { applyTheme(activity, it, styleLevel, backgrounds) }
    }

    /**
     * Main adapter of this fragment's recycler view. Shows profiles and handles
     * refreshing and page loading.
     */
    inner class ProfileListAdapter : UserContentListFragment.LoadMoreAdapter() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.fragment_profile_list_item, parent, false)
            return ProfileViewHolder(view, this@ProfileListSearchFragment)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val profile = items[position - headers.size] as OwnProfile
            (holder as ProfileViewHolder).setup(profile)
        }
    }

    /**
     * View holder for showing profiles as list.
     *
     * @param iv inflated view to be used by this holder
     * @param parentFragment fragment in which this holder is shown
     *
     * @author Kanedias
     */
    class ProfileViewHolder(iv: View, private val parentFragment: UserContentListFragment) : RecyclerView.ViewHolder(iv) {

        private val profileItem = FragmentProfileListItemBinding.bind(iv)

        init {
            setupTheming()
        }

        fun setupTheming() {
            parentFragment.styleLevel.bind(TEXT_BLOCK, itemView, CardViewColorAdapter())
            parentFragment.styleLevel.bind(TEXT, profileItem.profileName, TextViewColorAdapter())
            parentFragment.styleLevel.bind(TEXT, profileItem.profileName, TextViewDrawableAdapter())
            parentFragment.styleLevel.bind(TEXT, profileItem.profileRegistrationDateLabel, TextViewColorAdapter())
            parentFragment.styleLevel.bind(TEXT, profileItem.profileRegistrationDate, TextViewColorAdapter())
            parentFragment.styleLevel.bind(TEXT_LINKS, profileItem.communityJoinButton, ImageViewColorAdapter())
        }

        private fun showProfile(profile: OwnProfile) {
            val profShow = ProfileFragment().apply { this.profile = profile }
            profShow.show(parentFragment.parentFragmentManager, "Showing user profile fragment")
        }

        private fun showBlog(profile: OwnProfile) {
            val browseFragment = EntryListFragmentFull().apply { this.profile = profile }
            parentFragment.activity?.showFullscreenFragment(browseFragment)
        }

        fun setup(profile: OwnProfile) {
            profileItem.profileAvatar.setOnClickListener { showProfile(profile) }
            itemView.setOnClickListener { showBlog(profile) }

            profileItem.profileName.text = profile.nickname
            when (profile.isCommunity) {
                true -> profileItem.profileName.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.community, 0, 0, 0)
                false -> profileItem.profileName.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            }
            profileItem.profileRegistrationDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(profile.createdAt)

            // set avatar
            val avatar = Network.resolve(profile.settings.avatar) ?: Network.defaultAvatar()
            Glide.with(profileItem.profileAvatar)
                    .load(avatar.toString())
                    .apply(RequestOptions().centerInside().circleCrop())
                    .into(profileItem.profileAvatar)

            // setup community join button
            if (Auth.profile != null && profile.isCommunity) {
                // check if logged in profile has this community in "my-communities"
                profileItem.communityJoinButton.visibility = View.VISIBLE
                val isParticipant = Auth.profile!!.communities.any { it.idMatches(profile) }
                when (isParticipant) {
                    true -> profileItem.communityJoinButton.setImageResource(R.drawable.community_leave)
                    false -> profileItem.communityJoinButton.setImageResource(R.drawable.community_join)
                }
                profileItem.communityJoinButton.setOnClickListener { handleCommunityJoin(profile) }
            }
        }

        private fun handleCommunityJoin(communityProf: OwnProfile) {
            val shouldJoin = Auth.profile!!.communities.any { it.idMatches(communityProf) }.not()

            parentFragment.lifecycleScope.launch {

                profileItem.communityJoinButton.setImageResource(R.drawable.wait)
                when(shouldJoin) {
                    true -> Network.perform(
                            networkAction = { Network.communityJoin(communityProf) },
                            uiAction = { answer ->
                                when (answer.state) {
                                    "approved" -> {
                                        // this is an auto-join community
                                        profileItem.communityJoinButton.setImageResource(R.drawable.community_joined)
                                        Auth.profile!!.communities.add(communityProf)
                                        showToastAtView(profileItem.communityJoinButton, R.string.joined_community)
                                    }
                                    "pending" -> {
                                        // this is a pre-moderated community
                                        showToastAtView(profileItem.communityJoinButton, R.string.community_join_request_sent)
                                    }
                                }

                            },
                            errorAction = { profileItem.communityJoinButton.setImageResource(R.drawable.community_join) }
                    )
                    false -> Network.perform(
                            networkAction = { Network.communityLeave(communityProf) },
                            uiAction = {
                                profileItem.communityJoinButton.setImageResource(R.drawable.community_left)
                                Auth.profile!!.communities.remove(communityProf)
                                showToastAtView(profileItem.communityJoinButton, R.string.left_community)
                            },
                            errorAction = { profileItem.communityJoinButton.setImageResource(R.drawable.community_leave) }
                    )
                }

            }
        }
    }


}