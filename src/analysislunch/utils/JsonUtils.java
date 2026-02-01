package analysislunch.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtils {
    public static String extract(String json, String key) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has(key) || root.get(key).isJsonNull()) {
                return null;
            }
            return jsonElementToString(root.get(key));
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractGeminiText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = getArray(root, "candidates");
            if (candidates == null) {
                return "분석 결과 없음";
            }
            for (JsonElement candidateEl : candidates) {
                JsonObject candidate = asObject(candidateEl);
                if (candidate == null) continue;
                JsonObject content = getObject(candidate, "content");
                if (content == null) continue;
                JsonArray parts = getArray(content, "parts");
                if (parts == null) continue;
                for (JsonElement partEl : parts) {
                    JsonObject part = asObject(partEl);
                    if (part == null) continue;
                    if (part.has("text")) {
                        return jsonElementToString(part.get("text"));
                    }
                }
            }
            return "분석 결과 없음";
        } catch (Exception e) {
            return "분석 결과 없음";
        }
    }

    /**
     * Extract base64 image data from Gemini response
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
                if (candidate == null) continue;
                JsonObject content = getObject(candidate, "content");
                if (content == null) continue;
                JsonArray parts = getArray(content, "parts");
                if (parts == null) continue;
                for (JsonElement partEl : parts) {
                    JsonObject part = asObject(partEl);
                    if (part == null) continue;
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
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Slack 파일 업로드 응답에서 공유 메시지 ts 추출
     */
    public static String extractSlackShareTs(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray files = getArray(root, "files");
            if (files == null || files.size() == 0) {
                return null;
            }
            for (JsonElement fileEl : files) {
                JsonObject file = asObject(fileEl);
                if (file == null) continue;
                JsonObject shares = getObject(file, "shares");
                if (shares == null) continue;
                String ts = extractShareTsFromScope(shares, "public");
                if (ts != null) return ts;
                ts = extractShareTsFromScope(shares, "private");
                if (ts != null) return ts;
            }
            return null;
        } catch (Exception e) {
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
            if (shareList == null || shareList.size() == 0) {
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
