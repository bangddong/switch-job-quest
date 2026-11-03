package com.devquest.core.api.measurement

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 측정 전용 TCP 프록시(prod 코드 아님) - DB 쪽 네트워크가 "조용히 죽는" 상황을 로컬에서 재현한다.
 *
 * - [frozen]=false : 업스트림(실제 Postgres)으로 바이트를 그대로 전달한다.
 * - [frozen]=true  : 연결은 계속 accept 하고 열어 두지만 어느 방향으로도 바이트를 전달하지 않는다(버린다).
 *   RST/FIN 이 오지 않으므로 클라이언트에는 "응답이 영영 안 오는 서버"로 보인다 (파티션·VM 동결·보안그룹 DROP 의 근사).
 *   frozen 상태에서 새로 맺는 연결은 업스트림에 붙지도 않는다 - 즉 새 연결의 핸드셰이크도 응답이 없다.
 *
 * [upstream]=null 이면 처음부터 frozen 인 블랙홀 서버(accept 만 하고 아무것도 보내지 않는다)다.
 */
class FreezableProxy(private val upstream: Pair<String, Int>?, frozen: Boolean = upstream == null) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val sockets = CopyOnWriteArrayList<Socket>()

    @Volatile
    var frozen: Boolean = frozen

    val port: Int get() = server.localPort

    init {
        thread("proxy-accept") {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                sockets += client
                val target = upstream
                val up = if (!this.frozen && target != null) {
                    runCatching { Socket(target.first, target.second) }
                        .onFailure { System.err.println("[FreezableProxy] upstream 연결 실패 ${target}: $it") }
                        .getOrNull()?.also { sockets += it }
                } else {
                    null
                }
                pump(client, up)
                if (up != null) pump(up, client)
            }
        }
    }

    /** [from] 에서 읽어 [to] 로 전달한다. frozen 이거나 [to] 가 없으면 읽기만 하고 버린다. */
    private fun pump(from: Socket, to: Socket?) = thread("proxy-pump") {
        val buf = ByteArray(8192)
        try {
            val input = from.getInputStream()
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (!frozen && to != null) {
                    to.getOutputStream().write(buf, 0, n)
                    to.getOutputStream().flush()
                }
            }
        } catch (_: Exception) {
            // 소켓이 닫혔다 - 정리 중
        }
    }

    private fun thread(name: String, body: () -> Unit) {
        Thread(body, name).apply { isDaemon = true }.start()
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
    }
}
