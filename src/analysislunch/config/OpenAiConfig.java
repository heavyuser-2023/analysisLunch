package analysislunch.config;

/** OpenAI API 인증 설정. */
public final class OpenAiConfig {
    private static final String ENV_API_KEY = "OPENAI_API_KEY";

    private final String apiKey;

    /** 전달받은 OpenAI API 키를 보관합니다. */
    public OpenAiConfig(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 선택한 공급자에 필요한 OpenAI API 키를 환경 변수에서 읽습니다. */
    public static OpenAiConfig load() {
        String value = System.getenv(ENV_API_KEY);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("필수 환경 변수 누락: " + ENV_API_KEY);
        }
        return new OpenAiConfig(value.trim());
    }

    /** OpenAI API 키를 반환합니다. */
    public String getApiKey() {
        return apiKey;
    }
}
