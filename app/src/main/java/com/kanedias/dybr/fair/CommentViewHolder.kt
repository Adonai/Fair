package com.kanedias.dybr.fair

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import butterknife.BindView
import butterknife.BindViews
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.kanedias.dybr.fair.dto.*
import com.kanedias.dybr.fair.misc.showFullscreenFragment
import com.kanedias.dybr.fair.themes.*
import com.kanedias.dybr.fair.ui.handleMarkdown
import com.kanedias.dybr.fair.ui.styleLevel
import kotlinx.coroutines.*


/**
 * View holder for showing comments in entry view.
 *
 * @see CommentListFragment.commentRibbon
 * @author Kanedias
 */
class CommentViewHolder(iv: View, private val parent: View, private val entry: Entry) : UserContentViewHolder<Comment>(iv) {

    @BindView(R.id.comment_avatar)
    lateinit var avatarView: ImageView

    @BindView(R.id.comment_date)
    lateinit var dateView: TextView

    @BindView(R.id.comment_author)
    lateinit var authorView: TextView

    @BindView(R.id.comment_message)
    lateinit var bodyView: TextView

    @BindViews(R.id.comment_edit, R.id.comment_delete)
    lateinit var buttons: List<@JvmSuppressWildcards ImageView>

    private lateinit var comment: Comment

    init {
        ButterKnife.bind(this, iv)
        setupTheming()

        // make text selectable
        bodyView.isLongClickable = true
    }

    override fun getCreationDateView() = dateView
    override fun getProfileAvatarView() = avatarView
    override fun getAuthorNameView() = authorView
    override fun getContentView() = bodyView

    private fun setupTheming() {
        val styleLevel = parent.styleLevel ?: return

        styleLevel.bind(TEXT_BLOCK, itemView, CardViewColorAdapter())
        styleLevel.bind(TEXT, authorView)
        styleLevel.bind(TEXT, dateView)
        styleLevel.bind(TEXT, bodyView)
        styleLevel.bind(TEXT_LINKS, bodyView, TextViewLinksAdapter())
        buttons.forEach { styleLevel.bind(TEXT_LINKS, it) }
    }

    @OnClick(R.id.comment_edit)
    fun editComment() {
        val activity = itemView.context as AppCompatActivity
        val commentEdit = CreateNewCommentFragment().apply {
            entry = this@CommentViewHolder.entry
            editComment = this@CommentViewHolder.comment
            editMode = true
        }

        activity.showFullscreenFragment(commentEdit)
    }

    @OnClick(R.id.comment_delete)
    fun deleteComment() {
        val activity = itemView.context as AppCompatActivity

        // delete callback
        val delete = {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    withContext(Dispatchers.IO) { Network.deleteComment(comment) }
                    Toast.makeText(activity, R.string.comment_deleted, Toast.LENGTH_SHORT).show()

                    // if we have current tab, refresh it
                    val clPredicate = { it: Fragment -> it is CommentListFragment && it.userVisibleHint }
                    val currentTab = activity.supportFragmentManager.fragments.find(clPredicate) as CommentListFragment?
                    currentTab?.loadMore(reset = true)
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
                .show()
    }

    /**
     * Show or hide entry editing buttons depending on circumstances
     */
    private fun toggleEditButtons(show: Boolean) {
        val visibility = when (show) {
            true -> View.VISIBLE
            false -> View.GONE
        }
        val editTag = itemView.context.getString(R.string.edit_tag)
        buttons.filter { it.tag == editTag }.forEach { it.visibility = visibility }
    }

    /**
     * Called when this holder should be refreshed based on what it must show now
     */
    override fun setup(entity: Comment, standalone: Boolean) {
        super.setup(entity, standalone)

        this.comment = entity

        bodyView.handleMarkdown(comment.content)

        val profile = comment.profile.get(comment.document)
        toggleEditButtons(isBlogWritable(profile))
    }
}