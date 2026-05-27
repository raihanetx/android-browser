package com.zbrowser.app

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zbrowser.app.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NavigationController(
    private val binding: ActivityMainBinding,
    private val currentWebView: () -> WebView?,
    private val activity: AppCompatActivity
) {
    private val lifecycleScope get() = activity.lifecycleScope

    fun setupUrlBar() {
        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.urlBar.text.toString().trim()
                if (input.isNotEmpty()) loadUrl(processInput(input))
                true
            } else false
        }
        binding.urlBar.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.urlBar.selectAll() }
    }

    fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(activity, R.color.colorPrimary)
        )
        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            currentWebView()?.let { it.canScrollVertically(-1) } ?: false
        }
        // Disable SwipeRefreshLayout to avoid conflict with horizontal swipe
        binding.swipeRefresh.isEnabled = false
    }

    fun setupNavigationButtons() {
        // Navigation buttons removed - using swipe gesture and menu instead
    }

    fun animateProgress(targetProgress: Int) {
        if (activity.isFinishing || activity.isDestroyed) return

        binding.progressBar.animate().cancel()

        val current = binding.progressBar.progress
        if (targetProgress == 100) {
            binding.progressBar.progress = 100
            binding.progressBar.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    if (activity.isFinishing || activity.isDestroyed) return@withEndAction
                    binding.progressBar.visibility = View.GONE
                    binding.progressBar.progress = 0
                    binding.progressBar.alpha = 1f
                }
                .start()
        } else {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.alpha = 1f
            if (targetProgress > current) {
                binding.progressBar.progress = targetProgress
            }
        }
    }

    fun goBack() {
        currentWebView()?.let { if (it.canGoBack()) it.goBack() }
    }

    fun goForward() {
        currentWebView()?.let { if (it.canGoForward()) it.goForward() }
    }

    fun refresh() {
        currentWebView()?.reload()
    }

    fun loadUrl(url: String) {
        currentWebView()?.loadUrl(url)
        binding.urlBar.setText(url)
    }

    fun updateNavigationButtons() {
        // Navigation buttons removed - using swipe gesture and menu instead
    }

    fun updateSslIcon(url: String) {
        val isSecure = url.startsWith("https://")
        with(binding.sslIcon) {
            setImageResource(if (isSecure) R.drawable.ic_lock else R.drawable.ic_lock_open)
            imageTintList = ContextCompat.getColorStateList(
                activity,
                if (isSecure) R.color.ssl_secure else R.color.ssl_insecure
            )
            contentDescription = activity.getString(
                if (isSecure) R.string.ssl_secure_status else R.string.ssl_not_secure_status
            )
        }
    }

    fun setUrlBarText(url: String) {
        val cleanUrl = url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
        binding.urlBar.setText(cleanUrl)
    }

    fun showProgress() {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
    }

    fun hideProgress() {
        binding.progressBar.visibility = View.GONE
    }

    fun finishSwipeRefresh() {
        binding.swipeRefresh.isRefreshing = false
    }

    fun processInput(input: String): String = when {
        input.startsWith("http://") || input.startsWith("https://") -> input
        input.contains(".") && !input.contains(" ") -> "https://$input"
        else -> "https://www.google.com/search?q=${Uri.encode(input)}"
    }
}
