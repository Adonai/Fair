package com.kanedias.dybr.fair

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.kanedias.dybr.fair.dto.Auth
import com.kanedias.dybr.fair.dto.BlogCreateRequest
import kotlinx.coroutines.*

/**
 * Fragment for creating blog for currently logged in profile.
 *
 * @author Kanedias
 *
 * Created on 14.01.18
 */
class AddBlogFragment: Fragment() {

    private lateinit var activity: MainActivity

    @BindView(R.id.blog_slug_input)
    lateinit var slugInput: EditText

    @BindView(R.id.blog_title_input)
    lateinit var titleInput: EditText

    private lateinit var progressDialog: MaterialDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_create_blog, container, false)
        ButterKnife.bind(this, root)
        activity = context as MainActivity

        progressDialog = MaterialDialog(activity)
                .title(R.string.please_wait)
                .message(R.string.checking_in_progress)

        return root
    }

    @OnClick(R.id.blog_create_button)
    fun confirm() {
        val blogReq = BlogCreateRequest().apply {
            slug = slugInput.text.toString()
            title = titleInput.text.toString()
            profile.set(Auth.profile)
        }

        GlobalScope.launch(Dispatchers.Main) {
            progressDialog.show()

            try {
                val blog = withContext(Dispatchers.IO) { Network.createBlog(blogReq) }
                //Auth.updateBlog(blog)

                //we created blog successfully, return to main activity
                Toast.makeText(activity, R.string.blog_created, Toast.LENGTH_SHORT).show()
                handleSuccess()
            } catch (ex: Exception) {
                Network.reportErrors(activity, ex)
            }

            progressDialog.hide()
        }
    }

    /**
     * Handle successful blog addition. Navigate back to [MainActivity] and update sidebar account list.
     */
    private fun handleSuccess() {
        fragmentManager!!.popBackStack()
        activity.refresh()
    }
}