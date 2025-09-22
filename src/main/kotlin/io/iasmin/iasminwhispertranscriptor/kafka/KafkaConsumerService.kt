package io.iasmin.iasminwhispertranscriptor.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.iasmin.iasminwhispertranscriptor.cdr.Cdr
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Serviço responsável por interagir com o Kafka e utilidades correlatas.
 * Por ora, disponibiliza a função de baixar o áudio do PABX para a pasta local `audios/`.
 *
 * Autor: Jefferson A. Reis (jefaokpta)
 * Data: 15/09/2025
 */
@Service
class KafkaConsumerService(
    @param:Value("\${iasmin.pabx.url}")
    private val IASMIN_PABX_URL: String,
    @param:Value("\${iasmin.backend.url}")
    private val IASMIN_BACKEND_URL: String,
    @param:Value("\${whisper.command}")
    private val WHISPER_COMMAND: String,
    @param:Value("\${whisper.audios}")
    private val WHISPER_AUDIOS: String,
    @param:Value("\${whisper.transcripts}")
    private val WHISPER_TRANSCRIPTS: String,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplateBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .readTimeout(Duration.ofSeconds(8))
        .build()
    data class Recognition(val cdrId: Int, val segments: List<Segment>)

    @KafkaListener(id = "whisper-consumer", topics = ["\${kafka.topic}"], groupId = "iasmin-whisper-consumer")
    fun manageTask(@Payload message: String) {
        val cdr = jacksonObjectMapper().readValue(message, Cdr::class.java)
        logger.info(
            "Parsed CDR: id={}, uniqueId={}, callRecord={}, userfield={}, devInstance={}",
            cdr.id, cdr.uniqueId, cdr.callRecord, cdr.userfield, cdr.isDeveloperInstance
        )
        if (hasTranscription(cdr)) {
            logger.info("CDR ${cdr.uniqueId} ja tem transcricao, ignorando...")
            return
        }
        val container = kafkaListenerEndpointRegistry.getListenerContainer("whisper-consumer")!!
        try {
            container.pause()
            transcriptAudio(cdr)
        } catch (e: Exception) {
            logger.error("Falha ao processar mensagem do Kafka ${cdr.uniqueId}", e)
        } finally {
            container.resume()
        }
    }

    private fun transcriptAudio(cdr: Cdr) {
        val audioNameA = cdr.uniqueId.replace(".", "-").plus("-a.sln")
        val audioNameB = cdr.uniqueId.replace(".", "-").plus("-b.sln")
        downloadAudio(audioNameA)
        downloadAudio(audioNameB)
        whisper(audioNameA)
        whisper(audioNameB)
        readTranscriptions(cdr)
        clearAudioData(audioNameA, audioNameB)
        logger.info("Transcrição finalizada: {}", cdr.uniqueId)
    }

    private fun readTranscriptions(cdr: Cdr) {
        val transcriptionA = cdr.uniqueId.replace(".", "-").plus("-a.json")
        val transcriptionB = cdr.uniqueId.replace(".", "-").plus("-b.json")
        val transcriptionAPath = Paths.get("$WHISPER_TRANSCRIPTS/$transcriptionA")
        val transcriptionBPath = Paths.get("$WHISPER_TRANSCRIPTS/$transcriptionB")
        val jsonA = Files.readString(transcriptionAPath)
        val jsonB = Files.readString(transcriptionBPath)
        val segmentsA = Segment.fromWhisperJson(jsonA, CallLegEnum.A)
        val segmentsB = Segment.fromWhisperJson(jsonB, CallLegEnum.B)
        logger.info("Transcrição lida: {}", cdr.uniqueId)
        logger.info("Segmentos A: {}", segmentsA.size)
        logger.info("Segmentos B: {}", segmentsB.size)
        notifyTranscriptsToBackend(cdr, segmentsA + segmentsB)
    }

    private fun notifyTranscriptsToBackend(cdr: Cdr, segments: List<Segment>) {
        try {
            val request = RequestEntity.post(URI("$IASMIN_BACKEND_URL/recognitions"))
                .body(Recognition(cdr.id, segments))
            restTemplate.exchange(request, Void::class.java)
        } catch (e: ResourceAccessException) {
            logger.error("Timeout ao notificar transcrições para o backend: {}", e.message)
        }
    }

    private fun clearAudioData(audioNameA: String, audioNameB: String) {
        val task = Executors.newVirtualThreadPerTaskExecutor()
        task.submit {
            try {
                Files.deleteIfExists(Paths.get("$WHISPER_AUDIOS/$audioNameA"))
                Files.deleteIfExists(Paths.get("$WHISPER_AUDIOS/$audioNameB"))
                Files.deleteIfExists(Paths.get("$WHISPER_TRANSCRIPTS/$audioNameA"))
                Files.deleteIfExists(Paths.get("$WHISPER_TRANSCRIPTS/$audioNameB"))
            } catch (e: IOException) {
                logger.error("Erro ao apagar arquivos de transcrição: {}", e.message)
            }
        }
        task.shutdown()
    }

    private fun hasTranscription(cdr: Cdr): Boolean {
        try {
            val request = RequestEntity.get(URI("$IASMIN_BACKEND_URL/recognitions/${cdr.uniqueId}")).build()
            val response = restTemplate.exchange(request, Void::class.java)
            return response.statusCode == HttpStatus.OK
        } catch (_: HttpClientErrorException) {
            return false
        }
    }

    private fun whisper(audioName: String) {
        val command = WHISPER_COMMAND.split(" ").toTypedArray().toMutableList()
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
            .start()
            .waitFor()
    }

    /**
     * Faz o download do arquivo de áudio informado no CDR a partir do PABX (IASMIN_PABX_URL)
     * e salva em `audios/<callRecord>`.
     * - Se o arquivo já existir localmente, não baixa novamente.
     * - Garante a criação do diretório `audios/`.
     * - Evita path traversal usando apenas o nome do arquivo (fileName).
     *
     * @return Path absoluto do arquivo salvo
     */
    private fun downloadAudio(audioName: String) {
        val audioDirectory = Paths.get(WHISPER_AUDIOS)
        Files.createDirectories(audioDirectory)
        val audioFilePath = audioDirectory.resolve(audioName)

        val fullAudioUrl = buildString {
            append(IASMIN_PABX_URL.trim())
            append("/")
            append(audioName)
        }
        logger.info("Baixando áudio do PABX: {} -> {}", fullAudioUrl, audioFilePath.toAbsolutePath())
        val customRestTemplate = RestTemplateBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(300))
            .build()
        // Stream da resposta direto para o arquivo para evitar carregar tudo em memória
        customRestTemplate.execute(URI.create(fullAudioUrl), HttpMethod.GET, null) { response ->
            val body = response.body
            Files.newOutputStream(audioFilePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { out ->
                    body.transferTo(out)
                }
            null
        }
        logger.info("Download concluído: {} ({} bytes)", audioFilePath.toAbsolutePath(), Files.size(audioFilePath))
    }


}