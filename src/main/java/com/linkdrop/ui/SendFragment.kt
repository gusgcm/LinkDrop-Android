package com.linkdrop.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.linkdrop.MainActivity
import com.linkdrop.R
import com.linkdrop.databinding.FragmentSendBinding
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.launch
import java.io.File

class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) uploadFiles(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        binding.btnSendText.setOnClickListener { sendText() }
        binding.btnPickFile.setOnClickListener { pickFile.launch("*/*") }
        binding.btnSendNotification.setOnClickListener { sendNotification() }

        // Pre-fill from share intent
        val main = activity as? MainActivity
        main?.pendingShareText?.let {
            binding.editText.setText(it)
            main.pendingShareText = null
        }
        main?.pendingShareUri?.let {
            uploadFiles(listOf(it))
            main.pendingShareUri = null
        }
    }

    private fun sendText() {
        val text = binding.editText.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_type_something, Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                LinkDropApi(prefs).sendText(text)
                Toast.makeText(requireContext(), R.string.msg_text_sent, Toast.LENGTH_SHORT).show()
                binding.editText.setText("")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.msg_error, e.message), Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun sendNotification() {
        val title   = binding.editNotifTitle.text?.toString()?.trim() ?: "LinkDrop"
        val message = binding.editNotifMsg.text?.toString()?.trim()
        if (message.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_enter_notif_msg, Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                LinkDropApi(prefs).sendNotification(title, message)
                Toast.makeText(requireContext(), R.string.msg_notif_sent, Toast.LENGTH_SHORT).show()
                binding.editNotifMsg.setText("")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.msg_error, e.message), Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun uploadFiles(uris: List<Uri>) {
        setLoading(true)
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            var success = 0
            var failed  = 0
            for (uri in uris) {
                try {
                    val file = uriToTempFile(uri)
                    LinkDropApi(prefs).uploadFile(file) { progress ->
                        activity?.runOnUiThread {
                            binding.progressBar.progress = progress
                        }
                    }
                    file.delete()
                    success++
                } catch (e: Exception) {
                    failed++
                }
            }
            val msg = buildString {
                if (success > 0) append(getString(R.string.msg_upload_success, success))
                if (failed  > 0) append(getString(R.string.msg_upload_failed, failed))
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
            setLoading(false)
        }
    }

    private fun uriToTempFile(uri: Uri): File {
        val name = getFileName(uri) ?: "upload_${System.currentTimeMillis()}"
        val tmp  = File(requireContext().cacheDir, name)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        return tmp
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun setLoading(on: Boolean) {
        binding.btnSendText.isEnabled        = !on
        binding.btnPickFile.isEnabled        = !on
        binding.btnSendNotification.isEnabled = !on
        binding.loadingOverlay.visibility    = if (on) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
