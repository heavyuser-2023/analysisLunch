package analysislunch.infrastructure.client;

import analysislunch.domain.model.MenuInfo;
import analysislunch.utils.HttpUtils;
import analysislunch.utils.JsonUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

public class GeminiClient {
    private static final String API_URL_TEXT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String API_URL_IMAGE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-pro-image-preview:generateContent";
    private static final String TEMP_GENERATED_FILE = "generated_food.png";
    private final String apiKey;

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 메뉴 이미지에서 날짜와 텍스트 추출 (OCR)
     */
    public MenuInfo extractMenuInfo(File imageFile) throws IOException {
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
    public File generateFoodImage(String menuText) throws IOException {
        String prompt = String.format("""
            당신은 한국 구내식당 음식 사진 전문가입니다.
            다음 메뉴를 한국식 6칸 식판에 담긴 실제 음식 사진처럼 생성해주세요.

            메뉴: %s

            === 식판 구조 ===
            - 한국식 플라스틱 식판 (약한 회색 바탕, 검은 점 산재)
            - 오른쪽: 수저/젓가락 칸 + 상단에 소스용 작은 칸
            - 하단: 밥칸(네모) + 국칸(동그라미, 별도 하얀색 멜라민 국그릇)
            - 상단: 반찬 3칸 (좌우 동그라미, 가운데는 네모칸이고 좌우로 2등분)

            === 촬영 조건 ===
            - Top-down 시점, 흰색 테이블 배경
            - 자연광, 사실적 질감, 고해상도
            - 반찬이 많으면 한 칸에 여러 음식 배치 가능
            - 식판에 음식이 담긴 모습이 사람이 실제 담은것 처럼
              (밥, 반찬이 정형적이지 않게 배치, 반찬이 조금 넘치기도 하고 ) 

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
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
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
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
}
