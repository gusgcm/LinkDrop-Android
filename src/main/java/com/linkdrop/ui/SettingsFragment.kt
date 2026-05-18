package com.linkdrop.ui

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.linkdrop.databinding.FragmentSettingsBinding
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    private var lastClickTime = 0L
    private fun isClickTooFast(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 500) return true
        lastClickTime = now
        return false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        // Load saved values
        binding.editIp.setText(prefs.serverIp)
        binding.editPort.setText(prefs.serverPort)
        binding.editPassword.setText(prefs.password)
        binding.switchClipboard.isChecked = prefs.autoClipboard

        binding.btnSave.setOnClickListener { 
            if (!isClickTooFast()) saveSettings() 
        }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun saveSettings(showToast: Boolean = true): Boolean {
        val ip   = binding.editIp.text?.toString()?.trim() ?: ""
        val port = binding.editPort.text?.toString()?.trim() ?: "8765"
        val pwd  = binding.editPassword.text?.toString()?.trim() ?: ""

        if (ip.isEmpty()) {
            Toast.makeText(requireContext(), "Enter server IP", Toast.LENGTH_SHORT).show()
            return false
        }

        prefs.serverIp       = ip
        prefs.serverPort     = port
        prefs.password       = pwd
        prefs.autoClipboard  = binding.switchClipboard.isChecked

        if (showToast) {
            Toast.makeText(requireContext(), "✅ Settings saved!", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun testConnection() {
        if (isClickTooFast()) return
        if (!saveSettings(showToast = false)) return

        binding.btnTest.isEnabled = false
        binding.statusText.text   = "Testing…"

        lifecycleScope.launch {
            try {
                val info = LinkDropApi(prefs).ping()
                binding.statusText.text = "✅ Connected to \"${info.name}\"  •  v${info.version}"
                Toast.makeText(requireContext(), "Connection successful!", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                binding.statusText.text = "❌ Connection failed"
                Toast.makeText(requireContext(), "Connection failed. Please check IP, Port and Password.", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnTest.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
