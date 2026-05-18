package com.linkdrop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.linkdrop.databinding.ActivityMainBinding
import com.linkdrop.network.LinkDropApi
import com.linkdrop.prefs.Prefs
import com.linkdrop.ui.FilesFragment
import com.linkdrop.ui.ClipboardFragment
import com.linkdrop.ui.SendFragment
import com.linkdrop.ui.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    var pendingShareText: String? = null
    var pendingShareUri: Uri?     = null

    private var lastClickTime = 0L
    private fun isClickTooFast(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 500) return true
        lastClickTime = now
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LinkDrop", "MainActivity onCreate started")
        
        try {
            _binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e("LinkDrop", "Error inflating layout", e)
            return
        }

        prefs = Prefs(this)
        
        try {
            setSupportActionBar(binding.toolbar)
            setupTabs()
        } catch (e: Exception) {
            Log.e("LinkDrop", "Error during setup", e)
        }

        if (savedInstanceState == null) {
            binding.root.post {
                try {
                    if (prefs.serverUrl.isEmpty()) {
                        binding.viewPager.setCurrentItem(3, false)
                        Toast.makeText(this, "Configure o endereço do PC em Settings", Toast.LENGTH_LONG).show()
                    } else {
                        handleIncomingShare(intent)
                    }
                } catch (e: Exception) {
                    Log.e("LinkDrop", "Error in post-init", e)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            when {
                intent.type == "text/plain" -> {
                    pendingShareText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    binding.viewPager.setCurrentItem(1, true)
                }
                intent.type != null -> {
                    pendingShareUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    binding.viewPager.setCurrentItem(1, true)
                }
            }
        }
    }

    private fun setupTabs() {
        val titles = listOf("Files", "Send", "Clipboard", "Settings")
        val icons  = listOf(
            R.drawable.ic_folder,
            R.drawable.ic_send,
            R.drawable.ic_clipboard,
            R.drawable.ic_settings,
        )

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> FilesFragment()
                    1 -> SendFragment()
                    2 -> ClipboardFragment()
                    3 -> SettingsFragment()
                    else -> throw IllegalStateException("Invalid position")
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = titles[pos]
            try {
                tab.setIcon(icons[pos])
            } catch (e: Exception) {
                Log.e("LinkDrop", "Error setting icon for tab $pos", e)
            }
        }.attach()
        
        binding.viewPager.offscreenPageLimit = 1
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_ping) {
            pingServer()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun pingServer() {
        if (isClickTooFast()) return
        val url = prefs.normalizedUrl()
        if (url.isEmpty()) {
            Toast.makeText(this, "Configura o IP primeiro!", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val api  = LinkDropApi(prefs)
                val info = api.ping()
                Toast.makeText(this@MainActivity, "✅ Conectado: ${info.name}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "❌ Connection failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
