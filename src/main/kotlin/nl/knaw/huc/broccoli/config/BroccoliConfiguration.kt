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
    var textRepoURL: String = "textrepo"

    @Valid
    @NotNull
    @JsonProperty
    var annoRepoURL: String = "annorepo"

    @Valid
    @NotNull
    @JsonProperty
    var iiifUrl: String = ""

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

class RepublicConfiguration {
    @JsonProperty
    val volumes: List<RepublicVolume> = ArrayList()

    @Valid
    @NotNull
    @JsonProperty
    val defaultVolume: String = ""

    @Valid
    @NotNull
    @JsonProperty
    @Min(1)
    val defaultOpening: Int = 1
}

class RepublicVolume {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val imageset: String = ""
}