package com.linkdrop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.linkdrop.databinding.FragmentClipboardBinding
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.launch

class ClipboardFragment : Fragment() {

    private var _binding: FragmentClipboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentClipboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        binding.btnGetPcClip.setOnClickListener   { getPcClipboard() }
        binding.btnPushToPhone.setOnClickListener  { copyToPhone() }
        binding.btnSendToPC.setOnClickListener    { sendClipboardToPC() }
        binding.btnGetDeviceClip.setOnClickListener { loadDeviceClipboard() }
    }

    private fun getPcClipboard() {
        lifecycleScope.launch {
            try {
                val text = LinkDropApi(prefs).getClipboard()
                binding.clipText.setText(text)
                Toast.makeText(requireContext(), "Clipboard loaded from PC", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendClipboardToPC() {
        val text = binding.clipText.text?.toString() ?: return
        lifecycleScope.launch {
            try {
                LinkDropApi(prefs).pushClipboard(text)
                Toast.makeText(requireContext(), "✅ Sent to PC clipboard!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyToPhone() {
        val text = binding.clipText.text?.toString() ?: return
        val cm   = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LinkDrop", text))
        Toast.makeText(requireContext(), "Copied to phone clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun loadDeviceClipboard() {
        val cm   = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        binding.clipText.setText(text)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
