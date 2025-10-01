package nl.knaw.huc.broccoli.config

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.client.JerseyClientConfiguration
import io.dropwizard.core.Configuration
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.Companion.DEFAULT_CACHE_CAPACITY
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.Companion.DEFAULT_CACHE_THRESHOLD

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

    @Valid
    @JsonProperty
    val globalCache: CacheConfiguration? = null
}

class CacheConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val capacity = 1000
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

    @Valid
    @NotNull
    @JsonProperty
    var cacheCapacity = DEFAULT_CACHE_CAPACITY

    @Valid
    @NotNull
    @JsonProperty
    var cacheThreshold = DEFAULT_CACHE_THRESHOLD
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
    val topTierBodyType: String = "tf:File"

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
    val anno: List<ViewAnnoConstraint> = emptyList()

    @Valid
    @JsonProperty
    val groupBy: String? = null

    @Valid
    @JsonProperty
    val findWithin: ViewFindWithinConfiguration? = null
}

class ViewFindWithinConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val path: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val value: String = ""

    @Valid
    @NotNull
    @JsonProperty
    val groupBy: String = ""
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
    val values: List<String> = emptyList()
}

class BrintaConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    val uri: String = "http://localhost:9200"

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
    var name: String = ""

    @Valid
    @JsonProperty
    var type: String = "keyword"

    @Valid
    @JsonProperty
    // mutually exclusive with 'logical', 'nested'
    val path: String? = null

    @Valid
    @JsonProperty
    // mutually exclusive with 'path', 'nested'
    val logical: LogicalIndexFieldConfiguration? = null

    class LogicalIndexFieldConfiguration {
        @Valid
        @NotNull
        @JsonProperty
        val scope: String = ""

        @Valid
        @NotNull
        @JsonProperty
        val path: String = ""

        @Valid
        @JsonProperty
        val fixed: FixedIndexFieldConfiguration? = null

        class FixedIndexFieldConfiguration {
            @Valid
            @NotNull
            @JsonProperty
            val path: String = ""

            @Valid
            @NotNull
            @JsonProperty
            val value: String = ""
        }
    }

    @Valid
    @JsonProperty
    // mutually exclusive with 'path', 'logical'
    val nested: NestedIndexFieldConfiguration? = null

    class NestedIndexFieldConfiguration {
        @Valid
        @NotNull
        @JsonProperty
        val from: List<String> = ArrayList()

        @Valid
        @NotNull
        @JsonProperty
        val fields: List<PathIndexFieldConfiguration> = ArrayList()

        @Valid
        @NotNull
        @JsonProperty
        val with: List<NestedIndexFieldConstraint> = ArrayList()

        class NestedIndexFieldConstraint {
            @Valid
            @JsonProperty
            val equal: String? = null

            @Valid
            @JsonProperty
            val overlap: String? = null
        }

        class PathIndexFieldConfiguration {
            @Valid
            @NotNull
            @JsonProperty
            val name: String = ""

            @Valid
            @JsonProperty
            val path: String? = null

            @Valid
            @JsonProperty
            val type: String = "keyword"
        }
    }
}
