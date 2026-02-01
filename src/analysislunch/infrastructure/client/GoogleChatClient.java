package analysislunch.infrastructure.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GoogleChatClient {
    private final String webhookUrl;
    private static final Gson GSON = new Gson();

    public GoogleChatClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * 이미지와 텍스트가 포함된 카드 메시지 전송
     */
    public void sendCard(String imageUrl, String title, String text, String threadKey) throws IOException {
        JsonObject header = new JsonObject();
        header.addProperty("title", title);
        header.addProperty("imageUrl", imageUrl);
        header.addProperty("imageType", "SQUARE");

        JsonObject openLink = new JsonObject();
        openLink.addProperty("url", imageUrl);
        JsonObject onClick = new JsonObject();
        onClick.add("openLink", openLink);
        JsonObject image = new JsonObject();
        image.addProperty("imageUrl", imageUrl);
        image.add("onClick", onClick);
        JsonObject imageWidget = new JsonObject();
        imageWidget.add("image", image);

        JsonObject textParagraph = new JsonObject();
        textParagraph.addProperty("text", text);
        JsonObject textWidget = new JsonObject();
        textWidget.add("textParagraph", textParagraph);

        JsonArray widgets = new JsonArray();
        widgets.add(imageWidget);
        widgets.add(textWidget);

        JsonObject section = new JsonObject();
        section.add("widgets", widgets);
        JsonArray sections = new JsonArray();
        sections.add(section);

        JsonObject card = new JsonObject();
        card.add("header", header);
        card.add("sections", sections);

        JsonObject cardWrapper = new JsonObject();
        cardWrapper.addProperty("cardId", "lunchCard");
        cardWrapper.add("card", card);

        JsonArray cardsV2 = new JsonArray();
        cardsV2.add(cardWrapper);

        JsonObject body = new JsonObject();
        body.add("cardsV2", cardsV2);
        String jsonBody = GSON.toJson(body);

        String requestUrl = this.webhookUrl;
        if (threadKey != null && !threadKey.isEmpty()) {
            String encodedThreadKey = URLEncoder.encode(threadKey, StandardCharsets.UTF_8);
            String separator = requestUrl.contains("?") ? "&" : "?";
            requestUrl = requestUrl + separator + "threadKey=" + encodedThreadKey
                + "&messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD";
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Google Chat send failed (" + responseCode + "): " + error);
        }
        System.out.println("  Google Chat message sent successfully");
    }
}
