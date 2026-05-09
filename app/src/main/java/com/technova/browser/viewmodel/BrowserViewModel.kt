package com.technova.browser.viewmodel

import android.app.Application
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.technova.browser.data.model.History
import com.technova.browser.data.model.SearchEngine
import com.technova.browser.data.repository.HistoryRepository
import com.technova.browser.util.WebViewError
import com.technova.browser.util.WebViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import java.net.URL

class BrowserViewModel(
    application: Application,
    private val historyRepository: HistoryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WebViewState())
    val uiState: StateFlow<WebViewState> = _uiState.asStateFlow()

    private val _searchEngine = MutableStateFlow(SearchEngine.GOOGLE)
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    init {
        // Load initial settings
        loadSettings()
    }

    private fun loadSettings() {
        // Load search engine preference
        // In a real app, this would come from DataStore or SharedPreferences
        viewModelScope.launch {
            // Load settings implementation
        }
    }

    fun loadUrl(url: String) {
        val processedUrl = processUrl(url)
        _uiState.update { it.copy(currentUrl = processedUrl, error = null) }
        Timber.d("Loading URL: $processedUrl")
    }

    fun updateUrl(url: String) {
        _uiState.update { it.copy(currentUrl = url) }
    }

    fun updateTitle(title: String?) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateProgress(progress: Int) {
        _uiState.update { it.copy(progress = progress) }
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                currentUrl = url,
                error = null
            )
        }
    }

    fun onPageFinished(url: String) {
        _uiState.update { it.copy(isLoading = false, progress = 100) }
    }

    fun onWebViewError(request: WebResourceRequest?, error: WebResourceError?) {
        val webViewError = WebViewError(request, error)
        _uiState.update { it.copy(error = webViewError, isLoading = false) }
        Timber.e("WebView error: $webViewError")
    }

    fun goBack() {
        if (_uiState.value.canGoBack) {
            // WebView.goBack() would be called from the composable
            Timber.d("Going back")
        }
    }

    fun goForward() {
        if (_uiState.value.canGoForward) {
            // WebView.goForward() would be called from the composable
            Timber.d("Going forward")
        }
    }

    fun reload() {
        _uiState.update { it.copy(error = null) }
        // WebView.reload() would be called from the composable
        Timber.d("Reloading page")
    }

    fun stopLoading() {
        _uiState.update { it.copy(isLoading = false) }
        // WebView.stopLoading() would be called from the composable
        Timber.d("Stopping page load")
    }

    fun goHome() {
        loadUrl("https://www.google.com")
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun addToHistory(url: String, title: String?) {
        if (url.isNotEmpty()) {
            viewModelScope.launch {
                val historyItem = History(
                    url = url,
                    title = title ?: url,
                    visitTime = Clock.System.now(),
                    visitDuration = 0L
                )
                historyRepository.addHistoryItem(historyItem)
            }
        }
    }

    fun setSearchEngine(searchEngine: SearchEngine) {
        _searchEngine.value = searchEngine
        // Save to preferences
        viewModelScope.launch {
            // Save to DataStore
        }
    }

    private fun processUrl(input: String): String {
        val trimmed = input.trim()

        // If it's already a valid URL, return as-is
        if (isValidUrl(trimmed)) {
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        // If it looks like a search query, use the search engine
        return _searchEngine.value.searchUrl.replace("%s", trimmed)
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(if (url.startsWith("http")) url else "https://$url")
            parsedUrl.host.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
