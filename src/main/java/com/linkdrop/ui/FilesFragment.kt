package com.linkdrop.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.linkdrop.MainActivity
import com.linkdrop.R
import com.linkdrop.databinding.FragmentFilesBinding
import com.linkdrop.network.FileItem
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var adapter: FilesAdapter

    private var lastClickTime = 0L
    private fun isClickTooFast(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 500) return true
        lastClickTime = now
        return false
    }

    private var pendingDownload: FileItem? = null

    private val saveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { performDownload(pendingDownload!!, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        adapter = FilesAdapter(
            onDownload = { item -> downloadFile(item) },
            onDelete   = { item -> confirmDelete(item) },
            onOpen     = { item -> openFile(item) },
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter       = adapter

        binding.swipeRefresh.setOnRefreshListener { loadFiles() }
        binding.swipeRefresh.setColorSchemeResources(R.color.accent)

        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        if (isAdded) loadFiles()
    }

    private fun loadFiles() {
        if (prefs.serverUrl.isEmpty()) {
            binding.emptyText.text = getString(R.string.msg_configure_server)
            binding.emptyText.visibility = View.VISIBLE
            binding.swipeRefresh.isRefreshing = false
            return
        }

        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api   = LinkDropApi(prefs)
                val files = api.listFiles()
                adapter.setItems(files)
                binding.emptyText.visibility =
                    if (files.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.text = getString(R.string.label_no_files)
                binding.fileCount.text = if (files.size == 1) {
                    getString(R.string.label_file_count_single)
                } else {
                    getString(R.string.label_file_count_plural, files.size)
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.msg_conn_failed_simple, Toast.LENGTH_LONG).show()
                binding.emptyText.text = getString(R.string.msg_cannot_connect)
                binding.emptyText.visibility = View.VISIBLE
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun downloadFile(item: FileItem) {
        if (isClickTooFast()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf(
                getString(R.string.action_save_downloads),
                getString(R.string.action_save_as),
                getString(R.string.action_open_temp)
            )) { _, which ->
                when (which) {
                    0 -> saveToDownloads(item)
                    1 -> {
                        pendingDownload = item
                        saveAsLauncher.launch(item.name)
                    }
                    2 -> openFile(item)
                }
            }
            .show()
    }

    private fun saveToDownloads(item: FileItem) {
        val resolver = requireContext().contentResolver
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                val ext = item.name.substringAfterLast(".", "")
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                performDownload(item, uri)
            } else {
                Toast.makeText(requireContext(), R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Pre-Q: Save directly to the Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, item.name)
            
            // We need to check for permission if we were going to write directly, 
            // but since we are using FileProvider or just wanting to "save", 
            // let's try to use MediaStore for consistency if possible, or just File.
            
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            lifecycleScope.launch {
                try {
                    val api = LinkDropApi(prefs)
                    api.downloadFile(item.name, destFile) { progress ->
                        activity?.runOnUiThread {
                            binding.progressBar.progress = progress
                        }
                    }
                    
                    // Trigger media scanner so it shows up in file managers
                    val uri = Uri.fromFile(destFile)
                    val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
                    requireContext().sendBroadcast(scanIntent)

                    Snackbar.make(binding.root, R.string.action_save_downloads, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_open) {
                            val openUri = FileProvider.getUriForFile(
                                requireContext(), "${requireContext().packageName}.provider", destFile)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(openUri, requireContext().contentResolver.getType(openUri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try { startActivity(intent) } catch (_: Exception) {}
                        }.show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.msg_download_failed, e.message), Toast.LENGTH_LONG).show()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun performDownload(item: FileItem, uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        lifecycleScope.launch {
            try {
                val api = LinkDropApi(prefs)
                val os = requireContext().contentResolver.openOutputStream(uri) 
                    ?: throw Exception(getString(R.string.msg_output_stream_error))
                
                api.downloadFile(item.name, os) { progress ->
                    activity?.runOnUiThread {
                        binding.progressBar.progress = progress
                    }
                }
                
                Snackbar.make(binding.root, getString(R.string.msg_saved, item.name), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_open) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), R.string.msg_no_app_open, Toast.LENGTH_SHORT).show()
                        }
                    }.show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.msg_download_failed, e.message), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun openFile(item: FileItem) {
        val cacheDir = requireContext().cacheDir
        val destFile = File(cacheDir, item.name)

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = LinkDropApi(prefs)
                api.downloadFile(item.name, destFile) { progress ->
                    activity?.runOnUiThread {
                        binding.progressBar.progress = progress
                    }
                }
                
                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", destFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.msg_error, e.message), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmDelete(item: FileItem) {
        if (isClickTooFast()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, item.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        LinkDropApi(prefs).deleteFile(item.name)
                        Toast.makeText(requireContext(), R.string.msg_deleted, Toast.LENGTH_SHORT).show()
                        loadFiles()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), getString(R.string.msg_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class FilesAdapter(
    private val onDownload: (FileItem) -> Unit,
    private val onDelete:   (FileItem) -> Unit,
    private val onOpen:     (FileItem) -> Unit,
) : androidx.recyclerview.widget.RecyclerView.Adapter<FilesAdapter.VH>() {

    private val items = mutableListOf<FileItem>()

    fun setItems(list: List<FileItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: com.linkdrop.databinding.ItemFileBinding)
        : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.linkdrop.databinding.ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvName.text = item.name
            tvSize.text = formatSize(item.size)
            tvDate.text = formatDate(item.modified)
            tvExt.text  = item.name.substringAfterLast(".", "?").uppercase()

            btnDownload.setOnClickListener { onDownload(item) }
            btnDelete.setOnClickListener  { onDelete(item) }
            root.setOnClickListener       { onOpen(item) }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
        return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun formatDate(timestamp: Double): String {
        val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        return sdf.format(Date((timestamp * 1000).toLong()))
    }
}
