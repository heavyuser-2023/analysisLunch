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

    private static final String BLOG_URL = "https://m.blog.naver.com/yjm3038/222191646255";
    private static final String TEMP_ORIGINAL_FILE = "temp_original.png";
    private static final String TEMP_PROCESSED_FILE = "lunch_menu_white_bg.jpg";

    public static void main(String[] args) {
        try {
            Config config = Config.load();
            
            System.out.println("Processing started...");
            
            // 1. Extract image URL from blog page
            System.out.println("- Extracting image URL from blog...");
            String imageUrl = extractImageUrlFromBlog(BLOG_URL);
            System.out.println("  Found image URL: " + imageUrl);
            
            // 2. Download Image
            System.out.println("- Downloading image...");
            File originalFile = new File(TEMP_ORIGINAL_FILE);
            ImageProcessor.download(imageUrl, originalFile);
            
            // 3. Process Image (Remove Transparency)
            System.out.println("- Processing image (adding white background)...");
            File processedFile = new File(TEMP_PROCESSED_FILE);
            ImageProcessor.convertPngToWhiteBgJpg(originalFile, processedFile);
            
            // 4. Extract menu text from image
            System.out.println("- Extracting menu text from image...");
            GeminiClient geminiClient = new GeminiClient(config.geminiApiKey);
            MenuInfo menuInfo = geminiClient.extractMenuInfo(processedFile);
            System.out.println("Extracted Date: " + menuInfo.date());
            System.out.println("Extracted Menu: " + menuInfo.menu());
            
            // 5. Generate food tray image
            System.out.println("- Generating food tray image with Gemini...");
            File generatedImage = geminiClient.generateFoodImage(menuInfo.menu());
            
            // 6. Upload to Slack
            System.out.println("- Uploading generated image to Slack...");
            SlackClient slackClient = new SlackClient(config.botToken);
            String title = "오늘의 점심 메뉴";
            String initialComment = "📢 *오늘의 점심 메뉴 (" + menuInfo.date() + ")*" + "\n\n AI가 생성한 이미지 입니다. 실제 음식과 다를 수 있습니다." + "\n\n" + menuInfo.menu();
            
            slackClient.uploadFile(config.channelId, generatedImage, title, initialComment);
            
            System.out.println("✅ Task completed successfully.");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Cleanup temp files
            deleteFile(TEMP_ORIGINAL_FILE);
            deleteFile(TEMP_PROCESSED_FILE);
            deleteFile("generated_food.png");
        }
    }

    private static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 네이버 블로그 페이지에서 se-module-image 클래스의 img 태그 URL을 추출
     */
    private static String extractImageUrlFromBlog(String blogUrl) throws IOException {
        // 네이버 모바일 블로그 HTML 가져오기
        String html = HttpUtils.getHtml(blogUrl);
        
        // se-module se-module-image 클래스를 찾고 그 안의 img 태그의 src 추출
        // 패턴: class="se-module se-module-image" ... <img ... src="..." 또는 data-lazy-src="..."
        Pattern modulePattern = Pattern.compile(
            "class=\"se-module se-module-image\"[^>]*>[\\s\\S]*?<img[^>]+(?:data-lazy-src|src)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = modulePattern.matcher(html);
        if (matcher.find()) {
            String imageUrl = matcher.group(1);
            // URL이 상대경로인 경우 처리
            if (imageUrl.startsWith("//")) {
                imageUrl = "https:" + imageUrl;
            }
            return imageUrl;
        }
        
        throw new IOException("블로그 페이지에서 이미지를 찾을 수 없습니다: " + blogUrl);
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
     * Data carrier for Menu and Date
     */
    static class MenuInfo {
        private final String date;
        private final String menu;

        MenuInfo(String date, String menu) {
            this.date = date;
            this.menu = menu;
        }

        String date() { return date; }
        String menu() { return menu; }
    }

    /**
     * Gemini API Client - 메뉴 텍스트 추출 및 이미지 생성
     */
    static class GeminiClient {
        private static final String API_URL_TEXT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
        private static final String API_URL_IMAGE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-pro-image-preview:generateContent";
        private static final String TEMP_GENERATED_FILE = "generated_food.png";
        private final String apiKey;

        GeminiClient(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 메뉴 이미지에서 날짜와 텍스트 추출 (OCR)
         */
        MenuInfo extractMenuInfo(File imageFile) throws IOException {
            String base64Image = encodeImageToBase64(imageFile);
            
            String prompt = "이 이미지는 구내식당 메뉴판입니다. 오늘의 날짜와 메뉴 내용을 추출해주세요. 첫 번째 줄에는 날짜만 적고, 두 번째 줄에는 메뉴 이름만 쉼표로 구분해서 작성해주세요. 설명이나 다른 말은 하지 마세요.";
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            
            String jsonBody = "{"
                + "\"contents\": [{"
                + "\"parts\": ["
                + "{\"text\": \"" + escapedPrompt + "\"},"
                + "{\"inline_data\": {\"mime_type\": \"image/jpeg\", \"data\": \"" + base64Image + "\"}}"
                + "]"
                + "}]"
                + "}";

            String response = HttpUtils.postJson(API_URL_TEXT + "?key=" + apiKey, null, jsonBody);
            System.out.println("Menu Text Extraction Response: " + response);
            
            String fullText = JsonUtils.extractGeminiText(response);
            // 줄바꿈으로 날짜와 메뉴 분리
            String[] lines = fullText.trim().split("\n", 2);
            if (lines.length >= 2) {
                return new MenuInfo(lines[0].trim(), lines[1].trim());
            } else {
                // 분리에 실패한 경우 전체를 메뉴로 간주
                return new MenuInfo("날짜 없음", fullText.trim());
            }
        }

        /**
         * 메뉴 텍스트를 기반으로 식판 이미지 생성
         */
        File generateFoodImage(String menuText) throws IOException {
            String prompt = String.format("""
                당신은 한국 구내식당 음식 사진 전문가입니다.
                다음 메뉴를 한국식 6칸 식판에 담긴 실제 음식 사진처럼 생성해주세요.

                메뉴: %s

                === 식판 구조 ===
                - 한국식 플라스틱 식판 (약한 회색 바탕, 검은 점 산재)
                - 오른쪽: 수저/젓가락 칸 + 상단에 소스용 작은 칸
                - 하단: 밥칸(네모) + 국칸(동그라미, 별도 그릇)
                - 상단: 반찬 3칸 (좌우 동그라미, 가운데는 네모칸이고 좌우로 2등분)

                === 촬영 조건 ===
                - Top-down 시점, 흰색 테이블 배경
                - 자연광, 사실적 질감, 고해상도
                - 반찬이 많으면 한 칸에 여러 음식 배치 가능

                ⛔⛔⛔ 절대 금지 (위반 시 실패) ⛔⛔⛔
                1. 정의된 6칸 외 추가 칸 생성 금지
                2. 이미지 위 텍스트/라벨/음식명 표시 절대 금지
                3. 식판 외부에 음식/장식 배치 금지
                """, menuText);
            
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            
            String jsonBody = "{"
                + "\"contents\": [{"
                + "\"parts\": [{\"text\": \"" + escapedPrompt + "\"}]"
                + "}],"
                + "\"generationConfig\": {"
                + "\"responseModalities\": [\"IMAGE\", \"TEXT\"]"
                + "}"
                + "}";

            String response = HttpUtils.postJson(API_URL_IMAGE + "?key=" + apiKey, null, jsonBody);
            System.out.println("Image Generation Response received (length: " + response.length() + ")");
            
            // Extract base64 image data from response
            String base64Image = JsonUtils.extractImageData(response);
            if (base64Image == null || base64Image.isEmpty()) {
                throw new IOException("Failed to generate image. Response: " + response.substring(0, Math.min(500, response.length())));
            }
            
            // Decode and save image
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
            File outputFile = new File(TEMP_GENERATED_FILE);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(imageBytes);
            }
            
            System.out.println("Generated image saved: " + outputFile.getAbsolutePath());
            return outputFile;
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

        /**
         * HTML 페이지 가져오기 (브라우저 User-Agent 포함)
         */
        static String getHtml(String urlStr) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            conn.setInstanceFollowRedirects(true);
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

        /**
         * Extract base64 image data from Gemini response
         */
        static String extractImageData(String json) {
            // Look for inlineData -> data field containing base64 image
            Pattern pattern = Pattern.compile("\"data\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }
    }
}