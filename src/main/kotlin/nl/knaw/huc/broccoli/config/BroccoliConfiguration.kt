package nl.knaw.huc.broccoli.config

import arrow.core.identity
import com.fasterxml.jackson.annotation.JsonProperty
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Configuration
import io.dropwizard.client.JerseyClientConfiguration
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.resources.AboutResource
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull

class BroccoliConfiguration : Configuration() {
    @Valid
    @NotNull
    @JsonProperty
    var textUri = "textrepo"

    @Valid
    @NotNull
    @JsonProperty
    var iiifUri = "iiif"

    @Valid
    @NotNull
    @JsonProperty("swagger")
    val swaggerBundleConfiguration = SwaggerBundleConfiguration().apply {
        resourcePackage = AboutResource::class.java.getPackage().name
        version = javaClass.getPackage().implementationVersion
        title = Constants.APP_NAME
    }

    @Valid
    @NotNull
    @JsonProperty
    val externalBaseUrl = ""

    @Valid
    @NotNull
    @JsonProperty
    var jerseyClient = JerseyClientConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    var projects = ArrayList<ProjectConfiguration>()

    @Valid
    @NotNull
    @JsonProperty
    var globalise = GlobaliseConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    var republic = RepublicConfiguration()
}

class AnnoRepoConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    var uri: String = "https://annorepo.example.com"

    @Valid
    @JsonProperty
    var containerName: String = "annorepo-container-name"

    @Valid
    @JsonProperty
    var apiKey: String? = null
}

class TextRepoConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val uri = "https://textrepo.example.com"

    @Valid
    @JsonProperty
    var apiKey: String? = null
}

class IIIFConfiguration {
    @JsonProperty
    var fixed: String? = null

    @JsonProperty
    var fetch: String? = null
}

class ProjectConfiguration {
    @Valid
    @JsonProperty
    val name: String = ""

    @Valid
    @JsonProperty
    @NotNull
    val tiers: List<TierConfiguration> = ArrayList()

    @Valid
    @JsonProperty
    val views: List<ViewConfiguration> = emptyList()

    @Valid
    @JsonProperty
    @NotNull
    val brinta: BrintaConfiguration = BrintaConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    val annoRepo = AnnoRepoConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    val textRepo = TextRepoConfiguration()

    @Valid
    @JsonProperty
    val iiif = IIIFConfiguration()
}

class ViewConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val anno: List<ViewAnnoPath> = emptyList()
}

class ViewAnnoPath {
    @Valid
    @NotNull
    @JsonProperty
    val path: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val value: String = ""
}

class TierConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val type = Type.STR

    @Valid
    @JsonProperty
    val anno: String? = null

    override fun toString(): String = "$name (${type.name.lowercase()})"

    enum class Type(val toAnnoRepoQuery: (String) -> Any) {
        NUM(Integer::valueOf),
        STR(::identity);
    }
}

class BrintaConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val uri: String = "http://localhost:9200"

    @Valid
    @JsonProperty
    val joinSeparator: String? = null

    @Valid
    @NotNull
    @JsonProperty
    val indices: List<IndexConfiguration> = ArrayList()
}

class IndexConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val bodyTypes: List<String> = ArrayList()

    @Valid
    @NotNull
    @JsonProperty
    val fields: List<IndexFieldConfiguration> = ArrayList()
}

class IndexFieldConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @JsonProperty
    val path: String = "$.body.id"

    @Valid
    @JsonProperty
    val type: String? = null
}


class GlobaliseConfiguration {

    @Valid
    @NotNull
    @JsonProperty
    val annoRepo = AnnoRepoConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    val textRepo = TextRepoConfiguration()


    @Valid
    @NotNull
    @JsonProperty
    var archiefNr = ""

    @JsonProperty
    var documents: List<GlobaliseDocument> = ArrayList()

    @Valid
    @NotNull
    @JsonProperty
    val defaultDocument: String = ""

    @Valid
    @NotNull
    @JsonProperty
    @Min(1)
    val defaultOpening: Int = 1

}

class GlobaliseDocument {
    @Valid
    @NotNull
    @JsonProperty
    var name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    var invNr: String = ""

    @Valid
    @JsonProperty
    var manifest: String? = null
}

class RepublicConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val annoRepo: AnnoRepoConfiguration = AnnoRepoConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    var archiefNr = ""

    @JsonProperty
    var volumes: List<RepublicVolume> = ArrayList()

    @Valid
    @NotNull
    @JsonProperty
    var defaultVolume: String = ""

    @Valid
    @NotNull
    @JsonProperty
    @Min(1)
    var defaultOpening: Int = 1
}

class RepublicVolume {
    @Valid
    @NotNull
    @JsonProperty
    var name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    var invNr: String = ""

    @Valid
    @NotNull
    @JsonProperty
    var imageset: String = ""
}
