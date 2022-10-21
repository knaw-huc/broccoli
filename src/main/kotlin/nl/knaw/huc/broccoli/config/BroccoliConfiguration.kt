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
    var textUri: String = "textrepo"

    @Valid
    @NotNull
    @JsonProperty
    var annoRepo: AnnoRepoConfiguration = AnnoRepoConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    var iiifUri: String = "iiif"

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
    val externalBaseUrl: String = ""

    @Valid
    @NotNull
    @JsonProperty
    var jerseyClient: JerseyClientConfiguration = JerseyClientConfiguration()

    @Valid
    @NotNull
    @JsonProperty
    val republic: RepublicConfiguration = RepublicConfiguration()
}

class AnnoRepoConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    var uri: String = "annorepo"

    @Valid
    @JsonProperty
    var key: String? = null

    @Valid
    @JsonProperty
    var rev: String? = null
}

class RepublicConfiguration {
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
