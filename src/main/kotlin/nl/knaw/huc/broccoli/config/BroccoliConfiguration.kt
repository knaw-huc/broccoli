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

class TierConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val type = Type.STR

    override fun toString(): String = "$name (${type.name.lowercase()})"

    enum class Type(val toAnnoRepoQuery: (String) -> Any) {
        NUM(Integer::valueOf),
        STR(::identity);
    }
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
