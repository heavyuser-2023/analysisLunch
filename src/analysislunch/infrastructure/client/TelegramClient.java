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
 * Telegram Bot API와 통신하는 클라이언트 클래스.
 *
 * <p>봇 토큰과 채팅 ID(채널/그룹)만으로 텍스트와 사진을 브로드캐스트합니다.
 * 사용자별 상태 저장이 필요 없어 서버 없는 GitHub Actions cron 환경에 그대로
 * 들어맞습니다. 사진은 외부 URL이 아닌 로컬 파일을 multipart로 직접 업로드하여
 * CDN 전파 지연의 영향을 받지 않습니다.
 */
@Slf4j
public class TelegramClient {

    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final String IMAGE_CONTENT_TYPE = "image/png";
    private static final Gson GSON = new Gson();

    private final String botToken;
    private final String chatId;

    /**
     * TelegramClient 생성자.
     *
     * @param botToken Telegram 봇 토큰 (BotFather 발급)
     * @param chatId   메시지를 보낼 채널/그룹 채팅 ID (예: "@my_channel" 또는 숫자 ID)
     */
    public TelegramClient(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    /**
     * 채팅에 텍스트 메시지를 전송합니다.
     *
     * <p>메뉴 텍스트에 포함될 수 있는 특수문자로 인한 파싱 오류(400)를 피하기 위해
     * 서식 모드 없이 평문으로 전송합니다. cron은 전송 실패 시 재시도가 없으므로
     * 서식보다 전송 성공을 우선합니다.
     *
     * @param text 전송할 텍스트
     * @throws IOException API 호출 실패 또는 응답이 정상이 아닐 때
     */
    public void sendMessage(String text) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("text", text);

        String response = HttpUtils.postJson(API_BASE + botToken + "/sendMessage", null, GSON.toJson(body));
        if (!isResponseOk(response)) {
            throw new IOException("Telegram 메시지 전송 실패: " + response);
        }
    }

    /**
     * 채팅에 사진을 전송합니다.
     *
     * @param image   전송할 이미지 파일
     * @param caption 사진 캡션 (평문, null 또는 빈 문자열 허용)
     * @throws IOException API 호출 실패 또는 응답이 정상이 아닐 때
     */
    public void sendPhoto(File image, String caption) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chat_id", chatId);
        if (caption != null && !caption.isEmpty()) {
            fields.put("caption", caption);
        }

        String response = HttpUtils.postMultipart(
            API_BASE + botToken + "/sendPhoto", fields, "photo", image, IMAGE_CONTENT_TYPE);
        if (!isResponseOk(response)) {
            throw new IOException("Telegram 사진 전송 실패: " + response);
        }
    }

    /**
     * Telegram API 응답의 {@code ok} 필드로 성공 여부를 확인합니다.
     *
     * @param response API 응답 JSON 문자열
     * @return {@code ok}가 {@code true}이면 {@code true}
     */
    private boolean isResponseOk(String response) {
        try {
            JsonObject json = GSON.fromJson(response, JsonObject.class);
            return json != null && json.has("ok") && json.get("ok").getAsBoolean();
        } catch (Exception e) {
            log.warn("Telegram 응답 JSON 파싱 실패: {}", e.getMessage());
            return false;
        }
    }
}
