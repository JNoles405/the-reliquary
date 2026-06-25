package com.reliquary.app.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class LanSyncServer actual constructor(private val onReceive: (String) -> String) {
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false
    actual val isRunning: Boolean get() = running

    actual fun start(port: Int) {
        if (running) return
        val socket = ServerSocket(port)
        serverSocket = socket
        running = true
        thread = Thread {
            while (running) {
                try {
                    handle(socket.accept())
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun handle(socket: Socket) {
        socket.use {
            val input = DataInputStream(it.getInputStream().buffered())
            val output = DataOutputStream(it.getOutputStream().buffered())
            val length = input.readInt()
            val buffer = ByteArray(length)
            input.readFully(buffer)
            val reply = onReceive(buffer.decodeToString()).encodeToByteArray()
            output.writeInt(reply.size)
            output.write(reply)
            output.flush()
        }
    }

    actual fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}

actual class LanSyncClient actual constructor() {
    actual suspend fun exchange(host: String, port: Int, ourBundle: String): String =
        withContext(Dispatchers.IO) {
            Socket(host, port).use { socket ->
                socket.soTimeout = 30_000
                val output = DataOutputStream(socket.getOutputStream().buffered())
                val input = DataInputStream(socket.getInputStream().buffered())
                val payload = ourBundle.encodeToByteArray()
                output.writeInt(payload.size)
                output.write(payload)
                output.flush()
                val length = input.readInt()
                val buffer = ByteArray(length)
                input.readFully(buffer)
                buffer.decodeToString()
            }
        }
}

actual fun localIpAddresses(): List<String> = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { Collections.list(it.inetAddresses) }
        .filter { it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
        .filter { it.contains('.') }
        .distinct()
}.getOrDefault(emptyList())
