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

/**
 * OpenAI API와 통신하는 클라이언트 클래스.
 *
 * <p>메뉴 이미지 OCR, 음식 이미지 생성, 칼로리 분석 기능을 제공합니다.
 */
@Slf4j
public class OpenAiClient implements AiClient {

    private static final String API_URL_TEXT =
        "https://api.openai.com/v1/chat/completions";
    private static final String API_URL_IMAGE =
        "https://api.openai.com/v1/images/generations";
    private static final String TEXT_MODEL = "gpt-6-luna";
    private static final String IMAGE_MODEL = "gpt-image-2.6-sunburst";
    private static final String TEMP_GENERATED_FILE = "generated_food.png";
    private static final String MIME_TYPE_JPEG = "image/jpeg";
    private static final String MIME_TYPE_PNG = "image/png";
    private static final String FALLBACK_DATE = "날짜 없음";

    /** OpenAI 일시 오류 시 최대 시도 횟수 (최초 시도 포함). */
    private static final int MAX_ATTEMPTS = 3;
    /** 재시도 초기 대기 시간(ms). 시도마다 2배씩 증가. */
    private static final long INITIAL_BACKOFF_MS = 2000L;
    /** 재시도 대기 시간 상한(ms). */
    private static final long MAX_BACKOFF_MS = 16000L;
    /** 로그에 출력할 응답 본문 최대 길이. */
    private static final int LOG_RESPONSE_LIMIT = 500;

    private static final Gson GSON = new Gson();

    private final String apiKey;

    /**
     * OpenAiClient 생성자.
     *
     * @param apiKey OpenAI API 키
     */
    public OpenAiClient(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
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

        String prompt = LunchPrompts.MENU_OCR;

        String jsonBody = GSON.toJson(buildTextWithImageRequest(prompt, base64Image, MIME_TYPE_JPEG));

        String response = callOpenAiWithRetry(API_URL_TEXT, jsonBody, "메뉴 OCR");
        log.info("메뉴 텍스트 추출 응답 수신 완료");
        log.debug("메뉴 OCR 원본 응답: {}", response);

        String fullText = OpenAiResponseParser.extractOpenAiText(response);
        if (fullText == null || fullText.isBlank() || OpenAiResponseParser.FALLBACK_TEXT.equals(fullText.trim())) {
            log.warn("메뉴 OCR 실패. OpenAI 원본 응답: {}",
                response.substring(0, Math.min(2000, response.length())));
            throw new IOException("메뉴 OCR 실패: OpenAI 응답에서 유효한 텍스트를 추출할 수 없습니다.");
        }

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
        String prompt = LunchPrompts.foodImage(menuText);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", IMAGE_MODEL);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("quality", "medium");
        requestBody.addProperty("complexity", "medium");
        requestBody.addProperty("size", "1024x1024");
        requestBody.addProperty("n", 1);
        requestBody.addProperty("output_format", "png");

        String jsonBody = GSON.toJson(requestBody);

        String response = callOpenAiWithRetry(API_URL_IMAGE, jsonBody, "이미지 생성");
        log.info("이미지 생성 응답 수신 완료 (길이: {})", response.length());

        String base64Image = OpenAiResponseParser.extractImageData(response);
        if (base64Image == null || base64Image.isEmpty()) {
            throw new IOException(
                "이미지 생성 실패. 응답: " + response.substring(0, Math.min(500, response.length()))
            );
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Image);
        } catch (IllegalArgumentException e) {
            throw new IOException("이미지 생성 실패: OpenAI 응답의 Base64 이미지가 유효하지 않습니다.", e);
        }
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

        String prompt = LunchPrompts.calories(menuText);

        String jsonBody = GSON.toJson(buildTextWithImageRequest(prompt, base64Image, MIME_TYPE_PNG));

        String response = callOpenAiWithRetry(API_URL_TEXT, jsonBody, "칼로리 분석");
        log.info("칼로리 분석 응답 수신 완료");

