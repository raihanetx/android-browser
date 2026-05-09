package com.technova.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserToolbar(
    currentUrl: String,
    isPageLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onRefresh: () -> Unit,
    onStopLoading: () -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: (String) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var isVisible by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Format URL for display
    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotEmpty() && !isVisible) {
            urlText = currentUrl
        }
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Navigation buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back button
                IconButton(
                    onClick = onNavigateBack,
                    enabled = canGoBack && !isPageLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Forward button
                IconButton(
                    onClick = onNavigateForward,
                    enabled = canGoForward && !isPageLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Refresh/Stop button
                IconButton(
                    onClick = { if (isPageLoading) onStopLoading() else onRefresh() }
                ) {
                    Icon(
                        imageVector = if (isPageLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isPageLoading) "Stop" else "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // URL bar
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("Search or enter URL", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val query = urlText.trim()
                            if (query.isNotEmpty()) {
                                onUrlSubmit(query)
                                keyboardController?.hide()
                            }
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                // Menu button
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Progress bar
            AnimatedVisibility(
                visible = isPageLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
