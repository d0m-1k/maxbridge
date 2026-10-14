package dom4k.maxbridge.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class TelegramRaw {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private TelegramRaw() {}

    public static JsonNode call(String token, String method, ObjectNode params) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(params.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + method, e);
        }
        JsonNode json = MAPPER.readTree(response.body());
        if (!json.path("ok").asBoolean(false))
            throw new IOException(method + " failed (" + response.statusCode() + "): "
                    + json.path("description").asText(response.body()));
        return json.path("result");
    }

    public static ObjectNode params() {
        return MAPPER.createObjectNode();
    }
}
