package analysislunch.infrastructure.client;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

import analysislunch.utils.HttpUtils;

/**
 * Discord Incoming Webhook과 통신하는 클라이언트 클래스.
 *
 * <p>Webhook URL 하나로 텍스트와 사진을 채널에 브로드캐스트합니다. Google Chat
 * Webhook과 동일한 모델이라 서버 없는 GitHub Actions cron 환경에 그대로
 * 들어맞습니다. 사진은 외부 URL이 아닌 로컬 파일을 multipart로 직접 업로드하여
 * CDN 전파 지연의 영향을 받지 않습니다.
 */
@Slf4j
public class DiscordClient {

    private static final String IMAGE_CONTENT_TYPE = "image/png";
    /** Discord 메시지 본문(content) 최대 길이. */
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final Gson GSON = new Gson();

    private final String webhookUrl;

    /**
     * DiscordClient 생성자.
     *
     * @param webhookUrl Discord 채널 Incoming Webhook URL
     */
    public DiscordClient(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * 본문 텍스트와 함께 사진 한 장을 전송합니다.
     *
     * <p>{@code payload_json} 필드에 본문을, {@code file} 파트에 이미지를 담아
     * 한 번의 multipart 요청으로 전송합니다.
     *
     * @param image   전송할 이미지 파일
     * @param content 메시지 본문 (2000자 초과 시 잘림)
     * @throws IOException API 호출 실패 또는 응답 코드가 정상이 아닐 때
     */
    public void sendPhoto(File image, String content) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", truncate(content));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("payload_json", GSON.toJson(payload));

        HttpUtils.postMultipart(webhookUrl, fields, "file", image, IMAGE_CONTENT_TYPE);
    }

    /**
     * 본문을 Discord 허용 길이로 자릅니다.
     *
     * @param content 원본 본문
     * @return 최대 길이 이내로 잘린 본문
     */
    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH);
    }
}
