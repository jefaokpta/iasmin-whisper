package io.iasmin.iasminwhispertranscriptor.kafka

/**
 * @author Jefferson A. Reis (jefaokpta) < jefaokpta@hotmail.com >
 * Date: 9/19/25 1:07 PM
 */
class Segment(
    val id: Int,
    val text: String,
    val seek: Int,
    val start: String,
    val end: String,
    val callLeg: CallLegEnum?
) {
}
