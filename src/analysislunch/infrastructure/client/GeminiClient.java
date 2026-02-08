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
            - 좌우: 식판 좌우 테두리 위 아래 각각 20퍼센트만 직선이고, 가운데 60퍼센트 정도가 살짝 라운드로 튀어나옴

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
            4. 메뉴 목록 중 이미지 생성 누락 금지
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

    /**
     * 생성된 음식 이미지를 기반으로 칼로리 분석
     * 메뉴 텍스트를 참고하여 정확한 음식 이름을 사용하도록 함
     */
    public String analyzeCalories(File imageFile, String menuText) throws IOException {
        String base64Image = encodeImageToBase64(imageFile);
        
        String prompt = String.format("""
            이 이미지는 다음 메뉴로 구성된 점심 도시락(식판) 사진입니다:
            %s
            
            위 메뉴 목록에 있는 '모든' 항목들에 대해 예상 칼로리를 분석해서 표(Table) 형태로 정리해주세요.
            
            [중요 규칙]
            1. 표의 '메뉴명'은 위에서 제공된 메뉴 이름을 그대로 사용해야 합니다. (임의로 바꾸거나 생략하지 마세요)
            2. 사진에 보이지 않거나 불분명하더라도, 메뉴 목록에 있다면 포함시켜주세요.
            3. 반찬이 여러 개라면 모든 반찬을 나열해주세요.
            
            출력 형식:
            | 메뉴명 | 예상 칼로리 |
            |---|---|
            | 쌀밥 | 300kcal |
            ...
            
            마지막에는 **총 예상 칼로리: XXXkcal** 형태로 합계를 적어주세요.
            설명은 생략하고 표와 합계만 간단히 출력하세요.
            """, menuText);
            
        String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

        String jsonBody = "{"
            + "\"contents\": [{"
            + "\"parts\": ["
            + "{\"text\": \"" + escapedPrompt + "\"},"
            + "{\"inline_data\": {\"mime_type\": \"image/png\", \"data\": \"" + base64Image + "\"}}"
            + "]"
            + "}]"
            + "}";

        // Vision 기능이 있는 모델 사용 (extractMenuInfo와 동일한 endpoint 재사용 가능)
        String response = HttpUtils.postJson(API_URL_TEXT + "?key=" + apiKey, null, jsonBody);
        System.out.println("Calorie Analysis Response received");

        return JsonUtils.extractGeminiText(response);
    }
}
