package io.iasmin.iasminwhispertranscriptor.cdr

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
class KafkaController(private val whisperService: WhisperService) {

    private val log = LoggerFactory.getLogger(KafkaController::class.java)

    @KafkaListener(topics = ["transcriptions-test"], groupId = "iasmin-whisper-consumer")
    fun onCdr(@Payload message: String) {
            val cdr = jacksonObjectMapper().readValue(message, Cdr::class.java)
            log.info(
                "Parsed CDR: id={}, uniqueId={}, callRecord={}, userfield={}, devInstance={}",
                cdr.id, cdr.uniqueId, cdr.callRecord, cdr.userfield, cdr.isDeveloperInstance
            )
            whisperService.manageTask(cdr)
    }
}
