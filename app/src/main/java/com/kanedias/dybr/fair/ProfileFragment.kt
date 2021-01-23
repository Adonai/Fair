package com.kanedias.dybr.fair

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.internal.button.DialogActionButton
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.ftinc.scoop.Scoop
import com.ftinc.scoop.StyleLevel
import com.ftinc.scoop.adapters.DefaultColorAdapter
import com.ftinc.scoop.adapters.ImageViewColorAdapter
import com.ftinc.scoop.adapters.TextViewColorAdapter
import com.kanedias.dybr.fair.databinding.FragmentProfileBinding
import com.kanedias.dybr.fair.dto.ActionList
import com.kanedias.dybr.fair.dto.ActionListRequest
import com.kanedias.dybr.fair.dto.Auth
import com.kanedias.dybr.fair.dto.OwnProfile
import com.kanedias.dybr.fair.misc.idMatches
import com.kanedias.dybr.fair.misc.showFullscreenFragment
import com.kanedias.dybr.fair.service.Network
import com.kanedias.dybr.fair.themes.*
import kotlinx.coroutines.*
import moe.banana.jsonapi2.HasMany
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Kanedias
 *
 * Created on 20.07.18
 */
class ProfileFragment: DialogFragment() {

    lateinit var profile: OwnProfile
    private var actionLists: MutableList<ActionList> = mutableListOf()

    private lateinit var accountFavorited: Drawable
    private lateinit var accountUnfavorited: Drawable

    private lateinit var accountFeedBanned: Drawable
    private lateinit var accountFeedUnbanned: Drawable

    private lateinit var accountBanned: Drawable
    private lateinit var accountUnbanned: Drawable

    private lateinit var binding: FragmentProfileBinding
    private lateinit var labels: List<TextView>
    private lateinit var activity: MainActivity

    private lateinit var styleLevel: StyleLevel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedInstanceState?.getSerializable("profile")?.let { profile = it as OwnProfile }

        binding = FragmentProfileBinding.inflate(layoutInflater, null, false)
        labels = listOf(
                binding.authorNameLabel,
                binding.authorSubtextLabel,
                binding.authorRegistrationDateLabel,
                binding.authorBlogLabel
        )
        activity = context as MainActivity

        setupUI()

