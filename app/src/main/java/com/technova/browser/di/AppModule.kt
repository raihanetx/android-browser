package com.technova.browser.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.technova.browser.data.local.database.NovaBrowserDatabase
import com.technova.browser.data.local.dao.BookmarkDao
import com.technova.browser.data.local.dao.HistoryDao
import com.technova.browser.data.local.dao.TabDao
import com.technova.browser.data.repository.BookmarkRepository
import com.technova.browser.data.repository.BookmarkRepositoryImpl
import com.technova.browser.data.repository.HistoryRepository
import com.technova.browser.data.repository.HistoryRepositoryImpl
import com.technova.browser.data.repository.TabRepository
import com.technova.browser.data.repository.TabRepositoryImpl
import com.technova.browser.viewmodel.BrowserViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val AppModule = module {
    single { androidContext().getSharedPreferences("browser_prefs", Context.MODE_PRIVATE) }

    single { androidContext().dataStore }

    single { NovaBrowserDatabase.getDatabase(androidContext()) }

    single<BookmarkDao> { get<NovaBrowserDatabase>().bookmarkDao() }
    single<HistoryDao> { get<NovaBrowserDatabase>().historyDao() }
    single<TabDao> { get<NovaBrowserDatabase>().tabDao() }

    single<BookmarkRepository> { BookmarkRepositoryImpl(get()) }
    single<HistoryRepository> { HistoryRepositoryImpl(get()) }
    single<TabRepository> { TabRepositoryImpl(get()) }

    viewModel { BrowserViewModel(androidContext(), get()) }
}
