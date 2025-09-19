package io.iasmin.iasminwhispertranscriptor.kafka

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.iasmin.iasminwhispertranscriptor.cdr.Cdr
import io.iasmin.iasminwhispertranscriptor.cdr.UserfieldEnum
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.net.InetSocketAddress
import java.nio.file.Paths
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
            WHISPER_COMMAND = "whisper",
            kafkaListenerEndpointRegistry = Mockito.mock(KafkaListenerEndpointRegistry::class.java),
            WHISPER_AUDIOS = "whisper/audios",
            WHISPER_TRANSCRIPTS = "whisper/transcripts"
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
            WHISPER_COMMAND = "whisper",
            kafkaListenerEndpointRegistry = Mockito.mock(KafkaListenerEndpointRegistry::class.java),
            WHISPER_AUDIOS = "whisper/audios",
            WHISPER_TRANSCRIPTS = "whisper/transcripts"
        )
        val cdr = Cdr(id = 2, uniqueId = uniqueId, callRecord = "file2.mp3", userfield = UserfieldEnum.INBOUND)

        val status = invokeHasTranscription(service, cdr)
        assertFalse(status)
    }

    @Test
    fun `transformar json de segmentos em objeto`() {
        val json = """
            {
    "text": " Al\u00f4. Marivaldo?",
    "segments": [
        {
            "id": 0,
            "seek": 0,
            "start": 0.9399999999999995,
            "end": 1.5,
            "text": " Al\u00f4.",
            "tokens": [50365, 967, 2851, 13, 50465],
            "temperature": 0.0,
            "avg_logprob": -0.2075467951157514,
            "compression_ratio": 1.446808510638298,
            "no_speech_prob": 1.3259178477387223e-11,
            "words": [
                {"word": " Al\u00f4.", "start": 0.9399999999999995, "end": 1.5, "probability": 0.8412885069847107}
            ]
        },
        {
            "id": 1,
            "seek": 0,
            "start": 1.98,
            "end": 2.54,
            "text": " Marivaldo?",
            "tokens": [50465, 2039, 3576, 2595, 30, 50515],
            "temperature": 0.0,
            "avg_logprob": -0.2075467951157514,
            "compression_ratio": 1.446808510638298,
            "no_speech_prob": 1.3259178477387223e-11,
            "words": [
                {"word": " Marivaldo?", "start": 1.98, "end": 2.54, "probability": 0.8436808188756307}
            ]
        }
     ]
     }
        """.trimIndent()

        val segments = Segment.fromWhisperJson(json)

        assertEquals(2, segments.size)
        assertEquals(0, segments[0].id)
        assertEquals(" Alô.", segments[0].text)
        assertEquals("0", segments[0].seek.toString())
        assertEquals("0.9399999999999995", segments[0].start)
        assertEquals("1.5", segments[0].end)
        assertEquals(null, segments[0].callLeg)

        assertEquals(1, segments[1].id)
        assertEquals(" Marivaldo?", segments[1].text)
    }

    @Disabled
    @Test
    fun `executar docker compose`() {
        val WHISPER_COMMAND = "docker compose run --rm whisper whisper"
        val audioName = "test.sln"
        val command = WHISPER_COMMAND.split(" ").toTypedArray().toMutableList()
//        command.add("-h")
        val params = listOf(
            "audios/$audioName",
            "--model=turbo",
            "--fp16=False",
            "--language=pt",
            "--beam_size=5",
            "--patience=2",
            "--word_timestamps=True",
            "--hallucination_silence_threshold=3",
            "--output_format=json",
            "--output_dir=transcripts"
        )
        command.addAll(params)
        ProcessBuilder(command)
            .directory(Paths.get("whisper").toFile())
            .inheritIO() // opcional: para ver a saída no console
            .start()
            .waitFor()
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
