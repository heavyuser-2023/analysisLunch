package analysislunch.infrastructure.client;

import static analysislunch.utils.JsonUtils.asObject;
import static analysislunch.utils.JsonUtils.getArray;
import static analysislunch.utils.JsonUtils.getObject;
import static analysislunch.utils.JsonUtils.jsonElementToString;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;

/** OpenAI 응답의 choices/message 및 data/b64_json 구조를 처리합니다. */
@Slf4j
final class OpenAiResponseParser {
    static final String FALLBACK_TEXT = "분석 결과 없음";

    private OpenAiResponseParser() {
    }

    static String extractOpenAiText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = getArray(root, "choices");
            if (choices == null) {
                return FALLBACK_TEXT;
            }
            for (JsonElement choiceEl : choices) {
                JsonObject choice = asObject(choiceEl);
                JsonObject message = getObject(choice, "message");
                if (message != null && message.has("content")) {
                    return jsonElementToString(message.get("content"));
                }
            }
            return FALLBACK_TEXT;
        } catch (JsonParseException | IllegalStateException e) {
            log.warn("OpenAI 응답 텍스트 추출 실패: {}", e.getMessage());
            return FALLBACK_TEXT;
        }
    }

    static String extractImageData(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = getArray(root, "data");
            if (data == null || data.isEmpty()) {
                return null;
            }
            JsonObject image = asObject(data.get(0));
            if (image != null && image.has("b64_json")) {
                return jsonElementToString(image.get("b64_json"));
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            log.warn("OpenAI 응답 이미지 데이터 추출 실패: {}", e.getMessage());
            return null;
        }
    }
}
