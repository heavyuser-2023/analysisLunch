import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Downloads an image, processes it (white background), and sends it via Slack API.
 */
public class SlackSender {

    private static final String IMAGE_URL = "https://mblogthumb-phinf.pstatic.net/MjAyNTEyMzFfMjA3/MDAxNzY3MTYzMTM0MTE1.kFz3iaGWhYyUTEUP3PCY3_nVYvH74T_GRjChkSMW-zwg.beTQZBktpQcUw3fITP7lchshOtcaQEDq03rdngROlrYg.PNG/image.png?type=w800";
    private static final String TEMP_ORIGINAL_FILE = "temp_original.png";
    private static final String TEMP_PROCESSED_FILE = "lunch_menu_white_bg.jpg";

    public static void main(String[] args) {
        try {
            Config config = Config.load();
            
            System.out.println("Processing started...");
            
            // 1. Download Image
            System.out.println("- Downloading image...");
            File originalFile = new File(TEMP_ORIGINAL_FILE);
            ImageProcessor.download(IMAGE_URL, originalFile);
            
            // 2. Process Image (Remove Transparency)
            System.out.println("- Processing image (adding white background)...");
            File processedFile = new File(TEMP_PROCESSED_FILE);
            ImageProcessor.convertPngToWhiteBgJpg(originalFile, processedFile);
            
            // 3. Analyze with Gemini
            System.out.println("- Analyzing image with Gemini...");
            GeminiClient geminiClient = new GeminiClient(config.geminiApiKey);
            String analysisResult = geminiClient.analyze(processedFile);
            System.out.println("Analysis: " + analysisResult);
            
            // 4. Upload to Slack
            System.out.println("- Uploading to Slack...");
            SlackClient slackClient = new SlackClient(config.botToken);
            String title = "오늘의 점심 메뉴";
            String initialComment = "📢 *오늘의 점심 메뉴* (이미지 처리 완료)\n\n" + analysisResult;
            
            slackClient.uploadFile(config.channelId, processedFile, title, initialComment);
            
            System.out.println("✅ Task completed successfully.");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Cleanup temp files
            deleteFile(TEMP_ORIGINAL_FILE);
            deleteFile(TEMP_PROCESSED_FILE);
        }
    }

    private static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Configuration holder
     */
    static class Config {
        final String botToken;
        final String channelId;
        final String geminiApiKey;

        Config(String botToken, String channelId, String geminiApiKey) {
            this.botToken = botToken;
            this.channelId = channelId;
            this.geminiApiKey = geminiApiKey;
        }

