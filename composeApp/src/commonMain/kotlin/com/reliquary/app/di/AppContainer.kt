package com.reliquary.app.di

import app.cash.sqldelight.db.SqlDriver
import com.reliquary.app.csv.CsvService
import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.db.ReliquaryDatabase
import com.reliquary.app.images.CoverCache
import com.reliquary.app.integrations.LetterboxdImporter
import com.reliquary.app.integrations.SimklImporter
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.DiscoverService
import com.reliquary.app.metadata.MetadataService
import com.reliquary.app.metadata.UpcLookup
import com.reliquary.app.metadata.providers.ComicVineProvider
import com.reliquary.app.metadata.providers.DiscogsProvider
import com.reliquary.app.metadata.providers.GoogleBooksProvider
import com.reliquary.app.metadata.providers.IgdbProvider
import com.reliquary.app.metadata.providers.MusicBrainzProvider
import com.reliquary.app.metadata.providers.OmdbProvider
import com.reliquary.app.metadata.providers.OpenLibraryProvider
import com.reliquary.app.metadata.providers.SimklProvider
import com.reliquary.app.metadata.providers.TmdbProvider
import com.reliquary.app.domain.MediaType
import com.reliquary.app.network.createHttpClient
import com.reliquary.app.sync.LanSyncManager
import com.reliquary.app.sync.SyncService

/**
 * Lightweight manual DI. Each platform builds its own [SqlDriver] (Android needs
 * a Context, desktop uses a JDBC file) and hands it here; everything downstream
 * is shared common code.
 */
class AppContainer(driver: SqlDriver) {
    val database: ReliquaryDatabase = ReliquaryDatabase(driver)
    val repository: ReliquaryRepository = ReliquaryRepository(database)

    val httpClient = createHttpClient()
    val apiKeyStore = ApiKeyStore(repository)
    val syncService = SyncService(repository)
    val lanSync = LanSyncManager(syncService)
    val coverCache = CoverCache(httpClient)
    val updateService = com.reliquary.app.update.UpdateService(httpClient)
    val mediaServerService = com.reliquary.app.servers.MediaServerService(httpClient, repository)
    val discoverService = DiscoverService(httpClient, apiKeyStore)
    val csvService = CsvService(repository)
    val letterboxdImporter = LetterboxdImporter(httpClient, repository)
    val simklImporter = SimklImporter(httpClient, apiKeyStore, repository)

    val metadataService: MetadataService = MetadataService(
        listOf(
            // Keyless providers — active out of the box.
            OpenLibraryProvider(httpClient),
            GoogleBooksProvider(httpClient),
            MusicBrainzProvider(httpClient),
            // Key-gated providers — activate once a key is saved in Settings.
            TmdbProvider(httpClient, apiKeyStore),
            OmdbProvider(httpClient, apiKeyStore),
            DiscogsProvider(httpClient, apiKeyStore),
            ComicVineProvider(httpClient, apiKeyStore),
            IgdbProvider(httpClient, apiKeyStore),
            SimklProvider(httpClient, apiKeyStore, MediaType.TV, "tv"),
            SimklProvider(httpClient, apiKeyStore, MediaType.ANIME, "anime"),
        ),
        upcLookup = UpcLookup(httpClient),
    )
}
