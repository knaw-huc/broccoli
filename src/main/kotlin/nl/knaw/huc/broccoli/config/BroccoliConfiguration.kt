package nl.knaw.huc.broccoli.config

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

class GlobaliseConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val annoRepo: AnnoRepoConfiguration = AnnoRepoConfiguration()
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