        return MaterialDialog(activity)
                .title(R.string.view_profile)
                .customView(view = binding.root, scrollable = true)
                .positiveButton(android.R.string.ok)
    }

    override fun onResume() {
        super.onResume()
        setupTheming(dialog as MaterialDialog)
    }

    private fun setupTheming(dialog: MaterialDialog) {
        styleLevel = Scoop.getInstance().addStyleLevel()
        lifecycle.addObserver(styleLevel)

        val dialogTitle = dialog.view.titleLayout.findViewById(R.id.md_text_title) as TextView
        val okButton = dialog.view.buttonsLayout!!.findViewById(R.id.md_button_positive) as DialogActionButton

        styleLevel.bindBgDrawable(BACKGROUND, dialog.view)
        styleLevel.bind(TEXT_BLOCK, dialog.view.titleLayout, DefaultColorAdapter())
        styleLevel.bind(TEXT_BLOCK, dialog.view.contentLayout, DefaultColorAdapter())
        styleLevel.bind(TEXT_BLOCK, dialog.view.buttonsLayout, DefaultColorAdapter())

        styleLevel.bind(TEXT_HEADERS, dialogTitle, TextViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, okButton, MaterialDialogButtonAdapter())

        styleLevel.bind(TEXT, binding.authorName, TextViewColorAdapter())
        styleLevel.bind(TEXT, binding.authorSubtext, TextViewColorAdapter())
        styleLevel.bind(TEXT, binding.authorRegistrationDate, TextViewColorAdapter())
        styleLevel.bind(TEXT, binding.authorBlog, TextViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, binding.authorBlog, TextViewLinksAdapter())
        styleLevel.bind(TEXT_LINKS, binding.authorAddToFavorites, ImageViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, binding.authorFeedBan, ImageViewColorAdapter())
        styleLevel.bind(TEXT_LINKS, binding.authorBan, ImageViewColorAdapter())

        labels.forEach { styleLevel.bind(TEXT, it, TextViewColorAdapter()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("profile", profile)
    }

    private fun setupUI() {
        accountFavorited = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_favorited, null)!!
        accountUnfavorited = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_unfavorited, null)!!
        accountFeedBanned = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_feed_banned, null)!!
        accountFeedUnbanned = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_feed_unbanned, null)!!
        accountBanned = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_banned, null)!!
        accountUnbanned = ResourcesCompat.getDrawable(activity.resources, R.drawable.account_unbanned, null)!!

        // setup actions
        binding.authorAddToFavorites.setOnClickListener { toggleFavorite() }
        binding.authorFeedBan.setOnClickListener { toggleFeedBan() }
        binding.authorBan.setOnClickListener { toggleBan() }

        // set names and dates
        binding.authorName.text = profile.nickname
        binding.authorSubtext.text = profile.settings.subtext
        binding.authorRegistrationDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(profile.createdAt)

        if (profile.blogSlug != null) {
            binding.authorBlog.text = HtmlCompat.fromHtml("<a href='https://dybr.ru/blog/${profile.blogSlug}'>${profile.blogTitle}</a>", 0)
            binding.authorBlog.setOnClickListener { dismiss(); showBlog(profile) }
        } else {
            binding.authorBlog.text = ""
        }

        // set avatar
        val avatar = Network.resolve(profile.settings.avatar) ?: Network.defaultAvatar()
            // load avatar asynchronously
        Glide.with(binding.authorAvatar)
                .load(avatar.toString())
                .apply(RequestOptions().centerInside())
                .into(binding.authorAvatar)

        if (profile.idMatches(Auth.profile) || Auth.user == Auth.guest) {
            // hide all buttons if we're guest or it's our own profile
            // and don't refresh any views
            binding.authorAddToFavorites.visibility = View.GONE
            binding.authorFeedBan.visibility = View.GONE
            binding.authorBan.visibility = View.GONE
            return
        }

        // set favorite status
        when {
            Auth.profile?.favorites?.any { it.idMatches(profile) } == true -> binding.authorAddToFavorites.setImageDrawable(accountFavorited)
            else -> binding.authorAddToFavorites.setImageDrawable(accountUnfavorited)
        }

        // set feed banned status
        lifecycleScope.launch {
            Network.perform(
                networkAction = { Network.loadActionLists() },
                uiAction = { loaded ->
                    actionLists = loaded
                    setupActionLists()
                }
            )
        }
    }

    private fun setupActionLists() {
        val feedBanned = actionLists.filter { it.action == "hide" && it.scope == "feed" }
                .flatMap { list -> list.profiles.get() }
                .any { prof -> prof.idMatches(profile) }

        binding.authorFeedBan.visibility = View.VISIBLE
        when {
            feedBanned -> binding.authorFeedBan.setImageDrawable(accountFeedBanned)
            else -> binding.authorFeedBan.setImageDrawable(accountFeedUnbanned)
        }

        val accBanned = actionLists.filter { it.action == "ban" && it.scope == "blog" }
                .flatMap { list -> list.profiles.get() }
                .any { prof -> prof.idMatches(profile) }

        binding.authorBan.visibility = View.VISIBLE
        when {
            accBanned -> binding.authorBan.setImageDrawable(accountBanned)
            else -> binding.authorBan.setImageDrawable(accountUnbanned)
        }
    }

    fun toggleFeedBan() {
        val feedBanned = actionLists.filter { it.action == "hide" && it.scope == "feed" }
                                    .flatMap { list -> list.profiles.get() }
                                    .any { prof -> prof.idMatches(profile) }

        lifecycleScope.launch {
            if (feedBanned) {
                // remove from feed bans
                val feedBanList = actionLists.first { it.action == "hide" && it.scope == "feed" }
                Network.perform(
                    networkAction = { Network.removeFromActionList(feedBanList, profile) },
                    uiAction = {
                        feedBanList.profiles.remove(profile)
                        binding.authorFeedBan.setImageDrawable(accountFeedUnbanned)
                        Toast.makeText(activity, R.string.unbanned_from_feed, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // add to feed bans
                val feedBanList = actionLists.firstOrNull { it.action == "hide" && it.scope == "feed" }
                val feedBanReq = ActionListRequest().apply {
                    kind = "profile"
                    action = "hide"
                    scope = "feed"
                    profiles.add(profile)
                }

                Network.perform(
                    networkAction = { Network.addToActionList(feedBanReq) },
                    uiAction = {
                        if (feedBanList != null) {
                            feedBanList.profiles.add(profile)
                        } else {
                            actionLists.add(ActionList().apply {
                                kind = "profile"
                                action = "hide"
                                scope = "feed"
                                profiles = HasMany(profile)
                            })
                        }
                        binding.authorFeedBan.setImageDrawable(accountFeedBanned)
                        Toast.makeText(activity, R.string.banned_from_feed, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    fun toggleBan() {
        val accBanned = actionLists.filter { it.action == "ban" && it.scope == "blog" }
                .flatMap { list -> list.profiles.get() }
                .any { prof -> prof.idMatches(profile) }

        lifecycleScope.launch {
            if (accBanned) {
                // remove from bans
                val banList = actionLists.first { it.action == "ban" && it.scope == "blog" }
                Network.perform(
                        networkAction = { Network.removeFromActionList(banList, profile) },
                        uiAction = {
                            banList.profiles.remove(profile)
                            binding.authorBan.setImageDrawable(accountUnbanned)
                            Toast.makeText(activity, R.string.unbanned, Toast.LENGTH_SHORT).show()
                        }
                )
            } else {
                // add to bans
                val banList = actionLists.firstOrNull { it.action == "ban" && it.scope == "blog" }
                val banReq = ActionListRequest().apply {
                    kind = "profile"
                    action = "ban"
                    scope = "blog"
                    profiles.add(profile)
                }

                Network.perform(
                        networkAction = { Network.addToActionList(banReq) },
                        uiAction = {
                            if (banList != null) {
                                banList.profiles.add(profile)
                            } else {
                                actionLists.add(ActionList().apply {
                                    kind = "profile"
                                    action = "ban"
                                    scope = "blog"
                                    profiles = HasMany(profile)
                                })
                            }
                            binding.authorBan.setImageDrawable(accountBanned)
                            Toast.makeText(activity, R.string.banned, Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
    }

    fun toggleFavorite() {
        lifecycleScope.launch {
            try {
                if (Auth.profile?.favorites?.any { it.idMatches(profile) } == true) {
                    // remove from favorites
                    withContext(Dispatchers.IO) { Network.removeFavorite(profile) }
                    Auth.profile?.favorites?.remove(profile)
                    binding.authorAddToFavorites.setImageDrawable(accountUnfavorited)
                    Toast.makeText(activity, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
                } else {
                    // add to favorites
                    withContext(Dispatchers.IO) { Network.addFavorite(profile) }
                    Auth.profile?.apply {
                        favorites.add(profile)
                        document.addInclude(profile)
                    }
                    binding.authorAddToFavorites.setImageDrawable(accountFavorited)
                    Toast.makeText(activity, R.string.added_to_favorites, Toast.LENGTH_SHORT).show()
                }
            } catch (ioex: IOException) {
                Network.reportErrors(context, ioex)
            }
        }
    }

    private fun showBlog(profile: OwnProfile) {
        val browseFragment = EntryListFragmentFull().apply { this.profile = profile }
        activity.showFullscreenFragment(browseFragment)
    }
}