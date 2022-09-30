package nl.knaw.huc.broccoli.resources

import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotFoundException

private const val REPUBLIC_NS = "urn:republic:"

class VolumeMapper(private val config: RepublicConfiguration) {
    fun byBodyId(bodyId: String): RepublicVolume {
        if (bodyId.startsWith(REPUBLIC_NS)) {
            val remainderOfId = bodyId.substringAfter(REPUBLIC_NS)

            // urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11 -> 1728
            if (remainderOfId.startsWith("session")) {
                val volumeId = remainderOfId
                    .substringAfter("session-")
                    .substringBefore('-')
                return byVolumeId(volumeId)
            }

            // urn:republic:NL-HaNA_1.01.02_3783_0331 -> [invnr 3783] -> 1728
            if (remainderOfId.startsWith("NL-HaNA_${config.archiefNr}")) {
                val invNr = remainderOfId
                    .substringAfter("NL-HaNA_${config.archiefNr}_")
                    .substringBefore('_')
                return byInventarisNummer(invNr)
            }
        }

        throw BadRequestException(
            "Unrecognised bodyId [$bodyId]: expecting session, or NL-HaNA_${config.archiefNr} after $REPUBLIC_NS"
        )
    }

    fun byInventarisNummer(invNr: String): RepublicVolume {
        return config.volumes.find { it.invNr == invNr }
            ?: throw NotFoundException("Inventarisnummer [$invNr] not found in republic configuration")
    }

    fun byVolumeId(volumeId: String): RepublicVolume {
        return config.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume [$volumeId] not found in republic configuration")
    }

    fun buildBodyId(volume: RepublicVolume, openingNr: Int): String {
        val archNr = config.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(openingNr)
        return "${REPUBLIC_NS}NL-HaNA_${archNr}_${invNr}_${scanNr}"
    }

}