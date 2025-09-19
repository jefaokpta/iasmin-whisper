package io.iasmin.iasminwhispertranscriptor.kafka

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.iasmin.iasminwhispertranscriptor.cdr.Cdr
import io.iasmin.iasminwhispertranscriptor.cdr.UserfieldEnum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.net.InetSocketAddress
import kotlin.test.assertFalse

class KafkaConsumerServiceHasTranscriptionTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `ja existe transcricao returns OK when backend responds 200`() {
        val uniqueId = "abc123"
        val backendUrl = startBackend(uniqueId, 200)
        val service = KafkaConsumerService(
            IASMIN_PABX_URL = "http://example",
            IASMIN_BACKEND_URL = backendUrl,
            kafkaListenerEndpointRegistry = Mockito.mock(KafkaListenerEndpointRegistry::class.java)
        )
        val cdr = Cdr(id = 1, uniqueId = uniqueId, callRecord = "file.mp3", userfield = UserfieldEnum.OUTBOUND)

        val status = invokeHasTranscription(service, cdr)

        assertEquals(true, status)
    }

    @Test
    fun `nao existe transcricao throws HttpClientErrorException when backend responds 404`() {
        val uniqueId = "def456"
        val backendUrl = startBackend(uniqueId, 404)
        val service = KafkaConsumerService(
            IASMIN_PABX_URL = "http://example",
            IASMIN_BACKEND_URL = backendUrl,
            kafkaListenerEndpointRegistry = Mockito.mock(KafkaListenerEndpointRegistry::class.java)
        )
        val cdr = Cdr(id = 2, uniqueId = uniqueId, callRecord = "file2.mp3", userfield = UserfieldEnum.INBOUND)

        val status = invokeHasTranscription(service, cdr)
        assertFalse(status)
    }

    private fun invokeHasTranscription(service: KafkaConsumerService, cdr: Cdr): Boolean {
        val method = KafkaConsumerService::class.java.getDeclaredMethod("hasTranscription", Cdr::class.java)
        method.isAccessible = true
        return method.invoke(service, cdr) as Boolean
    }

    private fun startBackend(uniqueId: String, statusCode: Int): String {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/recognitions/$uniqueId", HttpHandler { exchange: HttpExchange ->
            handleRecognition(exchange, statusCode)
        })
        server.start()
        this.server = server
        val port = server.address.port
        return "http://localhost:$port"
    }

    private fun handleRecognition(exchange: HttpExchange, statusCode: Int) {
        try {
            if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                exchange.responseHeaders.add("Content-Type", "application/json")
                // -1 means no response body
                exchange.sendResponseHeaders(statusCode, -1)
            } else {
                exchange.sendResponseHeaders(405, -1)
            }
        } finally {
            exchange.close()
        }
    }
}