        String result = OpenAiResponseParser.extractOpenAiText(response);
        if (result == null || result.isBlank() || OpenAiResponseParser.FALLBACK_TEXT.equals(result.trim())) {
            log.warn("칼로리 분석 실패. OpenAI 원본 응답: {}",
                response.substring(0, Math.min(2000, response.length())));
            throw new IOException("칼로리 분석 실패: OpenAI 응답에서 유효한 텍스트를 추출할 수 없습니다.");
        }
        return result;
    }

    /**
     * OpenAI API를 호출하되, 일시적 오류(rate_limit_exceeded/server_error)나
     * 네트워크 예외 발생 시 지수 백오프로 재시도합니다.
     *
     * <p>재시도 대상이 아닌 API 오류(인증 실패, 크레딧 소진 등)이거나
     * 최종 시도까지 오류가 지속되면 구체적인 원인을 담은 {@link IOException}을 던집니다.
     *
     * @param url      요청 URL
     * @param jsonBody 요청 본문 JSON
     * @param taskName 로그 식별용 작업 이름
     * @return OpenAI 응답 본문 문자열
     * @throws IOException API 오류 응답 또는 마지막 시도까지 네트워크 오류가 지속될 때
     */
    private String callOpenAiWithRetry(String url, String jsonBody, String taskName) throws IOException {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                String response = HttpUtils.postJson(url, apiKey, jsonBody);
                if (!isTransientError(response) || attempt >= MAX_ATTEMPTS) {
                    throwIfApiError(response, taskName);
                    return response;
                }
                log.warn("{} OpenAI 일시 오류 (시도 {}/{}). {}ms 후 재시도. 응답: {}",
                    taskName, attempt, MAX_ATTEMPTS, backoffMs,
                    response.substring(0, Math.min(LOG_RESPONSE_LIMIT, response.length())));
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("{} 네트워크 오류 (시도 {}/{}). {}ms 후 재시도: {}",
                    taskName, attempt, MAX_ATTEMPTS, backoffMs, e.getMessage());
            }
            sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }

    /**
     * OpenAI 응답에 {@code error} 객체가 포함되어 있으면 상세 원인을 담아 {@link IOException}을 던집니다.
     *
     * @param response OpenAI 응답 본문 문자열
     * @param taskName 로그 식별용 작업 이름
     * @throws IOException OpenAI API 오류 응답일 때
     */
    private void throwIfApiError(String response, String taskName) throws IOException {
        if (response == null || response.isBlank()) {
            return;
        }
        try {
            JsonObject obj = GSON.fromJson(response, JsonObject.class);
            if (obj == null || !obj.has("error") || !obj.get("error").isJsonObject()) {
                return;
            }
            JsonObject error = obj.getAsJsonObject("error");
            String code = error.has("code") && !error.get("code").isJsonNull()
                ? error.get("code").getAsString() : "UNKNOWN";
            String status = error.has("type") ? error.get("type").getAsString() : "UNKNOWN";
            String message = error.has("message") ? error.get("message").getAsString() : response;
            log.error("{} OpenAI API 오류 응답 (code={}, status={}): {}", taskName, code, status, message);
            throw new IOException(
                String.format("%s 실패 - OpenAI API 오류 (%s %s): %s", taskName, code, status, message)
            );
        } catch (RuntimeException e) {
            // JSON 파싱 실패 시 호출부의 기존 응답 검증 로직에 위임
        }
    }

    /**
     * OpenAI 응답이 재시도 가능한 일시적 오류인지 판별합니다.
     *
     * <p>응답이 비어 있거나, {@code error.code}가 rate_limit_exceeded/server_error이거나
     * {@code error.type}이 server_error이면 일시 오류로 봅니다.
     *
     * @param response OpenAI 응답 본문 문자열
     * @return 일시적 오류이면 {@code true}
     */
    private boolean isTransientError(String response) {
        if (response == null || response.isBlank()) {
            return true;
        }
        try {
            JsonObject obj = GSON.fromJson(response, JsonObject.class);
            if (obj == null || !obj.has("error") || !obj.get("error").isJsonObject()) {
                return false;
            }
            JsonObject error = obj.getAsJsonObject("error");
            String code = error.has("code") && !error.get("code").isJsonNull()
                ? error.get("code").getAsString() : "UNKNOWN";
            String status = error.has("type") ? error.get("type").getAsString() : "";
            return "rate_limit_exceeded".equals(code)
                || "server_error".equals(code)
                || "server_error".equals(status);
        } catch (RuntimeException e) {
            // 파싱 불가한 응답은 인식된 일시 오류가 아니므로 재시도하지 않음
            return false;
        }
    }

    /**
     * 인터럽트 상태를 보존하며 지정한 시간만큼 대기합니다.
     *
     * @param ms 대기 시간(ms)
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 텍스트 프롬프트와 이미지 data URL을 포함한 OpenAI API 요청 객체를 생성합니다.
     *
     * @param prompt    텍스트 프롬프트
     * @param base64Data Base64 인코딩된 이미지 데이터
     * @param mimeType  이미지 MIME 타입 (예: "image/jpeg")
     * @return Gson으로 직렬화 가능한 요청 {@link JsonObject}
     */
    private JsonObject buildTextWithImageRequest(String prompt, String base64Data, String mimeType) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", prompt);

        JsonObject imageUrl = new JsonObject();
        imageUrl.addProperty("url", "data:" + mimeType + ";base64," + base64Data);
        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        imagePart.add("image_url", imageUrl);

        JsonArray parts = new JsonArray();
        parts.add(textPart);
        parts.add(imagePart);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", parts);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", TEXT_MODEL);
        requestBody.add("messages", messages);
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
