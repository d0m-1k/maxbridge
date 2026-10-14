package dom4k.maxbridge.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RichMessage {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ArrayNode blocks = MAPPER.createArrayNode();
    private Long messageThreadId;

    public RichMessage thread(Long messageThreadId) {
        this.messageThreadId = messageThreadId;
        return this;
    }

    public static RichMessage create() {
        return new RichMessage();
    }

    public RichMessage heading(String text, int size) {
        ObjectNode b = blocks.addObject();
        b.put("type", "heading");
        b.put("text", text);
        b.put("size", size);
        return this;
    }

    public RichMessage photo(String url) {
        ObjectNode b = blocks.addObject();
        b.put("type", "photo");
        ObjectNode photo = b.putObject("photo");
        photo.put("media", url);
        photo.put("type", "photo");
        return this;
    }

    public RichMessage paragraph(String text) {
        ObjectNode b = blocks.addObject();
        b.put("type", "paragraph");
        b.put("text", text);
        return this;
    }

    public RichMessage divider() {
        blocks.addObject().put("type", "divider");
        return this;
    }

    public RichMessage footer(String text) {
        ObjectNode b = blocks.addObject();
        b.put("type", "footer");
        b.put("text", text);
        return this;
    }


    public RichMessage details(String summary, java.util.function.Consumer<RichMessage> inner) {
        RichMessage nested = RichMessage.create();
        inner.accept(nested);
        ObjectNode b = blocks.addObject();
        b.put("type", "details");
        b.put("summary", summary);
        b.set("blocks", nested.blocks);
        return this;
    }

    public RichMessage buttons(ObjectNode... row) {
        ObjectNode b = blocks.addObject();
        b.put("type", "buttons");
        ArrayNode arr = b.putArray("buttons");
        for (ObjectNode btn : row) arr.add(btn);
        return this;
    }

    public static ObjectNode buttonCallback(String text, String callbackData) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("text", text);
        n.put("callback_data", callbackData);
        return n;
    }

    public static ObjectNode buttonInline(String text, String inlineQuery) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("text", text);
        n.put("switch_inline_query_current_chat", inlineQuery);
        return n;
    }

    public static ObjectNode buttonCopy(String text, String copyText) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("text", text);
        n.putObject("copy_text").put("text", copyText);
        return n;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public String toRequestJson(long chatId) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("chat_id", chatId);
        if (messageThreadId != null) root.put("message_thread_id", messageThreadId);
        root.putObject("rich_message").set("blocks", blocks);
        return root.toString();
    }

    public int send(String botToken, long chatId) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendRichMessage"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(chatId)))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending rich message", e);
        }

        JsonNode json = MAPPER.readTree(response.body());
        if (!json.path("ok").asBoolean(false)) {
            throw new IOException("sendRichMessage failed (" + response.statusCode() + "): "
                    + json.path("description").asText(response.body()));
        }
        JsonNode id = json.path("result").path("message_id");
        if (!id.isInt() && !id.isLong()) {
            throw new IOException("sendRichMessage ok, but no message_id in response: " + response.body());
        }
        return id.asInt();
    }
}
