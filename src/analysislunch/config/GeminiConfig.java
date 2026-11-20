package analysislunch.config;

/** Gemini API 인증 설정. */
public final class GeminiConfig {
    private static final String ENV_API_KEY = "GEMINI_API_KEY";

    private final String apiKey;

    /** 기존 직접 생성 경로와 동일하게 전달받은 키를 보관합니다. */
    public GeminiConfig(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 기존 환경 변수에서 필수 Gemini API 키를 읽습니다. */
    public static GeminiConfig load() {
        String value = System.getenv(ENV_API_KEY);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("필수 환경 변수 누락: " + ENV_API_KEY);
        }
        return new GeminiConfig(value.trim());
    }

    /** Gemini API 키를 반환합니다. */
    public String getApiKey() {
        return apiKey;
    }
}
