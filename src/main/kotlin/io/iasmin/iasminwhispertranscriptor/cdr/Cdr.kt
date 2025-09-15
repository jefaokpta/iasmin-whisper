package io.iasmin.iasminwhispertranscriptor.cdr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Cdr(
    val id: Int,
    val uniqueId: String,
    val callRecord: String,
    val userfield: UserfieldEnum,
    val isDeveloperInstance: Boolean = false
)