package analysislunch.config;

public class AppConfig {
    private final String botToken;
    private final String channelId;
    private final String geminiApiKey;
    private final String githubToken;
    private final String githubRepo;
    private final String googleChatWebhook;

    public AppConfig(String botToken, String channelId, String geminiApiKey, 
           String githubToken, String githubRepo, String googleChatWebhook) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.geminiApiKey = geminiApiKey;
        this.githubToken = githubToken;
        this.githubRepo = githubRepo;
        this.googleChatWebhook = googleChatWebhook;
    }

    public static AppConfig load() {
        String botToken = System.getenv("SLACK_BOT_TOKEN");
        String channelId = System.getenv("SLACK_CHANNEL_ID");
        String geminiApiKey = System.getenv("GEMINI_API_KEY");
        String githubToken = System.getenv("GITHUB_TOKEN");
        String githubRepo = System.getenv("GITHUB_REPO");
        String googleChatWebhook = System.getenv("GOOGLE_CHAT_WEBHOOK_URL");

        if (botToken == null || botToken.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: SLACK_BOT_TOKEN");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: SLACK_CHANNEL_ID");
        }
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: GEMINI_API_KEY");
        }
        if (githubToken == null || githubToken.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: GITHUB_TOKEN");
        }
        if (githubRepo == null || githubRepo.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: GITHUB_REPO");
        }
        if (googleChatWebhook == null || googleChatWebhook.isEmpty()) {
            throw new IllegalStateException("Missing environment variable: GOOGLE_CHAT_WEBHOOK_URL");
        }
        return new AppConfig(botToken, channelId, geminiApiKey, githubToken, githubRepo, googleChatWebhook);
    }

    public String getBotToken() { return botToken; }
    public String getChannelId() { return channelId; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public String getGithubToken() { return githubToken; }
    public String getGithubRepo() { return githubRepo; }
    public String getGoogleChatWebhook() { return googleChatWebhook; }
}
