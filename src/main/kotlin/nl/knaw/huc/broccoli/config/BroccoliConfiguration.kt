package nl.knaw.huc.broccoli.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.client.JerseyClientConfiguration
import io.dropwizard.core.Configuration
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.resources.AboutResource

class BroccoliConfiguration : Configuration() {
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

class ProjectConfiguration {
    @Valid
    @JsonProperty
    val name: String = ""

    @Valid
    @JsonProperty
    var textType: String = "Text"  // e.g., "Text" or "LogicalText"

    @Valid
    @NotNull
    @JsonProperty
    val topTier: String = "tf:File"

    @Valid
    @JsonProperty
    val views: List<NamedViewConfiguration> = emptyList()

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
}

class NamedViewConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val name: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val conf: ViewConfiguration = ViewConfiguration()
}

class ViewConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val anno: List<ViewAnnoConstraint> = emptyList()

    @Valid
    @NotNull
    @JsonProperty
    var scope: AnnoScope = AnnoScope.WITHIN

    @Suppress("UNUSED") // usage can change via config.yml
    enum class AnnoScope(val toAnnoRepoScope: String) {
        OVERLAP(Constants.AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE),
        WITHIN(Constants.AR_WITHIN_TEXT_ANCHOR_RANGE);

        override fun toString() = name.lowercase()
    }
}


class ViewAnnoConstraint {
    @Valid
    @NotNull
    @JsonProperty
    val path: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val value: String = ""
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

    @Valid
    @JsonProperty
    val deleteKey: String = "confirm-deletion-c6451546-ee86-45af-ac62-7cb1a3a405ed"
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
