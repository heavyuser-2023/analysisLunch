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

/** Gemini 응답의 candidates/parts 구조와 thought 파트를 처리합니다. */
@Slf4j
final class GeminiResponseParser {
    static final String FALLBACK_TEXT = "분석 결과 없음";

    private GeminiResponseParser() {
    }

    static String extractGeminiText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = getArray(root, "candidates");
            if (candidates == null) {
                return FALLBACK_TEXT;
            }
            for (JsonElement candidateEl : candidates) {
                JsonObject candidate = asObject(candidateEl);
                if (candidate == null) {
                    continue;
                }
                JsonObject content = getObject(candidate, "content");
                if (content == null) {
                    continue;
                }
                JsonArray parts = getArray(content, "parts");
                if (parts == null) {
                    continue;
                }
                for (JsonElement partEl : parts) {
                    JsonObject part = asObject(partEl);
                    if (part != null && !isThoughtPart(part) && part.has("text")) {
                        return jsonElementToString(part.get("text"));
                    }
                }
            }
            return FALLBACK_TEXT;
        } catch (JsonParseException | IllegalStateException e) {
            log.warn("Gemini 응답 텍스트 추출 실패: {}", e.getMessage());
            return FALLBACK_TEXT;
        }
    }

    static String extractImageData(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = getArray(root, "candidates");
            if (candidates == null) {
                return null;
            }
            for (JsonElement candidateEl : candidates) {
                JsonObject candidate = asObject(candidateEl);
                if (candidate == null) {
                    continue;
                }
                JsonObject content = getObject(candidate, "content");
                if (content == null) {
                    continue;
                }
                JsonArray parts = getArray(content, "parts");
                if (parts == null) {
                    continue;
                }
                for (JsonElement partEl : parts) {
                    JsonObject part = asObject(partEl);
                    if (part == null || isThoughtPart(part)) {
                        continue;
                    }
                    JsonObject inlineData = getObject(part, "inlineData");
                    if (inlineData == null) {
                        inlineData = getObject(part, "inline_data");
                    }
                    if (inlineData != null && inlineData.has("data")) {
                        return jsonElementToString(inlineData.get("data"));
                    }
                }
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            log.warn("Gemini 응답 이미지 데이터 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    // 사고 과정을 거치는 모델은 중간 결과를 thought: true 파트로 보낼 수 있으므로 건너뜀
    private static boolean isThoughtPart(JsonObject part) {
        JsonElement thought = part.get("thought");
        return thought != null && thought.isJsonPrimitive() && thought.getAsBoolean();
    }
}
