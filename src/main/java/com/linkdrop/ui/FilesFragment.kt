package com.linkdrop.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.linkdrop.MainActivity
import com.linkdrop.R
import com.linkdrop.databinding.FragmentFilesBinding
import com.linkdrop.network.FileItem
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var adapter: FilesAdapter

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
            binding.emptyText.text = "Configure server address in Settings"
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
                binding.emptyText.text = "No files in share folder"
                binding.fileCount.text = "${files.size} file${if (files.size != 1) "s" else ""}"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.emptyText.text = "Cannot connect to server"
                binding.emptyText.visibility = View.VISIBLE
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun downloadFile(item: FileItem) {
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
                Toast.makeText(requireContext(), "Downloaded: ${item.name}", Toast.LENGTH_SHORT).show()

                // Offer to open
                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", destFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, requireContext().contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun openFile(item: FileItem) {
        downloadFile(item)
    }

    private fun confirmDelete(item: FileItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete file")
            .setMessage("Delete \"${item.name}\" from the server?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        LinkDropApi(prefs).deleteFile(item.name)
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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
