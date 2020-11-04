package com.kanedias.dybr.fair

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import butterknife.BindView
import butterknife.BindViews
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.kanedias.dybr.fair.markdown.handleMarkdown
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.ftinc.scoop.adapters.DefaultColorAdapter
import com.ftinc.scoop.adapters.ImageViewColorAdapter
import com.ftinc.scoop.adapters.TextViewColorAdapter
import com.kanedias.dybr.fair.dto.*
import com.kanedias.dybr.fair.misc.idMatches
import com.kanedias.dybr.fair.misc.onClickSingleOnly
import com.kanedias.dybr.fair.misc.showFullscreenFragment
import com.kanedias.dybr.fair.service.Network
import com.kanedias.dybr.fair.themes.*
import com.kanedias.dybr.fair.ui.openUrlExternally
import com.kanedias.dybr.fair.ui.showToastAtView
import kotlinx.coroutines.*

/**
 * View holder for showing regular entries in blog view.
 *
 * @param iv inflated view to be used by this holder
 * @param allowSelection whether text in this view can be selected and copied
 * @see EntryListFragment.entryRibbon
 * @author Kanedias
 */
class EntryViewHolder(iv: View, parentFragment: UserContentListFragment, private val allowSelection: Boolean = false)
    : UserContentViewHolder<Entry>(iv, parentFragment) {

    @BindView(R.id.top_indicators_row)
    lateinit var topIndicatorsArea: RelativeLayout

    @BindView(R.id.community_row)
    lateinit var communityProfileArea: RelativeLayout

    @BindView(R.id.entry_community_avatar)
    lateinit var communityAvatarView: ImageView

    @BindView(R.id.entry_community_profile)
    lateinit var communityView: TextView

    @BindView(R.id.entry_community_profile_subtext)
    lateinit var communitySubtextView: TextView

    @BindView(R.id.profile_row)
    lateinit var profileRowArea: RelativeLayout

    @BindView(R.id.entry_avatar)
    lateinit var avatarView: ImageView

    @BindView(R.id.entry_author)
    lateinit var authorView: TextView

    @BindView(R.id.entry_author_subtext)
    lateinit var authorSubtextView: TextView

    @BindView(R.id.entry_date)
    lateinit var dateView: TextView

    @BindView(R.id.entry_title)
    lateinit var titleView: TextView

    @BindView(R.id.entry_message)
    lateinit var bodyView: TextView

    @BindView(R.id.entry_tags)
    lateinit var tagsView: TextView

    @BindView(R.id.entry_meta_divider)
    lateinit var metaDivider: View

    @BindView(R.id.entry_draft_state)
    lateinit var draftStateView: TextView

    @BindViews(
            R.id.entry_bookmark, R.id.entry_watch, R.id.entry_edit,
            R.id.entry_delete, R.id.entry_more_options, R.id.entry_add_reaction
    )
    lateinit var buttons: List<@JvmSuppressWildcards ImageView>

    @BindViews(R.id.entry_participants_indicator, R.id.entry_comments_indicator)
    lateinit var indicators: List<@JvmSuppressWildcards ImageView>

    @BindView(R.id.entry_comments_text)
    lateinit var comments: TextView

    @BindView(R.id.entry_participants_text)
    lateinit var participants: TextView

    @BindView(R.id.entry_permissions)
    lateinit var permissionIcon: ImageView

    @BindView(R.id.entry_pinned)
    lateinit var pinIcon: ImageView

    @BindView(R.id.entry_reactions)
    lateinit var reactionArea: LinearLayout

    /**
     * Entry that this holder represents
     */
    private lateinit var entry: Entry

    /**
     * Optional metadata associated with current entry
     */
    private var metadata: EntryMeta? = null
    private var reactions: MutableList<Reaction> = mutableListOf()

    /**
     * Blog this entry belongs to
     */
    private lateinit var profile: OwnProfile
    private var community: OwnProfile? = null

    override fun getCreationDateView() = dateView
    override fun getProfileAvatarView() = avatarView
    override fun getAuthorNameView() = authorView
    override fun getContentView() = bodyView

    /**
     * Listener to show comments of this entry
     */
    private val commentShow = View.OnClickListener {
        val activity = it.context as AppCompatActivity
        val commentsPage = CommentListFragment().apply {
            arguments = Bundle().apply { putSerializable(CommentListFragment.ENTRY_ARG, this@EntryViewHolder.entry) }
        }
        activity.showFullscreenFragment(commentsPage)
    }

    init {
        ButterKnife.bind(this, iv)
        setupTheming()

        tagsView.movementMethod = LinkMovementMethod()
        iv.setOnClickListener(commentShow)
    }

    private fun setupTheming() {
        val styleLevel = parentFragment.styleLevel

        styleLevel.bind(TEXT_BLOCK, itemView, CardViewColorAdapter())
        styleLevel.bind(TEXT_HEADERS, titleView, TextViewColorAdapter())
        styleLevel.bind(TEXT, dateView, TextViewColorAdapter())
        styleLevel.bind(TEXT, authorView, TextViewColorAdapter())
        styleLevel.bind(TEXT, authorSubtextView, TextViewColorAdapter())
        styleLevel.bind(TEXT, communityView, TextViewColorAdapter())
        styleLevel.bind(TEXT, communityView, TextViewDrawableAdapter())
        styleLevel.bind(TEXT, communitySubtextView, TextViewColorAdapter())
        styleLevel.bind(TEXT, bodyView, TextViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, bodyView, TextViewLinksAdapter())
        styleLevel.bind(TEXT_LINKS, tagsView, TextViewLinksAdapter())
        styleLevel.bind(TEXT_LINKS, permissionIcon, ImageViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, pinIcon, ImageViewColorAdapter())
        styleLevel.bind(DIVIDER, metaDivider, DefaultColorAdapter())

        (buttons + indicators).forEach { styleLevel.bind(TEXT_LINKS, it, ImageViewColorAdapter()) }
        listOf(participants, comments).forEach { styleLevel.bind(TEXT_LINKS, it, TextViewColorAdapter()) }
    }

    @OnClick(R.id.entry_edit)
    fun editEntry() {
        val activity = itemView.context as AppCompatActivity
        val entryEdit = CreateNewEntryFragment().apply {
            arguments = Bundle().apply {
                // edit entry
                putBoolean(CreateNewEntryFragment.EDIT_MODE, true)
                putString(CreateNewEntryFragment.EDIT_ENTRY_ID, entry.id)
                putString(CreateNewEntryFragment.EDIT_ENTRY_STATE, entry.state)
                putString(CreateNewEntryFragment.EDIT_ENTRY_TITLE, entry.title)
                putSerializable(CreateNewEntryFragment.EDIT_ENTRY_SETTINGS, entry.settings)
                putStringArray(CreateNewEntryFragment.EDIT_ENTRY_TAGS, entry.tags.toTypedArray())
                putString(CreateNewEntryFragment.EDIT_ENTRY_CONTENT_HTML, entry.content)

                // parent profile
                putString(CreateNewEntryFragment.PARENT_BLOG_PROFILE_ID, profile.id)
                putSerializable(CreateNewEntryFragment.PARENT_BLOG_PROFILE_SETTINGS, profile.settings)
            }
        }

        activity.showFullscreenFragment(entryEdit)
    }

    @OnClick(R.id.entry_watch)
    fun subscribeToEntry(subButton: ImageView) {
        val subscribe = !(metadata?.subscribed ?: false)

        val toastText = when (subscribe) {
            true -> R.string.subscribed_to_entry
            false -> R.string.unsubscribed_from_entry
        }

        parentFragment.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { Network.updateSubscription(entry, subscribe) }
                showToastAtView(subButton, itemView.context.getString(toastText))

                metadata?.subscribed = subscribe
                when(subscribe) {
                    true -> subButton.setImageResource(R.drawable.watch_added)
                    false -> subButton.setImageResource(R.drawable.watch_removed)
                }
            } catch (ex: Exception) {
                Network.reportErrors(itemView.context, ex)
            }
        }
    }

    @OnClick(R.id.entry_bookmark)
    fun bookmarkEntry(button: ImageView) {
        val bookmark = !(metadata?.bookmark ?: false)

        val toastText = when (bookmark) {
            true -> R.string.entry_bookmarked
            false -> R.string.entry_removed_from_bookmarks
        }

        parentFragment.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { Network.updateBookmark(entry, bookmark) }
                showToastAtView(button, itemView.context.getString(toastText))

                metadata?.bookmark = bookmark
                setupButtons()
            } catch (ex: Exception) {
                Network.reportErrors(itemView.context, ex)
            }
        }
    }

    @OnClick(R.id.entry_add_reaction)
    fun openReactionMenu(button: ImageView) {
        parentFragment.lifecycleScope.launch {
            try {
                val reactionSets = withContext(Dispatchers.IO) { Network.loadReactionSets() }
                if (!reactionSets.isNullOrEmpty()) {
                    showReactionMenu(button, reactionSets.first())
                }
            } catch (ex: Exception) {
                Network.reportErrors(itemView.context, ex)
            }
        }
    }

    private fun showReactionMenu(view: View, reactionSet: ReactionSet) {
        val reactionTypes = reactionSet.reactionTypes?.get(reactionSet.document).orEmpty()

        val emojiTable = View.inflate(view.context, R.layout.view_emoji_panel, null) as GridLayout
        val pw = PopupWindow().apply {
            height = WindowManager.LayoutParams.WRAP_CONTENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            contentView = emojiTable
            isOutsideTouchable = true
        }

        parentFragment.styleLevel.bind(TEXT_BLOCK, pw.contentView, DefaultColorAdapter())

        for (type in reactionTypes.sortedBy { it.id }) {
            emojiTable.addView(TextView(view.context).apply {
                text = type.emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setOnClickListener {
                    parentFragment.lifecycleScope.launch { toggleReaction(view, type) }
                    pw.dismiss()
                }
            })
        }
        pw.showAsDropDown(view, 0, 0, Gravity.TOP)
    }

    @OnClick(R.id.entry_delete)
    fun deleteEntry() {
        val activity = itemView.context as AppCompatActivity

        // delete callback
        val delete = {
            parentFragment.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { Network.deleteEntry(entry) }
                    Toast.makeText(activity, R.string.entry_deleted, Toast.LENGTH_SHORT).show()
                    activity.supportFragmentManager.popBackStack()

                    parentFragment.loadMore(reset = true)
                } catch (ex: Exception) {
                    Network.reportErrors(itemView.context, ex)
                }
            }
        }

        // show confirmation dialog
        MaterialDialog(itemView.context)
                .title(R.string.confirm_action)
                .message(R.string.are_you_sure)
                .negativeButton(android.R.string.no)
                .positiveButton(android.R.string.yes, click = { delete() })
                .showThemed(parentFragment.styleLevel)
    }

    @OnClick(R.id.entry_more_options)
    fun showOverflowMenu() {
        val ctx = itemView.context
        val items = mutableListOf(
                ctx.getString(R.string.open_in_browser),
                ctx.getString(R.string.share)
        )

        if (parentFragment is EntryListFragment
                && Auth.user != Auth.guest
                && parentFragment.profile === Auth.worldMarker) {
            // show hide-from-feed option
            items.add(ctx.getString(R.string.hide_author_from_feed))
        }

        MaterialDialog(itemView.context)
                .title(R.string.entry_menu)
                .listItems(items = items, selection = { _, index, _ ->
                    when (index) {
                        0 -> showInWebView()
                        1 -> sharePost()
                        2 -> hideFromFeed()
                    }
                }).show()
    }

    private fun hideFromFeed() {
        // hide callback
        val hide = { reason: String ->
            val activity = itemView.context as AppCompatActivity

            val listItem = ActionListRequest().apply {
                kind = "profile"
                action = "hide"
                scope = "feed"
                name = reason
                profiles.add(profile)
            }

            parentFragment.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { Network.addToActionList(listItem) }
                    Toast.makeText(activity, R.string.author_hidden_from_feed, Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Network.reportErrors(itemView.context, ex)
                }
            }
        }

        MaterialDialog(itemView.context)
                .title(R.string.confirm_action)
                .input(hintRes = R.string.reason)
                .negativeButton(android.R.string.cancel)
                .positiveButton(R.string.submit, click = { md -> hide(md.getInputField().text.toString()) })
                .show()
    }

    private fun sharePost() {
        val ctx = itemView.context

        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "https://dybr.ru/blog/${profile.blogSlug}/${entry.id}")
            ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_link_using)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.no_browser_found, Toast.LENGTH_SHORT).show()
        }

    }

    private fun showInWebView() {
        val uri = Uri.Builder()
                .scheme("https").authority("dybr.ru")
                .appendPath("blog").appendPath(profile.blogSlug)
                .appendPath(entry.id)
                .build()

        openUrlExternally(itemView.context, uri)
    }

    /**
     * Show or hide entry editing buttons depending on circumstances
     */
    private fun setupButtons() {
        // setup edit buttons
        val editVisibility = when (isEntryWritable(entry)) {
            true -> View.VISIBLE
            false -> View.GONE
        }
        val editTag = itemView.context.getString(R.string.edit_tag)
        buttons.filter { it.tag == editTag }.forEach { it.visibility = editVisibility }

        // setup subscription button
        val subButton = buttons.first { it.id == R.id.entry_watch }
        when (metadata?.subscribed) {
            true -> subButton.apply { visibility = View.VISIBLE; setImageResource(R.drawable.watch_remove) }
            false -> subButton.apply { visibility = View.VISIBLE; setImageResource(R.drawable.watch_add) }
            null -> subButton.visibility = View.GONE
        }

        // setup bookmark button
        val bookmarkButton = buttons.first { it.id == R.id.entry_bookmark }
        when (metadata?.bookmark) {
            true -> bookmarkButton.apply { visibility = View.VISIBLE; setImageResource(R.drawable.bookmark_filled) }
            false -> bookmarkButton.apply { visibility = View.VISIBLE; setImageResource(R.drawable.bookmark_add) }
            null -> bookmarkButton.visibility = View.GONE
        }

        // setup reactions button
        val reactionButton = buttons.first { it.id == R.id.entry_add_reaction }
        when {
            // disabled globally by current user
            Auth.profile?.settings?.reactions?.disable == true -> reactionButton.visibility = View.GONE
            // disabled in current blog by owner
            profile.settings.reactions.disableInBlog -> reactionButton.visibility = View.GONE
            // not authorized, can't add reactions
            Auth.profile == null -> reactionButton.visibility = View.GONE
            // enabled, show the button
            else -> reactionButton.visibility = View.VISIBLE
        }
    }

    /**
     * Called when this holder should be refreshed based on what it must show now
     */
    override fun setup(entity: Entry) {
        super.setup(entity)

        // bind variables
        this.entry = entity
        this.metadata = Network.bufferToObject(entry.meta, EntryMeta::class.java)
        this.profile = entity.profile.get(entity.document)
        this.community = entity.community?.get(entity.document)
        this.reactions = entity.reactions?.get(entity.document) ?: mutableListOf()

        // setup profile info
        authorSubtextView.text = profile.settings.subtext

        // setup community info
        if (community == null || community!!.idMatches(profile)) {
            // default, no community row
            communityProfileArea.visibility = View.GONE
        } else {
            // community exists
            communityProfileArea.visibility = View.VISIBLE
            communityView.text = community!!.nickname
            communityView.setOnClickListener { showProfile(community!!) }

            val avatar = Network.resolve(community?.settings?.avatar) ?: Network.defaultAvatar()
            Glide.with(communityAvatarView).load(avatar.toString())
                    .apply(RequestOptions().centerInside().circleCrop())
                    .into(communityAvatarView)
            communityAvatarView.setOnClickListener { showProfile(community!!) }

            val communitySubtext = community!!.settings.subtext
            if (communitySubtext.isNullOrEmpty()) {
                communitySubtextView.visibility = View.GONE
            } else {
                communitySubtextView.visibility = View.VISIBLE
                communitySubtextView.text = communitySubtext
            }
        }

        // setup text views from entry data
        titleView.text = entry.title
        titleView.visibility = if (entry.title.isNullOrEmpty()) { View.GONE } else { View.VISIBLE }
        draftStateView.visibility = if (entry.state == "published") { View.GONE } else { View.VISIBLE }

        // setup permission icon
        val accessItem = entry.settings?.permissions?.access?.firstOrNull()
        if (accessItem == null) {
            permissionIcon.visibility = View.GONE
        } else {
            permissionIcon.visibility = View.VISIBLE
            permissionIcon.setOnClickListener { showToastAtView(permissionIcon, accessItem.toDescription(it.context)) }
        }

        // setup pin icon
        val pinned = profile.settings.pinnedEntries.contains(entry.id)
        if (pinned) {
            pinIcon.visibility = View.VISIBLE
            pinIcon.setOnClickListener { showToastAtView(pinIcon, it.context.getString(R.string.pinned_entry)) }
        } else {
            pinIcon.visibility = View.GONE
        }

        // show tags if they are present
        setupTags(entry)

        // setup bottom row of metadata buttons
        metadata?.let { comments.text = it.comments.toString() }
        metadata?.let { participants.text = it.commenters.toString() }

        // setup bottom row of buttons
        setupButtons()

        // setup reaction row
        setupReactions()

        // don't show subscribe button if we can't subscribe
        // guests can't do anything
        if (Auth.profile == null) {
            buttons.first { it.id == R.id.entry_watch }.visibility = View.GONE
            buttons.first { it.id == R.id.entry_bookmark }.visibility = View.GONE
        }

        bodyView.handleMarkdown(entry.content)

        if (allowSelection) {
            // make text selectable
            // XXX: this is MAGIC: see https://stackoverflow.com/a/56224791/1696844
            bodyView.setTextIsSelectable(false)
            bodyView.measure(-1, -1)
            bodyView.setTextIsSelectable(true)
        }
    }

    /**
     * Setup reactions row, to show reactions which were attached to this entry
     */
    private fun setupReactions() {
        reactionArea.removeAllViews()

        val reactionsDisabled = Auth.profile?.settings?.reactions?.disable == true
        val reactionsDisabledInThisBlog = profile.settings.reactions.disableInBlog

        if (reactions.isEmpty() || reactionsDisabled || reactionsDisabledInThisBlog) {
            // no reactions for this entry or reactions disabled
            reactionArea.visibility = View.GONE
            return
        } else {
            reactionArea.visibility = View.VISIBLE
        }

        // there are some reactions, display them
        val styleLevel = parentFragment.styleLevel
        val counts = reactions.groupBy { it.reactionType.get().id }
        val types = reactions.map { it.reactionType.get(it.document) }.associateBy { it.id }
        for (reactionTypeId in counts.keys) {
            // for each reaction type get reaction counts and authors
            val reactionType = types[reactionTypeId] ?: continue
            val postedWithThisType = counts[reactionTypeId] ?: continue
            val includingMe = postedWithThisType.any { Auth.profile?.idMatches(it.author.get()) == true }

            val reactionView = LayoutInflater.from(itemView.context).inflate(R.layout.view_reaction, reactionArea, false)

            reactionView.onClickSingleOnly { toggleReaction(it, reactionType) }
            if (includingMe) {
                reactionView.isSelected = true
            }

            val emojiTxt = reactionView.findViewById<TextView>(R.id.reaction_emoji)
            val emojiCount = reactionView.findViewById<TextView>(R.id.reaction_count)

            emojiTxt.text = reactionType.emoji
            emojiCount.text = postedWithThisType.size.toString()

            styleLevel.bind(TEXT_LINKS, reactionView, BackgroundTintColorAdapter())
            styleLevel.bind(TEXT, emojiCount, TextViewColorAdapter())

            reactionArea.addView(reactionView)
        }
    }

    private suspend fun toggleReaction(view: View, reactionType: ReactionType) {
        // find reaction with this type
        val myReaction = reactions
                .filter { reactionType.idMatches(it.reactionType.get()) }
                .find { Auth.profile?.idMatches(it.author.get()) == true }

        if (myReaction != null) {
            // it's there, delete it
            try {
                withContext(Dispatchers.IO) { Network.deleteReaction(myReaction) }
                showToastAtView(view, view.context.getString(R.string.reaction_deleted))

                reactions.remove(myReaction)
                setupReactions()
            } catch (ex: Exception) {
                Network.reportErrors(view.context, ex)
            }
        } else {
            // add it
            try {
                val newReaction = withContext(Dispatchers.IO) { Network.createReaction(entry, reactionType) }
                showToastAtView(view, view.context.getString(R.string.reaction_added))

                reactions.add(newReaction)
                setupReactions()
            } catch (ex: Exception) {
                Network.reportErrors(view.context, ex)
            }
        }
    }

    /**
     * Show tags below the message, with divider.
     * Make tags below the entry message clickable.
     */
    private fun setupTags(entry: Entry) {
        if (entry.tags.isEmpty()) {
            tagsView.visibility = View.GONE
        } else {
            tagsView.visibility = View.VISIBLE

            val clickTags = SpannableStringBuilder()
            for (tag in entry.tags) {
                clickTags.append("#").append(tag)
                clickTags.setSpan(ClickableTag(tag), clickTags.length - 1 - tag.length, clickTags.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                clickTags.append(" ")
            }
            tagsView.text = clickTags
        }
    }

    /**
     * Clickable tag span. Don't make it look like a URL link but make it clickable nevertheless.
     */
    inner class ClickableTag(private val tagValue: String) : ClickableSpan() {

        override fun onClick(widget: View) {
            val activity = itemView.context as AppCompatActivity
            val filters = hashMapOf("tag" to tagValue)

            val insideBlog = parentFragment is EntryListFragmentFull
            val insideEntry = parentFragment is CommentListFragment
            val tagInOurBlog = parentFragment is EntryListFragment && parentFragment.profile == Auth.profile
            if (insideBlog || insideEntry || tagInOurBlog) {
                // we're browsing one person's blog, show only their entries
                filters["profile_id"] = this@EntryViewHolder.profile.id
            }

            val searchType = parentFragment.getString(R.string.search_by, parentFragment.getString(R.string.dative_case_tag))
            val searchFragment = EntryListSearchFragmentFull().apply {
                arguments = Bundle().apply {
                    putString("title", "#${tagValue}")
                    putString("subtitle", searchType)
                    putSerializable("filters", filters)
                }
            }
            activity.showFullscreenFragment(searchFragment)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = false
        }
    }

}