package analysislunch.config;

/**
 * 애플리케이션 환경 변수 설정을 관리하는 불변 클래스.
 *
 * <p>{@link #load()} 팩토리 메서드를 통해 환경 변수를 읽고 유효성을 검증합니다.
 */
public class AppConfig {

    private static final String ENV_SLACK_BOT_TOKEN = "SLACK_BOT_TOKEN";
    private static final String ENV_SLACK_CHANNEL_ID = "SLACK_CHANNEL_ID";
    private static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    private static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";
    private static final String ENV_GITHUB_REPO = "GITHUB_REPO";
    private static final String ENV_GOOGLE_CHAT_WEBHOOK_URL = "GOOGLE_CHAT_WEBHOOK_URL";

    private final String botToken;
    private final String channelId;
    private final String geminiApiKey;
    private final String githubToken;
    private final String githubRepo;
    private final String googleChatWebhook;

    /**
     * AppConfig 생성자.
     *
     * @param botToken         Slack 봇 토큰
     * @param channelId        Slack 채널 ID
     * @param geminiApiKey     Gemini API 키
     * @param githubToken      GitHub 개인 액세스 토큰
     * @param githubRepo       GitHub 저장소 (예: "owner/repo")
     * @param googleChatWebhook Google Chat Webhook URL
     */
    public AppConfig(
            String botToken,
            String channelId,
            String geminiApiKey,
            String githubToken,
            String githubRepo,
            String googleChatWebhook) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.geminiApiKey = geminiApiKey;
        this.githubToken = githubToken;
        this.githubRepo = githubRepo;
        this.googleChatWebhook = googleChatWebhook;
    }

    /**
     * 환경 변수에서 설정을 읽어 {@link AppConfig} 인스턴스를 생성합니다.
     *
     * @return 유효성이 검증된 {@link AppConfig} 인스턴스
     * @throws IllegalStateException 필수 환경 변수가 누락되었을 때
     */
    public static AppConfig load() {
        String botToken = requireEnv(ENV_SLACK_BOT_TOKEN);
        String channelId = requireEnv(ENV_SLACK_CHANNEL_ID);
        String geminiApiKey = requireEnv(ENV_GEMINI_API_KEY);
        String githubToken = requireEnv(ENV_GITHUB_TOKEN);
        String githubRepo = requireEnv(ENV_GITHUB_REPO);
        String googleChatWebhook = requireEnv(ENV_GOOGLE_CHAT_WEBHOOK_URL);

        return new AppConfig(botToken, channelId, geminiApiKey, githubToken, githubRepo, googleChatWebhook);
    }

    /**
     * 환경 변수 값을 읽고, 누락된 경우 예외를 던집니다.
     *
     * @param key 환경 변수 키
     * @return 환경 변수 값
     * @throws IllegalStateException 환경 변수가 null이거나 비어있을 때
     */
    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("필수 환경 변수 누락: " + key);
        }
        return value;
    }

    /**
     * Slack 봇 토큰을 반환합니다.
     *
     * @return Slack 봇 토큰
     */
    public String getBotToken() {
        return botToken;
    }

    /**
     * Slack 채널 ID를 반환합니다.
     *
     * @return Slack 채널 ID
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * Gemini API 키를 반환합니다.
     *
     * @return Gemini API 키
     */
    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    /**
     * GitHub 토큰을 반환합니다.
     *
     * @return GitHub 개인 액세스 토큰
     */
    public String getGithubToken() {
        return githubToken;
    }

    /**
     * GitHub 저장소를 반환합니다.
     *
     * @return GitHub 저장소 (예: "owner/repo")
     */
    public String getGithubRepo() {
        return githubRepo;
    }

    /**
     * Google Chat Webhook URL을 반환합니다.
     *
     * @return Google Chat Webhook URL
     */
    public String getGoogleChatWebhook() {
        return googleChatWebhook;
    }
}
