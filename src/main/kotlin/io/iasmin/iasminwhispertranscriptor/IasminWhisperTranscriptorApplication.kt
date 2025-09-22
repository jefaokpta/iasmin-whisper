package io.iasmin.iasminwhispertranscriptor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class IasminWhisperTranscriptorApplication

fun main(args: Array<String>) {
    runApplication<IasminWhisperTranscriptorApplication>(*args)
}
