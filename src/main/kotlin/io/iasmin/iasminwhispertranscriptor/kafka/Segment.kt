package io.iasmin.iasminwhispertranscriptor.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * @author Jefferson A. Reis (jefaokpta) < jefaokpta@hotmail.com >
 * Date: 9/19/25 1:07 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Segment(
    val id: Int,
    val text: String,
    val seek: Int,
    val start: String,
    val end: String,
    val callLeg: CallLegEnum?
) {
    companion object {
        /**
         * Converte o JSON de saída do Whisper (que contém um array "segments")
         * em uma lista de [Segment]. Campos não mapeados são ignorados.
         */
        fun fromWhisperJson(json: String, callLeg: CallLegEnum? = null): List<Segment> {
            val mapper = jacksonObjectMapper()
            val payload: WhisperResponse = mapper.readValue(json)
            val segments = payload.segments
            return segments.map { s ->
                Segment(
                    id = s.id,
                    text = s.text,
                    seek = s.seek,
                    start = s.start,
                    end = s.end,
                    callLeg = callLeg
                )
            }
        }
    }
}

/**
 * Estruturas mínimas para desserializar o JSON do Whisper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class WhisperResponse(
    val text: String? = null,
    val segments: List<Segment>,
    val language: String? = null
)
