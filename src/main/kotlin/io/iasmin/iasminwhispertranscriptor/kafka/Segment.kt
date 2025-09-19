package io.iasmin.iasminwhispertranscriptor.kafka

/**
 * @author Jefferson A. Reis (jefaokpta) < jefaokpta@hotmail.com >
 * Date: 9/19/25 1:07 PM
 */
class Segment(
    val id: Int,
    val segmentId: Int,
    val text: String,
    val seek: Int,
    val startSecond: Int,
    val endSecond: Int,
    val callLeg: CallLegEnum?
) {
}
