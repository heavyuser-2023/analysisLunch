package analysislunch.infrastructure.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GoogleChatClient {
    private final String webhookUrl;

    public GoogleChatClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * 이미지와 텍스트가 포함된 카드 메시지 전송
     */
    public void sendCard(String imageUrl, String title, String text, String threadKey) throws IOException {
        // Escape text for JSON
        String escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"");

        String jsonBody = String.format("""
            {
              "cardsV2": [{
                "cardId": "lunchCard",
                "card": {
                  "header": {
                    "title": "%s",
                    "imageUrl": "%s",
                    "imageType": "SQUARE"
                  },
                  "sections": [{
                    "widgets": [{
                      "image": {
                        "imageUrl": "%s",
                        "onClick": {
                          "openLink": {
                            "url": "%s"
                          }
                        }
                      }
                    }, {
                      "textParagraph": {
                        "text": "%s"
                      }
                    }]
                  }]
                }
              }]
            }
            """, escapedTitle, imageUrl, imageUrl, imageUrl, escapedText);

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
