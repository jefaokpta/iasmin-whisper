package io.iasmin.iasminwhispertranscriptor.cdr

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration

/**
 * Serviço responsável por interagir com o Whisper e utilidades correlatas.
 * Por ora, disponibiliza a função de baixar o áudio do PABX para a pasta local `audios/`.
 *
 * Autor: Jefferson A. Reis (jefaokpta)
 * Data: 15/09/2025
 */
@Service
class WhisperService(
    @param:Value("\${iasmin.pabx.url}")
    private val pabxBaseUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) {

    private val logger = LoggerFactory.getLogger(WhisperService::class.java)
    private var isBusy = false
    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()


    fun manageTask(cdr: Cdr) {
        if (isBusy) {
            logger.warn("Serviço ocupado, aguardando liberação para processar CDR: {}", cdr.uniqueId)
            return
        }
        isBusy = true
        transcriptAudio(cdr)
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
        try {
            val path = downloadAudio(cdr)
            logger.info("Arquivo pronto para transcrição: {} (uniqueId={})", path, cdr.uniqueId)
            // Próximos passos (fora do escopo atual):
            // - Executar whisper com o comando configurado
            // - Ler JSON de saída e enviar para backend
        } catch (ex: Exception) {
            logger.error("Falha ao preparar áudio para transcrição (uniqueId=${cdr.uniqueId}): ${ex.message}", ex)
            isBusy = false
        }
    }
}