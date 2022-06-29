package nl.knaw.huc.broccoli.config

import com.fasterxml.jackson.annotation.JsonProperty
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Configuration
import nl.knaw.huc.broccoli.api.BRConst
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
    @JsonProperty("swagger")
    val swaggerBundleConfiguration = SwaggerBundleConfiguration().apply {
        resourcePackage = "blabla" // TODO: AboutResource::class.java.getPackage().name
        version = javaClass.getPackage().implementationVersion
        title = BRConst.APP_NAME
    }
}
