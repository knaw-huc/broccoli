package nl.knaw.huc.broccoli.resources.republic

import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotFoundException

class RepublicVolumeMapper(private val config: RepublicConfiguration) {
    fun byBodyId(bodyId: String): RepublicVolume {
        if (bodyId.startsWith(REPUBLIC_NS)) {
            val remainderOfId = bodyId.substringAfter(REPUBLIC_NS)

            // urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11 -> 1728
            if (remainderOfId.startsWith("session")) {
                val volumeName = remainderOfId
                    .substringAfter("session-")
                    .substringBefore('-')
                return byVolumeName(volumeName)
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

    fun byVolumeName(volumeName: String): RepublicVolume {
        return config.volumes.find { it.name == volumeName }
            ?: throw NotFoundException("Volume [$volumeName] not found in republic configuration")
    }

    fun buildBodyId(volume: RepublicVolume, openingNr: Int): String {
        val archNr = config.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(openingNr)
        return "${REPUBLIC_NS}NL-HaNA_${archNr}_${invNr}_${scanNr}"
    }

    companion object {
        private const val REPUBLIC_NS = "urn:republic:"
    }
}
