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
    private static final String ENV_TELEGRAM_BOT_TOKEN = "TELEGRAM_BOT_TOKEN";
    private static final String ENV_TELEGRAM_CHAT_ID = "TELEGRAM_CHAT_ID";
    private static final String ENV_DISCORD_WEBHOOK_URL = "DISCORD_WEBHOOK_URL";
    private static final String ENV_INSTAGRAM_ACCESS_TOKEN = "INSTAGRAM_ACCESS_TOKEN";
    private static final String ENV_INSTAGRAM_BUSINESS_ACCOUNT_ID = "INSTAGRAM_BUSINESS_ACCOUNT_ID";

    private final String botToken;
    private final String channelId;
    private final String geminiApiKey;
    private final String githubToken;
    private final String githubRepo;
    private final String googleChatWebhook;
    private final String telegramBotToken;
    private final String telegramChatId;
    private final String discordWebhook;
    private final String instagramAccessToken;
    private final String instagramBusinessAccountId;

    /**
     * AppConfig 생성자.
     *
     * @param botToken         Slack 봇 토큰
     * @param channelId        Slack 채널 ID
     * @param geminiApiKey     Gemini API 키
     * @param githubToken      GitHub 개인 액세스 토큰
     * @param githubRepo       GitHub 저장소 (예: "owner/repo")
     * @param googleChatWebhook Google Chat Webhook URL
     * @param telegramBotToken Telegram 봇 토큰 (선택, 미설정 시 {@code null})
     * @param telegramChatId   Telegram 채팅 ID (선택, 미설정 시 {@code null})
     * @param discordWebhook   Discord Webhook URL (선택, 미설정 시 {@code null})
     * @param instagramAccessToken       Instagram Graph API 액세스 토큰 (선택, 미설정 시 {@code null})
     * @param instagramBusinessAccountId Instagram 비즈니스 계정 ID (선택, 미설정 시 {@code null})
     */
    public AppConfig(
            String botToken,
            String channelId,
            String geminiApiKey,
            String githubToken,
            String githubRepo,
            String googleChatWebhook,
            String telegramBotToken,
            String telegramChatId,
            String discordWebhook,
            String instagramAccessToken,
            String instagramBusinessAccountId) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.geminiApiKey = geminiApiKey;
        this.githubToken = githubToken;
        this.githubRepo = githubRepo;
        this.googleChatWebhook = googleChatWebhook;
        this.telegramBotToken = telegramBotToken;
        this.telegramChatId = telegramChatId;
        this.discordWebhook = discordWebhook;
        this.instagramAccessToken = instagramAccessToken;
        this.instagramBusinessAccountId = instagramBusinessAccountId;
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

        // 선택 채널: 미설정 시 해당 채널 전송을 건너뜁니다.
        String telegramBotToken = optionalEnv(ENV_TELEGRAM_BOT_TOKEN);
        String telegramChatId = optionalEnv(ENV_TELEGRAM_CHAT_ID);
        String discordWebhook = optionalEnv(ENV_DISCORD_WEBHOOK_URL);
        String instagramAccessToken = optionalEnv(ENV_INSTAGRAM_ACCESS_TOKEN);
        String instagramBusinessAccountId = optionalEnv(ENV_INSTAGRAM_BUSINESS_ACCOUNT_ID);

        return new AppConfig(
            botToken, channelId, geminiApiKey, githubToken, githubRepo, googleChatWebhook,
            telegramBotToken, telegramChatId, discordWebhook,
            instagramAccessToken, instagramBusinessAccountId);
    }

    /**
     * 환경 변수 값을 읽고, 없으면 {@code null}을 반환합니다 (선택 설정용).
     *
     * @param key 환경 변수 키
     * @return 환경 변수 값, 없거나 비어있으면 {@code null}
     */
    private static String optionalEnv(String key) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? null : value;
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

    /**
     * Telegram 봇 토큰을 반환합니다.
     *
     * @return Telegram 봇 토큰, 미설정 시 {@code null}
     */
    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    /**
     * Telegram 채팅 ID를 반환합니다.
     *
     * @return Telegram 채팅 ID, 미설정 시 {@code null}
     */
    public String getTelegramChatId() {
        return telegramChatId;
    }

    /**
     * Discord Webhook URL을 반환합니다.
     *
     * @return Discord Webhook URL, 미설정 시 {@code null}
     */
    public String getDiscordWebhook() {
        return discordWebhook;
    }

    /**
     * Telegram 전송에 필요한 설정이 모두 갖춰졌는지 확인합니다.
     *
     * @return 봇 토큰과 채팅 ID가 모두 설정되어 있으면 {@code true}
     */
    public boolean isTelegramEnabled() {
        return telegramBotToken != null && telegramChatId != null;
    }

    /**
     * Discord 전송에 필요한 설정이 갖춰졌는지 확인합니다.
     *
     * @return Webhook URL이 설정되어 있으면 {@code true}
     */
    public boolean isDiscordEnabled() {
        return discordWebhook != null;
    }

    /**
     * Instagram 액세스 토큰을 반환합니다.
     *
     * @return Instagram Graph API 액세스 토큰, 미설정 시 {@code null}
     */
    public String getInstagramAccessToken() {
        return instagramAccessToken;
    }

    /**
     * Instagram 비즈니스 계정 ID를 반환합니다.
     *
     * @return Instagram 비즈니스 계정 ID, 미설정 시 {@code null}
     */
    public String getInstagramBusinessAccountId() {
        return instagramBusinessAccountId;
    }

    /**
     * Instagram 게시에 필요한 설정이 모두 갖춰졌는지 확인합니다.
     *
     * @return 액세스 토큰과 비즈니스 계정 ID가 모두 설정되어 있으면 {@code true}
     */
    public boolean isInstagramEnabled() {
        return instagramAccessToken != null && instagramBusinessAccountId != null;
    }
}
