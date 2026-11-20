package analysislunch.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;

/** 공급자와 무관한 JSON 값 접근 유틸리티. */
@Slf4j
public final class JsonUtils {
    private JsonUtils() {
        // 유틸리티 클래스 - 인스턴스화 금지
    }

    /**
     * JSON 문자열의 최상위 객체에서 특정 키의 값을 추출합니다.
     *
     * @param json JSON 문자열
     * @param key 추출할 키
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
            log.warn("JSON에서 키 '{}' 추출 실패: {}", key, e.getMessage());
            return null;
        }
    }

    /** 객체의 자식 객체를 반환하며, 없거나 객체가 아니면 {@code null}입니다. */
    public static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return asObject(obj.get(key));
    }

    /** 객체의 자식 배열을 반환하며, 없거나 배열이 아니면 {@code null}입니다. */
    public static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        return el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    /** JSON 값이 객체일 때만 객체로 반환합니다. */
    public static JsonObject asObject(JsonElement el) {
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    /** 단순 값은 문자열로, 구조화된 값은 JSON으로 반환합니다. */
    public static String jsonElementToString(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            return el.getAsJsonPrimitive().getAsString();
        }
        return el.toString();
    }
}
