package com.ctjsoft.devops.api

import com.ctjsoft.devops.core.DevOpsException
import com.ctjsoft.devops.core.ErrorKind
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class TransportRequest(
    val method: String,
    val uri: URI,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutMillis: Long,
)

data class TransportResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
)

fun interface DevOpsTransport {
    fun execute(request: TransportRequest): TransportResponse
}

class JavaHttpDevOpsTransport(
    private val maxResponseBytes: Int = 5 * 1024 * 1024,
    private val logger: (String) -> Unit = {},
) : DevOpsTransport {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun execute(request: TransportRequest): TransportResponse {
        validateUri(request.uri)
        val startedAt = System.nanoTime()
        val path = request.uri.path
        logger("[http] start method=${request.method} path=$path timeoutMs=${request.timeoutMillis}")
        val builder = HttpRequest.newBuilder(request.uri)
            .timeout(Duration.ofMillis(request.timeoutMillis))
        request.headers.forEach(builder::header)
        val publisher = request.body?.let(HttpRequest.BodyPublishers::ofByteArray)
            ?: HttpRequest.BodyPublishers.noBody()
        builder.method(request.method, publisher)

        val response = try {
            client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        } catch (error: Exception) {
            logger(
                "[http] failure method=${request.method} path=$path " +
                    "elapsedMs=${elapsedMillis(startedAt)} error=${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
            throw DevOpsException("DevOps 网络请求失败：${error.message ?: error.javaClass.simpleName}", ErrorKind.NETWORK, cause = error)
        }
        if (response.body().size > maxResponseBytes) {
            logger(
                "[http] rejected method=${request.method} path=$path status=${response.statusCode()} " +
                    "bytes=${response.body().size} reason=response-too-large"
            )
            throw DevOpsException("DevOps 响应超过允许大小。", ErrorKind.PARSE)
        }
        logger(
            "[http] end method=${request.method} path=$path status=${response.statusCode()} " +
                "bytes=${response.body().size} elapsedMs=${elapsedMillis(startedAt)}"
        )
        return TransportResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map(),
            body = response.body().toString(Charsets.UTF_8),
        )
    }

    private fun validateUri(uri: URI) {
        if (uri.scheme != "https" || uri.host != DevOpsApi.BASE_HOST) {
            throw DevOpsException("拒绝访问未登记的 DevOps 地址。", ErrorKind.VALIDATION)
        }
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
