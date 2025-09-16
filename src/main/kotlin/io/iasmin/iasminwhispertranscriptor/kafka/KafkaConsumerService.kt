package io.iasmin.iasminwhispertranscriptor.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.iasmin.iasminwhispertranscriptor.cdr.Cdr
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.TimeUnit

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
    private val pabxBaseUrl: String,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplateBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    @KafkaListener(id = "whisper-consumer", topics = ["transcriptions-test"], groupId = "iasmin-whisper-consumer")
    fun manageTask(@Payload message: String) {
        val cdr = jacksonObjectMapper().readValue(message, Cdr::class.java)
        logger.info(
            "Parsed CDR: id={}, uniqueId={}, callRecord={}, userfield={}, devInstance={}",
            cdr.id, cdr.uniqueId, cdr.callRecord, cdr.userfield, cdr.isDeveloperInstance
        )
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

    /**
     * Faz o download do arquivo de áudio informado no CDR a partir do PABX (IASMIN_PABX_URL)
     * e salva em `audios/<callRecord>`.
     * - Se o arquivo já existir localmente, não baixa novamente.
     * - Garante a criação do diretório `audios/`.
     * - Evita path traversal usando apenas o nome do arquivo (fileName).
     *
     * @return Path absoluto do arquivo salvo
     */
    private fun downloadAudio(cdr: Cdr): Path {
        val fileName = Paths.get(cdr.callRecord).fileName.toString()
        val targetDir = Paths.get("audios")
        Files.createDirectories(targetDir)
        val targetPath = targetDir.resolve(fileName)

        if (Files.exists(targetPath)) {
            logger.info("Áudio já existe localmente, pulando download: {}", targetPath.toAbsolutePath())
            return targetPath.toAbsolutePath()
        }

        val finalUrl = buildString {
            append(pabxBaseUrl.trimEnd('/'))
            append("/")
            append(fileName)
        }
        logger.info("Baixando áudio do PABX: {} -> {}", finalUrl, targetPath.toAbsolutePath())

        // Stream da resposta direto para o arquivo para evitar carregar tudo em memória
        restTemplate.execute(URI.create(finalUrl), HttpMethod.GET, null) { response ->
            val body = response.body
            Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { out ->
                    body.transferTo(out)
                }
            null
        }

        logger.info("Download concluído: {} ({} bytes)", targetPath.toAbsolutePath(), Files.size(targetPath))
        return targetPath.toAbsolutePath()
    }

    /**
     * Fluxo de transcrição (WIP): por enquanto apenas baixa o áudio necessário.
     */
    private fun transcriptAudio(cdr: Cdr) {
        TimeUnit.MINUTES.sleep(5)
        logger.info("Transcrição finalizada: {}", cdr.uniqueId)
//            val path = downloadAudio(cdr)
//            logger.info("Arquivo pronto para transcrição: {} (uniqueId={})", path, cdr.uniqueId)
        // Próximos passos (fora do escopo atual):
        // - Executar whisper com o comando configurado
        // - Ler JSON de saída e enviar para backend
    }
}