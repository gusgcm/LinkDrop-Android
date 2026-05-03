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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        // Load saved values
        binding.editUrl.setText(prefs.serverUrl)
        binding.editPassword.setText(prefs.password)
        binding.switchClipboard.isChecked = prefs.autoClipboard

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTest.setOnClickListener { testConnection() }
    }

    private fun saveSettings() {
        val url = binding.editUrl.text?.toString()?.trim() ?: ""
        val pwd = binding.editPassword.text?.toString()?.trim() ?: ""

        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Enter server address", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.serverUrl      = url
        prefs.password       = pwd
        prefs.autoClipboard  = binding.switchClipboard.isChecked

        Toast.makeText(requireContext(), "✅ Settings saved!", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        saveSettings()
        binding.btnTest.isEnabled = false
        binding.statusText.text   = "Testing…"

        lifecycleScope.launch {
            try {
                val info = LinkDropApi(prefs).ping()
                binding.statusText.text = "✅ Connected to \"${info.name}\"  •  v${info.version}"
                Toast.makeText(requireContext(), "Connection successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.statusText.text = "❌ Failed: ${e.message}"
                Toast.makeText(requireContext(), "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
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
