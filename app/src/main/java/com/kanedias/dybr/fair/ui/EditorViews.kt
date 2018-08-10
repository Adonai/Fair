package com.kanedias.dybr.fair.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.kanedias.dybr.fair.Network
import com.kanedias.dybr.fair.R
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

/**
 * Fragment to hold all editing-related functions in all edit views where possible.
 *
 * @author Kanedias
 *
 * Created on 07.04.18
 */
class EditorViews : Fragment() {

    companion object {
        const val ACTIVITY_REQUEST_IMAGE_UPLOAD = 0

        const val PERMISSION_REQUEST_STORAGE_FOR_IMAGE_UPLOAD = 0
    }

    @BindView(R.id.source_text)
    lateinit var contentInput: EditText

    @BindView(R.id.edit_insert_from_clipboard)
    lateinit var clipboardSwitch: CheckBox

    @BindView(R.id.edit_formatting_helper_label)
    lateinit var mdLabel: TextView

    @BindView(R.id.edit_quick_image)
    lateinit var imageUpload: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_edit_form, container, false)
        ButterKnife.bind(this, root)

        @Suppress("DEPRECATION") // we need to support API < 24
        mdLabel.text = Html.fromHtml(getString(R.string.markdown_basics))
        mdLabel.movementMethod = LinkMovementMethod.getInstance()

        // start editing content right away
        contentInput.requestFocus()

        return root
    }

    /**
     * Handler of all small editing buttons above content input.
     */
    @OnClick(
            R.id.edit_quick_bold, R.id.edit_quick_italic, R.id.edit_quick_underlined, R.id.edit_quick_strikethrough,
            R.id.edit_quick_code, R.id.edit_quick_quote, R.id.edit_quick_number_list, R.id.edit_quick_bullet_list,
            R.id.edit_quick_link
    )
    fun editSelection(clicked: View) {
        val clipboard = clicked.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val paste = if (clipboardSwitch.isChecked && clipboard.hasPrimaryClip() && clipboard.primaryClip.itemCount > 0) {
            clipboard.primaryClip.getItemAt(0).text.toString()
        } else {
            ""
        }

        when (clicked.id) {
            R.id.edit_quick_bold -> insertInCursorPosition("<b>", paste, "</b>")
            R.id.edit_quick_italic -> insertInCursorPosition( "<i>", paste, "</i>")
            R.id.edit_quick_underlined -> insertInCursorPosition("<u>", paste, "</u>")
            R.id.edit_quick_strikethrough -> insertInCursorPosition("<s>", paste, "</s>")
            R.id.edit_quick_code -> insertInCursorPosition("```\n", paste, "\n```\n")
            R.id.edit_quick_quote -> insertInCursorPosition("> ", paste)
            R.id.edit_quick_number_list -> insertInCursorPosition("\n1. ", paste, "\n2. \n3. ")
            R.id.edit_quick_bullet_list -> insertInCursorPosition("\n* ", paste, "\n* \n* ")
            R.id.edit_quick_link -> insertInCursorPosition("<a href=\"$paste\">", paste, "</a>")
            R.id.edit_quick_image -> insertInCursorPosition("<img src='", paste, "' />")
        }

        clipboardSwitch.isChecked = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.isEmpty()) {
            return // request cancelled
        }

        // Return from the permission request we sent in [uploadImage]
        if (requestCode == PERMISSION_REQUEST_STORAGE_FOR_IMAGE_UPLOAD) {
            val result = permissions.filterIndexed { idx, pm -> pm == WRITE_EXTERNAL_STORAGE && grantResults[idx] == PERMISSION_GRANTED }
            when (result.any()) {
                true -> uploadImage(imageUpload)
                false -> Toast.makeText(requireContext(), R.string.no_permissions, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Image upload button requires special handling
     */
    @OnClick(R.id.edit_quick_image)
    fun uploadImage(clicked: View) {
        // sometimes we need SD-card access to load the image
        if (ContextCompat.checkSelfPermission(requireContext(), WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_STORAGE_FOR_IMAGE_UPLOAD)
            return
        }

        if (clipboardSwitch.isChecked) {
            // delegate to just paste image link from clipboard
            editSelection(clicked)
            return
        }

        // not from clipboard, show upload dialog
        val ctx = clicked.context as AppCompatActivity
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            val chooser = Intent.createChooser(intent, getString(R.string.select_image_to_upload))
            startActivityForResult(chooser, ACTIVITY_REQUEST_IMAGE_UPLOAD)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(ctx, getString(R.string.no_file_manager_found), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Called when activity called to select image/file to upload has finished executing
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        when (requestCode) {
            ACTIVITY_REQUEST_IMAGE_UPLOAD -> requestImageUpload(data)
        }
    }

    private fun requestImageUpload(intent: Intent?) {
        if (intent?.data == null)
            return

        val stream = activity?.contentResolver?.openInputStream(intent.data) ?: return
        launch(UI) {
            val dialog = MaterialDialog.Builder(activity!!)
                    .title(R.string.please_wait)
                    .content(R.string.uploading)
                    .progress(true, 0)
                    .show()

            try {
                val link = async { Network.uploadImage(stream.readBytes()) }.await()
                MaterialDialog.Builder(activity!!)
                        .title(R.string.insert_image)
                        .content(R.string.select_image_height)
                        .items(R.array.image_sizes)
                        .itemsCallback { _, _, pos, _ ->
                            val spec = when (pos) {
                                0 -> "100"
                                1 -> "200"
                                2 -> "300"
                                3 -> "500"
                                4 -> "800"
                                else -> "auto"
                            }
                            insertInCursorPosition("<img height='$spec' width='auto' src='", link, "' />")
                        }.show()
            } catch (ex: Exception) {
                Network.reportErrors(activity!!, ex)
            }

            dialog.dismiss()
        }
    }

    /**
     * Helper function for inserting quick snippets of markup into the various parts of edited text
     * @param prefix prefix preceding content.
     *          This is most likely non-empty. Cursor is positioned after it in all cases.
     * @param what content to insert.
     *          If it's empty and [suffix] is not, cursor will be positioned here
     * @param suffix suffix after content. Can be empty fairly often. Cursor will be placed after it if [what] is
     *          not empty.
     */
    private fun insertInCursorPosition(prefix: String, what: String, suffix: String = "") {
        var cursorPos = contentInput.selectionStart
        if (cursorPos == -1)
            cursorPos = contentInput.text.length

        val beforeCursor = contentInput.text.substring(0, cursorPos)
        val afterCursor = contentInput.text.substring(cursorPos, contentInput.text.length)

        val beforeCursorWithPrefix = beforeCursor + prefix
        val suffixWithAfterCursor = suffix + afterCursor
        val result = beforeCursorWithPrefix + what + suffixWithAfterCursor
        contentInput.setText(result)

        when {
        // empty string between tags, set cursor between tags
            what.isEmpty() -> contentInput.setSelection(contentInput.text.indexOf(suffixWithAfterCursor, cursorPos))
        // append to text, set cursor to the end of text
            afterCursor.isEmpty() -> contentInput.setSelection(contentInput.text.length)
        // insert inside text, set cursor at the end of paste
            else -> contentInput.setSelection(contentInput.text.indexOf(afterCursor, cursorPos))
        }
    }

}