package com.reliquary.app.di

import app.cash.sqldelight.db.SqlDriver
import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.db.ReliquaryDatabase
import com.reliquary.app.metadata.MetadataService
import com.reliquary.app.metadata.providers.GoogleBooksProvider
import com.reliquary.app.metadata.providers.MusicBrainzProvider
import com.reliquary.app.metadata.providers.OpenLibraryProvider
import com.reliquary.app.network.createHttpClient

/**
 * Lightweight manual DI. Each platform builds its own [SqlDriver] (Android needs
 * a Context, desktop uses a JDBC file) and hands it here; everything downstream
 * is shared common code.
 */
class AppContainer(driver: SqlDriver) {
    val database: ReliquaryDatabase = ReliquaryDatabase(driver)
    val repository: ReliquaryRepository = ReliquaryRepository(database)

    private val httpClient = createHttpClient()

    val metadataService: MetadataService = MetadataService(
        listOf(
            // Keyless providers — active out of the box.
            OpenLibraryProvider(httpClient),
            GoogleBooksProvider(httpClient),
            MusicBrainzProvider(httpClient),
            // Key-gated providers (TMDB, IGDB, ComicVine, Discogs) are added in
            // the next increment, activated from the Settings screen.
        ),
    )
}
