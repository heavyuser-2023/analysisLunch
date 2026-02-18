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
import java.util.logging.Logger;

/**
 * Google Chat Webhook API와 통신하는 클라이언트 클래스.
 *
 * <p>이미지와 텍스트가 포함된 카드 형식의 메시지를 Google Chat 공간에 전송합니다.
 */
public class GoogleChatClient {

    private static final Logger logger = Logger.getLogger(GoogleChatClient.class.getName());

    private static final Gson GSON = new Gson();
    private static final int HTTP_OK = 200;
    private static final String CARD_ID = "lunchCard";
    private static final String IMAGE_TYPE = "SQUARE";
    private static final String THREAD_KEY_PARAM = "threadKey";
    private static final String REPLY_OPTION = "messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD";

    private final String webhookUrl;

    /**
     * GoogleChatClient 생성자.
     *
     * @param webhookUrl Google Chat Incoming Webhook URL
     */
    public GoogleChatClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * 이미지와 텍스트가 포함된 카드 메시지를 Google Chat에 전송합니다.
     *
     * @param imageUrl  카드에 표시할 이미지 URL
     * @param title     카드 헤더 제목
     * @param text      카드 본문 텍스트
     * @param threadKey 스레드 키 (null 또는 빈 문자열이면 새 스레드 생성)
     * @throws IOException 메시지 전송 실패 또는 응답 코드가 200이 아닐 때
     */
    public void sendCard(String imageUrl, String title, String text, String threadKey) throws IOException {
        String jsonBody = buildCardJson(imageUrl, title, text);
        String requestUrl = buildRequestUrl(threadKey);

        HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HTTP_OK) {
            String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Google Chat 전송 실패 (" + responseCode + "): " + error);
        }
        logger.info("Google Chat 메시지 전송 성공");
    }

    /**
     * 카드 메시지 JSON 본문을 생성합니다.
     *
     * @param imageUrl 이미지 URL
     * @param title    카드 제목
     * @param text     카드 본문 텍스트
     * @return JSON 문자열
     */
    private String buildCardJson(String imageUrl, String title, String text) {
        JsonObject header = new JsonObject();
        header.addProperty("title", title);
        header.addProperty("imageUrl", imageUrl);
        header.addProperty("imageType", IMAGE_TYPE);

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
        cardWrapper.addProperty("cardId", CARD_ID);
        cardWrapper.add("card", card);

        JsonArray cardsV2 = new JsonArray();
        cardsV2.add(cardWrapper);

        JsonObject body = new JsonObject();
        body.add("cardsV2", cardsV2);

        return GSON.toJson(body);
    }

    /**
     * 스레드 키를 포함한 요청 URL을 생성합니다.
     *
     * @param threadKey 스레드 키 (null 또는 빈 문자열이면 기본 URL 반환)
     * @return 완성된 요청 URL 문자열
     */
    private String buildRequestUrl(String threadKey) {
        if (threadKey == null || threadKey.isEmpty()) {
            return webhookUrl;
        }
        String encodedThreadKey = URLEncoder.encode(threadKey, StandardCharsets.UTF_8);
        String separator = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + separator + THREAD_KEY_PARAM + "=" + encodedThreadKey + "&" + REPLY_OPTION;
    }
}
