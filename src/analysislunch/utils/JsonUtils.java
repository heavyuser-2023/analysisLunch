package analysislunch.utils;

import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * JSON 파싱 유틸리티 클래스.
 *
 * <p>Gemini API 응답, Slack API 응답 등의 JSON 데이터에서 필요한 값을 추출합니다.
 */
public class JsonUtils {

    private static final Logger logger = Logger.getLogger(JsonUtils.class.getName());
    private static final String FALLBACK_TEXT = "분석 결과 없음";

    private JsonUtils() {
        // 유틸리티 클래스 - 인스턴스화 금지
    }

    /**
     * JSON 문자열의 최상위 객체에서 특정 키의 값을 추출합니다.
     *
     * @param json JSON 문자열
     * @param key  추출할 키
     * @return 키에 해당하는 값 문자열, 없거나 파싱 실패 시 {@code null}
     */
    public static String extract(String json, String key) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has(key) || root.get(key).isJsonNull()) {
                return null;
            }
            return jsonElementToString(root.get(key));
        } catch (JsonParseException | IllegalStateException e) {
            logger.warning("JSON에서 키 '" + key + "' 추출 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gemini API 응답 JSON에서 텍스트 내용을 추출합니다.
     *
     * @param json Gemini API 응답 JSON 문자열
     * @return 추출된 텍스트, 없거나 파싱 실패 시 {@value #FALLBACK_TEXT}
     */
    public static String extractGeminiText(String json) {
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
                    if (part != null && part.has("text")) {
                        return jsonElementToString(part.get("text"));
                    }
                }
            }
            return FALLBACK_TEXT;
        } catch (JsonParseException | IllegalStateException e) {
            logger.warning("Gemini 응답 텍스트 추출 실패: " + e.getMessage());
            return FALLBACK_TEXT;
        }
    }

    /**
     * Gemini API 응답 JSON에서 Base64 인코딩된 이미지 데이터를 추출합니다.
     *
     * @param json Gemini API 응답 JSON 문자열
     * @return Base64 이미지 데이터 문자열, 없거나 파싱 실패 시 {@code null}
     */
    public static String extractImageData(String json) {
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
                    if (part == null) {
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
            logger.warning("Gemini 응답 이미지 데이터 추출 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * Slack 파일 업로드 완료 응답에서 공유 메시지의 타임스탬프(ts)를 추출합니다.
     *
     * @param json Slack files.completeUploadExternal API 응답 JSON 문자열
     * @return 메시지 타임스탬프 문자열, 없거나 파싱 실패 시 {@code null}
     */
    public static String extractSlackShareTs(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray files = getArray(root, "files");
            if (files == null || files.isEmpty()) {
                return null;
            }
            for (JsonElement fileEl : files) {
                JsonObject file = asObject(fileEl);
                if (file == null) {
                    continue;
                }
                JsonObject shares = getObject(file, "shares");
                if (shares == null) {
                    continue;
                }
                String ts = extractShareTsFromScope(shares, "public");
                if (ts != null) {
                    return ts;
                }
                ts = extractShareTsFromScope(shares, "private");
                if (ts != null) {
                    return ts;
                }
            }
            return null;
        } catch (JsonParseException | IllegalStateException e) {
            logger.warning("Slack 파일 공유 타임스탬프 추출 실패: " + e.getMessage());
            return null;
        }
    }

    private static String extractShareTsFromScope(JsonObject shares, String scope) {
        JsonObject scopeObj = getObject(shares, scope);
        if (scopeObj == null) {
            return null;
        }
        for (String channelKey : scopeObj.keySet()) {
            JsonArray shareList = getArray(scopeObj, channelKey);
            if (shareList == null || shareList.isEmpty()) {
                continue;
            }
            JsonObject share = asObject(shareList.get(0));
            if (share != null && share.has("ts")) {
                return jsonElementToString(share.get("ts"));
            }
        }
        return null;
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return asObject(obj.get(key));
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    private static JsonObject asObject(JsonElement el) {
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static String jsonElementToString(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            return el.getAsJsonPrimitive().getAsString();
        }
        return el.toString();
    }
}
