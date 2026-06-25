package com.reliquary.app.network

import io.ktor.client.HttpClient

/** Each platform supplies its own Ktor engine (OkHttp on Android, CIO on desktop). */
expect fun createHttpClient(): HttpClient
