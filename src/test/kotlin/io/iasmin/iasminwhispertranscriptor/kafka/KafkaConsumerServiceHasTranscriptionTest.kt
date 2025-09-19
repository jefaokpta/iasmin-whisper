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
    "text": " Al\u00f4. Marivaldo? Isso. \u00c9 o L\u00facio da VIP, tudo bem? Tudo e voc\u00ea? Bem, querido, gra\u00e7as a Deus. Eu recebi seu chamado aqui sobre a quest\u00e3o do VIP, do WhatsApp. A quest\u00e3o de copiar e colar as mensagens. Voc\u00ea falou que teria alguma alternativa, n\u00e9? Isso. T\u00e1. Voc\u00ea fala, essa ferramenta de voc\u00eas \u00e9 algum sistema ou \u00e9 algum documento? N\u00e3o, n\u00e3o. \u00c9 um sistema. \u00c9 o Gira. Gira? Isso. N\u00f3s utilizamos o Gira como ferramenta de Service Desk, t\u00e1? E cada atendimento que o nosso pessoal executa, eles pegam a conversa que teve com o chat e anexam nesse Gira. hoje, hoje n\u00f3s usamos um chat que t\u00e1 embutido no nosso RP, mas que se o VIP atender as nossas expectativas, n\u00f3s vamos acabar desativando esse chat. Certo. E hoje, esse chat, ele t\u00e1 integrado com o Gira automaticamente. Quando a gente encerra o chat, ele j\u00e1 envia um e-mail com o conte\u00fado daquela conversa pra dentro do Gira, onde o Gira consegue capturar esse e-mail e transcrever tudo aquilo que foi escrito, n\u00e9? no chat, pra dentro do Gira, entendeu? Certo. Entendi, sim. T\u00e1. \u00c9 isso que eu queria saber dessas informa\u00e7\u00f5es, que o pessoal do desenvolvimento pediu pra mim passar pra eles, pra eles poderem analisar e ver se voc\u00ea consegue pra voc\u00ea. Entendi. Nessa parte, nessa parte aqui, qual que \u00e9 o servi\u00e7o mesmo? Desculpa, vou me anotar aqui, o que voc\u00ea faz pra mandar as mensagens? \u00c9, do chat hoje? \u00c9. \u00c9. Quando ele encerra, ele envia um e-mail, ele faz uma integra\u00e7\u00e3o, ele faz um envio do e-mail pra dentro do Gira. T\u00e1, do Gira. \u00c9. Isso. Ent\u00e3o, esse servi\u00e7o que voc\u00ea manda \u00e9 pro Gira, n\u00e9? Isso, isso. \u00c9 o nosso Service Desk, o Gira Service Desk. T\u00e1. Ent\u00e3o, voc\u00ea manda pra esse servi\u00e7o do Gira. \u00c9. Voc\u00eas tem alguma API de comunica\u00e7\u00e3o com a ferramenta do IP, ou n\u00e3o? Ent\u00e3o, essa parte que eu preciso ver com o desenvolvimento, se d\u00e1 pra integrar, n\u00e9, ou vincular alguma API pra fazer essa sincronia pro envio, n\u00e9, da mensagem, n\u00e9? Isso, isso. Exatamente, exatamente. \u00c9, eu vou ver com eles, deixa eu s\u00f3 anotar aqui. Ent\u00e3o, eles precisam sincronizar o IP com esse Gira do servi\u00e7o de voc\u00eas, n\u00e9? Exatamente. Imagina que fechando o protocolo l\u00e1 do atendimento no IP, n\u00e9, esse fechamento do protocolo seria o gatilho pra poder enviar essa conversa que teve pra dentro do Gira. T\u00e1. Eu vou ver com eles se tem a possibilidade de sincronia ou se precisa de alguma informa\u00e7\u00e3o, porque \u00e9 o pessoal do desenvolvimento que faz essa, n\u00e9, essa parte de, n\u00e9, de configura\u00e7\u00e3o, de c\u00f3digo, de servidor. Ent\u00e3o, eu vou passar as informa\u00e7\u00f5es pra eles e o que o pessoal do desenvolvimento me posicionar, eu te posiciono e a\u00ed a gente v\u00ea se precisa de alguma informa\u00e7\u00e3o sua ou se vai precisar de alguma coisa mais al\u00e9m a\u00ed, t\u00e1? Perfeito. Se precisar ter uma conversa um pouco mais t\u00e9cnica com o pessoal do desenvolvimento pra gente poder mostrar como que \u00e9 hoje, t\u00f4 \u00e0 disposi\u00e7\u00e3o, t\u00e1? T\u00e1 bom. Eu vou falar com ele aqui, com o Jefferson, que \u00e9 o desenvolvedor. Se ele me pedir alguma informa\u00e7\u00e3o, eu vejo com voc\u00ea e se precisar marcar alguma coisa, eu falo com ele aqui tamb\u00e9m. Maravilha. Obrigado, viu, Marivaldo? Eu que agrade\u00e7o. Por enquanto, t\u00f4 deixando a chamada em aberto e t\u00f4 em an\u00e1lise aqui com ele. T\u00e1 bom, muito obrigado, n\u00e9? Obrigado, querido. Tchau, tchau. Tchau, tchau.",
    "segments": [
        {
            "id": 0,
            "seek": 0,
            "start": 0.9399999999999995,
            "end": 1.5,
            "text": " Al\u00f4.",
            "tokens": [
                50365,
                967,
                2851,
                13,
                50465
            ],
            "temperature": 0.0,
            "avg_logprob": -0.2075467951157514,
            "compression_ratio": 1.446808510638298,
            "no_speech_prob": 1.3259178477387223e-11,
            "words": [
                {
                    "word": " Al\u00f4.",
                    "start": 0.9399999999999995,
                    "end": 1.5,
                    "probability": 0.8412885069847107
                }
            ]
        },
        {
            "id": 1,
            "seek": 0,
            "start": 1.98,
            "end": 2.54,
            "text": " Marivaldo?",
            "tokens": [
                50465,
                2039,
                3576,
                2595,
                30,
                50515
            ],
            "temperature": 0.0,
            "avg_logprob": -0.2075467951157514,
            "compression_ratio": 1.446808510638298,
            "no_speech_prob": 1.3259178477387223e-11,
            "words": [
                {
                    "word": " Marivaldo?",
                    "start": 1.98,
                    "end": 2.54,
                    "probability": 0.8436808188756307
                }
            ]
        }
     ]
     }
        """.trimIndent()

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
