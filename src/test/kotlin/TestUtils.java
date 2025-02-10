import org.apache.commons.io.IOUtils;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class TestUtils {
  public static String resourceAsString(String resourcePath) throws IOException {
    var stream = getInputStream(resourcePath);
    return IOUtils.toString(stream, UTF_8);
  }

  private static InputStream getInputStream(String resourcePath) {
    var stream = TestUtils.class.getClassLoader().getResourceAsStream(resourcePath);
    if (stream == null) {
      throw new RuntimeException(format("Could not find resource [%s]", resourcePath));
    }
    return stream;
  }
  /**
   * Application context needs host
   */
  public static String toUrl(int port, String path) {
    return String.format("http://localhost:%d%s", port, path);
  }

  public static void mockAnnoRepo(ClientAndServer mockServer) throws IOException {
    mockServer
        .when(request().withPath("/anno-repo.*"))
        .respond(
            response().withStatusCode(200).withBody(
                json(
                    resourceAsString("./arAboutResponse.json")
                )
            )
        );
  }



}
