/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.ktfiles

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Fragment that shows a list of documents in a directory.
 */
class DirectoryFragment : Fragment() {
    private lateinit var directoryUri: Uri

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DirectoryEntryAdapter

    private lateinit var viewModel: DirectoryFragmentViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        directoryUri = arguments?.getString(ARG_DIRECTORY_URI)?.toUri()
            ?: throw IllegalArgumentException("Must pass URI of directory to open")

        viewModel = ViewModelProviders.of(this)
            .get(DirectoryFragmentViewModel::class.java)

        val view = inflater.inflate(R.layout.fragment_directory, container, false)
        recyclerView = view.findViewById(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)

        adapter = DirectoryEntryAdapter(object : ClickListeners {
            override fun onDocumentClicked(clickedDocument: CachingDocumentFile) {
                viewModel.documentClicked(clickedDocument)
            }

            override fun onDocumentLongClicked(clickedDocument: CachingDocumentFile) {
                renameDocument(clickedDocument)
            }
        })

        recyclerView.adapter = adapter

        viewModel.documents.observe(viewLifecycleOwner, Observer { documents ->
            documents?.let { adapter.setEntries(documents) }
        })

        viewModel.openDirectory.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { directory ->
                (activity as? MainActivity)?.showDirectoryContents(directory.uri)
            }
        })

        viewModel.openDocument.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { document ->
                showDialog(document, directoryUri)

            }
        })

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.loadDirectory(directoryUri)
    }

    private fun showDialog(document: CachingDocumentFile, directoryUri: Uri) {
        // Late initialize an alert dialog object
        lateinit var dialog: AlertDialog
        val array = arrayOf("Open", "Encrypt", "Decrypt")
        val builder = AlertDialog.Builder(this.activity)
        builder.setTitle("Choose an action.")

        // Set the single choice items for alert dialog with initial selection
        builder.setSingleChoiceItems(array, -1) { _, which ->
            // Get the dialog selected item
            val action = array[which]

            when (action) {
                "Open" -> openDocument(document)
                "Encrypt" -> encryptDocument(document)
                "Decrypt" -> decryptDocument(document)
            }

            // Dismiss the dialog
            dialog.dismiss()
        }

        dialog = builder.create()
        dialog.show()
    }

    @Throws(IOException::class)
    fun InputStream.readAllBytes(): ByteArray {
        val bufLen = 4 * 0x400 // 4KB
        val buf = ByteArray(bufLen)
        var readLen: Int = 0

        ByteArrayOutputStream().use { o ->
            this.use { i ->
                while (i.read(buf, 0, bufLen).also { readLen = it } != -1)
                    o.write(buf, 0, readLen)
            }

            return o.toByteArray()
        }
    }

    private fun encryptDocument(document: CachingDocumentFile) {

        val key = AesPbkdf2Helper.getSecretKey(requireActivity())

        try {
            val input = requireActivity().contentResolver.openInputStream(document.uri)


            val data = input.readAllBytes()
            Log.e("df","data-"+String(data))

            var encData = AesPbkdf2Helper.encrypt(key, data)
            Log.e("df","encData-"+String(encData))

            val output = requireActivity().contentResolver.openOutputStream(document.uri, "w")
            output.write(encData)
            output.flush()
            output.close()


            Toast.makeText(
                requireContext(),
                "Encrypted successfully",
                Toast.LENGTH_SHORT
            ).show()


        } catch (e: Exception) {
            e.printStackTrace()

            Toast.makeText(
                requireContext(),
                "Encryption failed",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    private fun decryptDocument(document: CachingDocumentFile) {

        val key = AesPbkdf2Helper.getSecretKey(requireActivity())

        try {
            val input = requireActivity().contentResolver.openInputStream(document.uri)


            val data = input.readAllBytes()
            Log.e("df","data-"+String(data))

            var encData = AesPbkdf2Helper.decrypt(key, data)
            Log.e("df","encData-"+String(encData))

            val output = requireActivity().contentResolver.openOutputStream(document.uri, "w")
            output.write(encData)
            output.flush()
            output.close()


            Toast.makeText(
                requireContext(),
                "Decrypted successfully",
                Toast.LENGTH_SHORT
            ).show()


        } catch (e: Exception) {
            e.printStackTrace()

            Toast.makeText(
                requireContext(),
                "Decryption failed",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    private fun openDocument(document: CachingDocumentFile) {
        try {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                data = document.uri
            }
            startActivity(openIntent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                resources.getString(R.string.error_no_activity, document.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("InflateParams")
    private fun renameDocument(document: CachingDocumentFile) {
        // Normally we don't want to pass `null` in as the parent, but the dialog doesn't exist,
        // so there isn't a parent layout to use yet.
        val dialogView = layoutInflater.inflate(R.layout.rename_layout, null)
        val editText = dialogView.findViewById<EditText>(R.id.file_name)
        editText.setText(document.name)

        // Use a lambda so that we have access to the [EditText] with the new name.
        val buttonCallback: (DialogInterface, Int) -> Unit = { _, buttonId ->
            when (buttonId) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val newName = editText.text.toString()
                    if (newName.isNotBlank()) {
                        document.rename(newName)

                        // The easiest way to refresh the UI is to load the directory again.
                        viewModel.loadDirectory(directoryUri)
                    }
                }
            }
        }

        val renameDialog = AlertDialog.Builder(requireActivity())
            .setTitle(R.string.rename_title)
            .setView(dialogView)
            .setPositiveButton(R.string.rename_okay, buttonCallback)
            .setNegativeButton(R.string.rename_cancel, buttonCallback)
            .create()

        // When the dialog is shown, select the name so it can be easily changed.
        renameDialog.setOnShowListener {
            editText.requestFocus()
            editText.selectAll()
        }

        renameDialog.show()
    }

    companion object {

        /**
         * Convenience method for constructing a [DirectoryFragment] with the directory uri
         * to display.
         */
        @JvmStatic
        fun newInstance(directoryUri: Uri) =
            DirectoryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DIRECTORY_URI, directoryUri.toString())
                }
            }
    }
}

private const val ARG_DIRECTORY_URI = "com.example.android.directoryselection.ARG_DIRECTORY_URI"
