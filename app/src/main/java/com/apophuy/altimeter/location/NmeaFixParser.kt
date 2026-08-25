package com.apophuy.altimeter.location

import java.util.Locale

internal data class NmeaFixEvidence(
    val sentenceType: String,
    val talker: String,
    val hasFix: Boolean,
    val usedSatellites: Int?,
)

internal object NmeaFixParser {
    /**
     * Reads only fix metadata. Coordinates and raw NMEA text are deliberately not retained so the
     * same value can be written to a user-shareable diagnostic log without exposing a location.
     */
    fun inspect(sentence: String): NmeaFixEvidence? {
        val payload = sentence.trim().substringBefore('*')
        val fields = payload.split(',')
        val header = fields.firstOrNull()?.removePrefix("$") ?: return null
        if (header.length < 5) return null
        val sentenceType = header.takeLast(3)
        val talker = header.dropLast(3)

        return when (sentenceType) {
            "GGA" -> inspectGga(fields, sentenceType, talker)
            "GNS" -> inspectGns(fields, sentenceType, talker)
            "GSA" -> inspectGsa(fields, sentenceType, talker)
            "RMC" -> inspectRmc(fields, sentenceType, talker)
            else -> null
        }
    }

    private fun inspectGga(fields: List<String>, type: String, talker: String): NmeaFixEvidence? {
        if (fields.size < 8) return null
        val quality = fields[6].toIntOrNull() ?: return null
        val used = fields[7].toIntOrNull()?.takeIf { it > 0 }
        return NmeaFixEvidence(type, talker, hasFix = quality > 0, usedSatellites = used)
    }

    private fun inspectGns(fields: List<String>, type: String, talker: String): NmeaFixEvidence? {
        if (fields.size < 8) return null
        val mode = fields[6].uppercase(Locale.ROOT)
        val used = fields[7].toIntOrNull()?.takeIf { it > 0 }
        return NmeaFixEvidence(
            type,
            talker,
            hasFix = mode.any { it in GNSS_FIX_MODES },
            usedSatellites = used,
        )
    }

    private fun inspectGsa(fields: List<String>, type: String, talker: String): NmeaFixEvidence? {
        if (fields.size < 3) return null
        val fixType = fields[2].toIntOrNull() ?: return null
        // GSA is commonly emitted once per constellation, so its satellite slots are not an
        // aggregate count and must not be shown as X/N.
        return NmeaFixEvidence(type, talker, hasFix = fixType >= 2, usedSatellites = null)
    }

    private fun inspectRmc(fields: List<String>, type: String, talker: String): NmeaFixEvidence? {
        if (fields.size < 3) return null
        val statusValid = fields[2].equals("A", ignoreCase = true)
        val hasCoordinates = fields.getOrNull(3).orEmpty().isNotBlank() &&
            fields.getOrNull(4).orEmpty().isNotBlank() &&
            fields.getOrNull(5).orEmpty().isNotBlank() &&
            fields.getOrNull(6).orEmpty().isNotBlank()
        val mode = fields.getOrNull(12)?.uppercase(Locale.ROOT).orEmpty()
        val gnssMode = mode.isEmpty() || mode.any { it in GNSS_FIX_MODES }
        return NmeaFixEvidence(
            type,
            talker,
            hasFix = statusValid && hasCoordinates && gnssMode,
            usedSatellites = null,
        )
    }

    private const val GNSS_FIX_MODES = "ADPRF"
}
