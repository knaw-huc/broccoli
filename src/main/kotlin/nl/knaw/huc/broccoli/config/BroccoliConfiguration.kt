package nl.knaw.huc.broccoli.config

import com.fasterxml.jackson.annotation.JsonProperty
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Configuration
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.resources.AboutResource
import javax.validation.Valid
import javax.validation.constraints.NotNull

open class BroccoliConfiguration : Configuration() {

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
    var iiifUrl : String = ""

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

}
