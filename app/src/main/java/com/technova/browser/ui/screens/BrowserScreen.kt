package com.technova.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.technova.browser.ui.components.BrowserToolbar
import com.technova.browser.ui.components.WebView
import com.technova.browser.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            BrowserToolbar(
                currentUrl = uiState.currentUrl,
                isPageLoading = uiState.isLoading,
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                onNavigateBack = { viewModel.goBack() },
                onNavigateForward = { viewModel.goForward() },
                onRefresh = { viewModel.reload() },
                onStopLoading = { viewModel.stopLoading() },
                onUrlChange = { viewModel.updateUrl(it) },
                onUrlSubmit = { url -> viewModel.loadUrl(url) },
                onMenuClick = onMenuClick
            )
        },
        contentWindowPadding = WindowInsets(0.dp).asPaddingValues()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            WebView(
                url = uiState.currentUrl,
                onProgressChanged = { progress ->
                    viewModel.updateProgress(progress)
                },
                onPageTitleChanged = { title ->
                    viewModel.updateTitle(title)
                },
                onUrlChanged = { url ->
                    viewModel.updateUrl(url)
                    viewModel.addToHistory(url, title = uiState.title)
                },
                onPageStarted = { url ->
                    viewModel.onPageStarted(url)
                },
                onPageFinished = { url ->
                    viewModel.onPageFinished(url)
                },
                onReceivedError = { request, error ->
                    viewModel.onWebViewError(request, error)
                },
                shouldOverrideUrlLoading = { webView, request ->
                    val url = request.url.toString()
                    viewModel.loadUrl(url)
                    false // Allow WebView to handle the URL
                }
            )

            // Error state
            if (uiState.error != null) {
                ErrorPage(
                    error = uiState.error,
                    onRetry = { viewModel.reload() },
                    onHome = { viewModel.goHome() }
                )
            }
        }
    }
}

@Composable
private fun ErrorPage(
    error: Throwable?,
    onRetry: () -> Unit,
    onHome: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to load page",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error?.localizedMessage ?: "Unknown error occurred",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
                OutlinedButton(onClick = onHome) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Home")
                }
            }
        }
    }
}
