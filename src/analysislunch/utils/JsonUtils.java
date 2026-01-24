package analysislunch.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtils {
    public static String extract(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String extractGeminiText(String json) {
        // Improved regex to handle escaped quotes and characters inside JSON strings
        Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            // Return unescaped newlines and quotes
            String content = matcher.group(1);
            return content.replace("\\n", "\n").replace("\\\"", "\"");
        }
        return "분석 결과 없음";
    }

    /**
     * Extract base64 image data from Gemini response
     */
    public static String extractImageData(String json) {
        // Look for inlineData -> data field containing base64 image
        Pattern pattern = Pattern.compile("\"data\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
