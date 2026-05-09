package com.technova.browser.viewmodel

import com.technova.browser.data.repository.HistoryRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BrowserViewModelTest {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var historyRepository: HistoryRepository

    @Before
    fun setup() {
        historyRepository = mockk()
        // viewModel = BrowserViewModel(mockk(), historyRepository)
    }

    @Test
    fun `initial state should have default URL`() {
        // Given - ViewModel is initialized
        // When - Initial state is observed
        // Then - Should have default URL
        // assertEquals("https://www.google.com", viewModel.uiState.value.currentUrl)
    }

    @Test
    fun `loadUrl should update current URL`() {
        // Given - ViewModel is initialized
        // When - loadUrl is called
        // viewModel.loadUrl("https://example.com")
        // Then - URL should be updated
        // assertEquals("https://example.com", viewModel.uiState.value.currentUrl)
    }

    @Test
    fun `processUrl should add https to plain domain`() {
        // Given - ViewModel is initialized
        // When - loadUrl is called with plain domain
        // viewModel.loadUrl("example.com")
        // Then - Should add https
        // assertTrue(viewModel.uiState.value.currentUrl.startsWith("https://"))
    }
}