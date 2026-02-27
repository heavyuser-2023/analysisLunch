package analysislunch.infrastructure.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

import analysislunch.domain.model.MenuInfo;
import analysislunch.utils.HttpUtils;
import analysislunch.utils.JsonUtils;

/**
 * Google Gemini API와 통신하는 클라이언트 클래스.
 *
 * <p>메뉴 이미지 OCR, 음식 이미지 생성, 칼로리 분석 기능을 제공합니다.
 */
@Slf4j
public class GeminiClient {

    private static final String API_URL_TEXT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String API_URL_IMAGE =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent";
    private static final String TEMP_GENERATED_FILE = "generated_food.png";
    private static final String MIME_TYPE_JPEG = "image/jpeg";
    private static final String MIME_TYPE_PNG = "image/png";
    private static final String FALLBACK_DATE = "날짜 없음";

    private static final Gson GSON = new Gson();

    private final String apiKey;

    /**
     * GeminiClient 생성자.
     *
     * @param apiKey Gemini API 키
     */
    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 메뉴 이미지에서 날짜와 메뉴 텍스트를 추출합니다 (OCR).
     *
     * @param imageFile OCR을 수행할 이미지 파일
     * @return 추출된 날짜와 메뉴 정보를 담은 {@link MenuInfo}
     * @throws IOException 이미지 읽기 또는 API 호출 실패 시
     */
    public MenuInfo extractMenuInfo(File imageFile) throws IOException {
        String base64Image = encodeImageToBase64(imageFile);

        String prompt = "이 이미지는 구내식당 메뉴판입니다. 오늘의 날짜와 메뉴 내용을 추출해주세요. "
            + "첫 번째 줄에는 날짜만 적고, 두 번째 줄에는 메뉴 이름만 쉼표로 구분해서 작성해주세요. "
            + "설명이나 다른 말은 하지 마세요.";

        String jsonBody = GSON.toJson(buildTextWithImageRequest(prompt, base64Image, MIME_TYPE_JPEG));

        String response = HttpUtils.postJson(API_URL_TEXT + "?key=" + apiKey, null, jsonBody);
        log.info("메뉴 텍스트 추출 응답 수신 완료");

        String fullText = JsonUtils.extractGeminiText(response);
        String[] lines = fullText.trim().split("\n", 2);
        if (lines.length >= 2) {
            return new MenuInfo(lines[0].trim(), lines[1].trim());
        }
        return new MenuInfo(FALLBACK_DATE, fullText.trim());
    }

    /**
     * 메뉴 텍스트를 기반으로 한국식 식판 음식 이미지를 생성합니다.
     *
     * @param menuText 쉼표로 구분된 메뉴 텍스트
     * @return 생성된 이미지 파일
     * @throws IOException 이미지 생성 API 호출 실패 또는 이미지 데이터 추출 실패 시
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
            - 식탁은 흰색 테이블 깔끔한 6인용 테이블

            ⛔⛔⛔ 절대 금지 (위반 시 실패) ⛔⛔⛔
            1. 정의된 6칸 외 추가 칸 생성 금지
            2. 이미지 위 텍스트/라벨/음식명 표시 절대 금지
            3. 식판 외부에 음식/장식 배치 금지
            4. 메뉴 목록 중 이미지 생성 누락 금지
            """, menuText);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(textPart);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonArray responseModalities = new JsonArray();
        responseModalities.add("IMAGE");
        responseModalities.add("TEXT");
        JsonObject generationConfig = new JsonObject();
        generationConfig.add("responseModalities", responseModalities);

        JsonObject requestBody = new JsonObject();
        requestBody.add("contents", contents);
        requestBody.add("generationConfig", generationConfig);

        String jsonBody = GSON.toJson(requestBody);

        String response = HttpUtils.postJson(API_URL_IMAGE + "?key=" + apiKey, null, jsonBody);
        log.info("이미지 생성 응답 수신 완료 (길이: {})", response.length());

        String base64Image = JsonUtils.extractImageData(response);
        if (base64Image == null || base64Image.isEmpty()) {
            throw new IOException(
                "이미지 생성 실패. 응답: " + response.substring(0, Math.min(500, response.length()))
            );
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        File outputFile = new File(TEMP_GENERATED_FILE);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(imageBytes);
        }

        log.info("생성된 이미지 저장 완료: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 생성된 음식 이미지를 기반으로 칼로리를 분석합니다.
     *
     * <p>메뉴 텍스트를 참고하여 정확한 음식 이름을 사용합니다.
     *
     * @param imageFile  분석할 음식 이미지 파일
     * @param menuText   메뉴 텍스트 (칼로리 분석 기준)
     * @return 마크다운 표 형식의 칼로리 분석 결과 문자열
     * @throws IOException 이미지 읽기 또는 API 호출 실패 시
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

        String jsonBody = GSON.toJson(buildTextWithImageRequest(prompt, base64Image, MIME_TYPE_PNG));

        String response = HttpUtils.postJson(API_URL_TEXT + "?key=" + apiKey, null, jsonBody);
        log.info("칼로리 분석 응답 수신 완료");

        return JsonUtils.extractGeminiText(response);
    }

    /**
     * 텍스트 프롬프트와 인라인 이미지를 포함한 Gemini API 요청 객체를 생성합니다.
     *
     * @param prompt    텍스트 프롬프트
     * @param base64Data Base64 인코딩된 이미지 데이터
     * @param mimeType  이미지 MIME 타입 (예: "image/jpeg")
     * @return Gson으로 직렬화 가능한 요청 {@link JsonObject}
     */
    private JsonObject buildTextWithImageRequest(String prompt, String base64Data, String mimeType) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", mimeType);
        inlineData.addProperty("data", base64Data);
        JsonObject imagePart = new JsonObject();
        imagePart.add("inline_data", inlineData);

        JsonArray parts = new JsonArray();
        parts.add(textPart);
        parts.add(imagePart);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject requestBody = new JsonObject();
        requestBody.add("contents", contents);
        return requestBody;
    }

    /**
     * 파일을 Base64 문자열로 인코딩합니다.
     *
     * @param file 인코딩할 파일
     * @return Base64 인코딩된 문자열
     * @throws IOException 파일 읽기 실패 시
     */
    private String encodeImageToBase64(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }
}
