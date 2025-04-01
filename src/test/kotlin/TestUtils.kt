import com.google.gson.Gson
import io.dropwizard.testing.junit5.ResourceExtension
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonMapper
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonParser
import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.apache.commons.io.IOUtils
import org.glassfish.jersey.client.JerseyClientBuilder.createClient
import org.mockito.Mockito.mock
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object TestUtils {
    @Throws(IOException::class)
    fun resourceAsString(resourcePath: String): String {
        val stream = getInputStream(resourcePath)
        return IOUtils.toString(stream, StandardCharsets.UTF_8)
    }

    private fun getInputStream(resourcePath: String): InputStream {
        val stream =
            TestUtils::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: throw RuntimeException(
                    String.format(
                        "Could not find resource [%s]",
                        resourcePath
                    )
                )
        return stream
    }

    /**
     * Application context needs host
     */
    fun toUrl(port: Int, path: String?): String {
        return String.format("http://localhost:%d%s", port, path)
    }

    @Throws(IOException::class)
    fun mockAnnoRepo(mockServer: ClientAndServer) {
        mockServer
            .`when`(HttpRequest.request().withPath("/anno-repo.*"))
            .respond(
                HttpResponse.response().withStatusCode(200).withBody(
                    JsonBody.json(
                        resourceAsString("./arAboutResponse.json")
                    )
                )
            )
    }


    fun createTestResources(projectId: String): ResourceExtension {
        val mockAnnoRepoClient = mock(AnnoRepoClient::class.java)
        val brintaConfigJson = resourceAsString("./projects/brintaConfig.json")
        val brintaConfiguration =
            Gson().fromJson(brintaConfigJson, BrintaConfiguration::class.java)
        val project = Project(
            projectId,
            "textType",
            "topTierBodyType",
            emptyMap(),
            brintaConfiguration,
            TextRepo("uri", "apiKey"),
            AnnoRepo(
                mockAnnoRepoClient,
                "containerName", "textType"
            )
        )
        val projects = mapOf(projectId to project)
        val client = createClient()
        val jsonParser = createJsonParser()
        val jsonWriter = createJsonMapper()
        val esClient = ElasticSearchClient(client, jsonParser, jsonWriter)

        val resource = ResourceExtension
            .builder()
            .setMapper(createJsonMapper())
            .addResource(
                HomePageResource()
            ).addResource(
                AboutResource(
                    "http://foo:1234",
                    "foo",
                    "v1"
                )
            )
            .addResource(
                V0Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                    esClient
                )
            )
            .addResource(
                V1Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                    esClient
                )
            )
            .addResource(
                V2Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                    esClient
                )
            )
            .build()
        return resource
    }
}