        static Config load() {
            String botToken = System.getenv("SLACK_BOT_TOKEN");
            String channelId = System.getenv("SLACK_CHANNEL_ID");
            String geminiApiKey = System.getenv("GEMINI_API_KEY");

            if (botToken == null || botToken.isEmpty()) {
                throw new IllegalStateException("Missing environment variable: SLACK_BOT_TOKEN");
            }
            if (channelId == null || channelId.isEmpty()) {
                throw new IllegalStateException("Missing environment variable: SLACK_CHANNEL_ID");
            }
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                throw new IllegalStateException("Missing environment variable: GEMINI_API_KEY");
            }
            return new Config(botToken, channelId, geminiApiKey);
        }
    }

    /**
     * Gemini API Client
     */
    static class GeminiClient {
        private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent";
        private final String apiKey;

        GeminiClient(String apiKey) {
            this.apiKey = apiKey;
        }

        String analyze(File imageFile) throws IOException {
            String base64Image = encodeImageToBase64(imageFile);
            
            // JSON Payload manual construction is tricky with large base64, usually safe if no special chars in base64 (which is true)
            // But we need to be careful with the prompt text escaping if it had quotes.
            String prompt = """
                [Role] 너는 임상 영양학적 관점에서 식단을 분석하는 전문가야. 사진 속 음식을 분석하여 당뇨(혈당), 고혈압(나트륨), 고지혈증(지방), 다이어트(칼로리) 관리 측면에서 주의해야 할 성분 정보를 제공해줘.

                [Instruction]

                메뉴 구성: 사진에 담긴 주요 메뉴를 한 문장으로 요약해.

                질환별 핵심 주의 성분: 아래 세 가지 항목에 해당하는 메뉴와 이유를 핵심만 설명해.

                당질(탄수화물): 혈당 및 체중 관리에 영향을 주는 정제 탄수화물, 당분 함량 분석.

                나트륨: 혈압 및 부종에 영향을 주는 소금기, 장류, 국물 분석.

                지방: 혈관 건강에 영향을 주는 튀김, 포화지방, 고칼로리 부위 분석.

                [Constraint]

                실천 가이드나 조언(예: ~하세요, ~남기세요)은 절대 포함하지 말 것.

                오직 식단의 구성 성분이 건강 관리에 미치는 부정적 요인(위험 요소) 분석에만 집중할 것.

                전체 내용은 최대한 짧고 건조한 문체로 작성할 것.
                """;
            
            // Escape for JSON
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            
            String jsonBody = "{"
                + "\"contents\": [{"
                + "\"parts\": ["
                + "{\"text\": \"" + escapedPrompt + "\"},"
                + "{\"inline_data\": {\"mime_type\": \"image/jpeg\", \"data\": \"" + base64Image + "\"}}"
                + "]"
                + "}]"
                + "}";

            String response = HttpUtils.postJson(API_URL + "?key=" + apiKey, null, jsonBody);
            System.out.println("Gemini Raw Response: " + response);
            
            // Extract text from response deeply nested JSON
            return JsonUtils.extractGeminiText(response);
        }

        private String encodeImageToBase64(File file) throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                fis.read(bytes);
                return java.util.Base64.getEncoder().encodeToString(bytes);
            }
        }
    }

    /**
     * Slack API Client handling V2 Uploads
     */
    static class SlackClient {
        private static final String API_GET_URL = "https://slack.com/api/files.getUploadURLExternal";
        private static final String API_COMPLETE = "https://slack.com/api/files.completeUploadExternal";
        private final String token;

        SlackClient(String token) {
            this.token = token;
        }

        void uploadFile(String channelId, File file, String title, String initialComment) throws IOException {
            // Step 1: Get Upload URL
            String getUrlResponse = callGetUploadUrl(file.getName(), file.length());
            String uploadUrl = JsonUtils.extract(getUrlResponse, "upload_url");
            String fileId = JsonUtils.extract(getUrlResponse, "file_id");
            
            if (uploadUrl == null || fileId == null) {
                throw new IOException("Failed to get upload URL: " + getUrlResponse);
            }
            // Unescape URL if needed
            uploadUrl = uploadUrl.replace("\\/", "/");

            // Step 2: Upload Binary
            HttpUtils.uploadBinary(uploadUrl, file);

            // Step 3: Complete Upload
            String completeResponse = callCompleteUpload(fileId, title, initialComment, channelId);
            if (!completeResponse.contains("\"ok\":true")) {
                throw new IOException("Failed to complete upload: " + completeResponse);
            }
        }

        private String callGetUploadUrl(String filename, long length) throws IOException {
            String params = String.format("filename=%s&length=%d", 
                URLEncoder.encode(filename, StandardCharsets.UTF_8), length);
            return HttpUtils.get(API_GET_URL + "?" + params, token);
        }

        private String callCompleteUpload(String fileId, String title, String initialComment, String channelId) throws IOException {
            // JSON Escaping for initialComment
            String escapedComment = initialComment.replace("\"", "\\\"").replace("\n", "\\n");
            
            String jsonBody = String.format(
                "{\"files\":[{\"id\":\"%s\",\"title\":\"%s\"}],\"channel_id\":\"%s\",\"initial_comment\":\"%s\"}",
                fileId, title, channelId, escapedComment
            );
            return HttpUtils.postJson(API_COMPLETE, token, jsonBody);
        }
    }

    /**
     * Image Processing Utilities
     */
    static class ImageProcessor {
        static void download(String imageUrl, File destination) throws IOException {
            URL url = new URL(imageUrl);
            try (InputStream in = new BufferedInputStream(url.openStream());
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }

        static void convertPngToWhiteBgJpg(File input, File output) throws IOException {
            BufferedImage original = ImageIO.read(input);
            if (original == null) throw new IOException("Failed to read image: " + input.getName());

            BufferedImage newImage = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = newImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
            g2d.drawImage(original, 0, 0, null);
            g2d.dispose();

            ImageIO.write(newImage, "jpg", output);
        }
    }

    /**
     * HTTP Utilities
     */
    static class HttpUtils {
        static String get(String urlStr, String token) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            return readResponse(conn);
        }

        static String postJson(String urlStr, String token, String jsonBody) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        }

        static void uploadBinary(String uploadUrl, File file) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");

            try (OutputStream os = conn.getOutputStream();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            
            if (conn.getResponseCode() != 200) {
                throw new IOException("Binary upload failed with code: " + conn.getResponseCode());
            }
        }

        private static String readResponse(HttpURLConnection conn) throws IOException {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        }
    }

    /**
     * Simple JSON Utilities
     */
    static class JsonUtils {
        static String extract(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            return matcher.find() ? matcher.group(1) : null;
        }

        static String extractGeminiText(String json) {
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
    }
}