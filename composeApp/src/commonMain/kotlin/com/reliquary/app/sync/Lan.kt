package com.reliquary.app.sync

/**
 * Direct device-to-device sync over the local network, layered on the same
 * [SyncBundle] used by file sync. Transport is a tiny length-prefixed TCP
 * protocol (no extra dependencies):
 *
 *   client → server : [int len][our bundle bytes]
 *   server → client : [int len][their bundle bytes]
 *
 * The server merges what it receives and replies with its own full library;
 * the client then merges that. Both devices end up reconciled in one round.
 */
const val LAN_SYNC_PORT: Int = 8765

/** Accepts incoming connections; [onReceive] merges the peer's bundle and returns ours. */
expect class LanSyncServer(onReceive: (String) -> String) {
    val isRunning: Boolean
    fun start(port: Int)
    fun stop()
}

/** Connects to a hosting peer, sends our bundle, and returns the peer's bundle. */
expect class LanSyncClient() {
    suspend fun exchange(host: String, port: Int, ourBundle: String): String
}

/** Site-local IPv4 addresses of this device, for display so a peer can connect. */
expect fun localIpAddresses(): List<String>

/** Coordinates hosting and connecting using a [SyncService]. */
class LanSyncManager(private val syncService: SyncService) {
    private val server = LanSyncServer(onReceive = { incoming ->
        // Merge what the peer sent, then hand back our whole library.
        syncService.importJson(incoming)
        syncService.exportJson()
    })

    private val client = LanSyncClient()

    val isHosting: Boolean get() = server.isRunning
    fun startHosting(port: Int = LAN_SYNC_PORT) = server.start(port)
    fun stopHosting() = server.stop()

    /** Connect to a hosting peer and two-way merge. Returns what we accepted. */
    suspend fun connectAndSync(host: String, port: Int = LAN_SYNC_PORT): SyncResult {
        val ours = syncService.exportJson()
        val theirs = client.exchange(host.trim(), port, ours)
        return syncService.importJson(theirs)
    }
}
