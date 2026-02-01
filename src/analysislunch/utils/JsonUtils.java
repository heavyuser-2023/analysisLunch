package analysislunch.utils;

import java.util.List;
import java.util.Map;

public class JsonUtils {
    public static String extract(String json, String key) {
        try {
            Object root = SimpleJson.parse(json);
            return findFirstStringByKey(root, key);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractGeminiText(String json) {
        try {
            Object root = SimpleJson.parse(json);
            String text = findFirstStringByKey(root, "text");
            return text != null ? text : "분석 결과 없음";
        } catch (Exception e) {
            return "분석 결과 없음";
        }
    }

    /**
     * Extract base64 image data from Gemini response
     */
    public static String extractImageData(String json) {
        try {
            Object root = SimpleJson.parse(json);
            return findFirstStringByKey(root, "data");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Slack 파일 업로드 응답에서 공유 메시지 ts 추출
     */
    public static String extractSlackShareTs(String json) {
        try {
            Object root = SimpleJson.parse(json);
            Object shares = findFirstValueByKey(root, "shares");
            if (shares == null) {
                return null;
            }
            return findFirstStringByKey(shares, "ts");
        } catch (Exception e) {
            return null;
        }
    }

    private static String findFirstStringByKey(Object node, String key) {
        Object value = findFirstValueByKey(node, key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static Object findFirstValueByKey(Object node, String key) {
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            if (map.containsKey(key)) {
                return map.get(key);
            }
            for (Object value : map.values()) {
                Object found = findFirstValueByKey(value, key);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                Object found = findFirstValueByKey(item, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
