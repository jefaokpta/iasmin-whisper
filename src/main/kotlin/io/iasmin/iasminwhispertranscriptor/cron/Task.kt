package io.iasmin.iasminwhispertranscriptor.cron

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

/**
 * Tarefas agendadas.
 * - Limpeza diária (04:00) dos áudios baixados e transcrições geradas com mais de 48 horas.
 *
 * Autor: Jefferson A. Reis (jefaokpta)
 * Data: 22/09/2025
 */
@Component
class Task(
    @param:Value("\${whisper.audios}")
    private val whisperAudios: String,
    @param:Value("\${whisper.transcripts}")
    private val whisperTranscripts: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Executa todos os dias às 04:00 no fuso de São Paulo
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Sao_Paulo")
    fun cleanupOldArtifacts() {
        val now = Instant.now()
        val plus48h = Duration.ofHours(48)
        val audioDir = Paths.get(whisperAudios)
        val transcriptDir = Paths.get(whisperTranscripts)

        logger.info("Iniciando limpeza diária de áudios e transcrições antigas (>48h)")
        var deletedCount = 0
        deletedCount += deleteOlderThan(audioDir, now, plus48h)
        deletedCount += deleteOlderThan(transcriptDir, now, plus48h)
        logger.info("Limpeza concluída. Arquivos deletados: {}", deletedCount)
    }

    private fun deleteOlderThan(dir: Path, now: Instant, plus48h: Duration): Int {
        if (!Files.exists(dir)) {
            logger.debug("Diretório não existe, ignorando: {}", dir.toAbsolutePath())
            return 0
        }
        var count = 0
        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (p in stream) {
                    try {
                        if (Files.isRegularFile(p)) {
                            val lastModified = Files.getLastModifiedTime(p).toInstant()
                            if (Duration.between(lastModified, now) > plus48h) {
                                Files.deleteIfExists(p)
                                count++
                                logger.debug("Removido: {} (modificado em {})", p.toAbsolutePath(), lastModified)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Falha ao processar caminho {}: {}", p.toAbsolutePath(), e.message)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Falha ao varrer diretório {}: {}", dir.toAbsolutePath(), e.message)
        }
        return count
    }
}