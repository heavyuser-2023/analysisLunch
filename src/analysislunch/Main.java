package analysislunch;

import analysislunch.config.AppConfig;
import analysislunch.domain.service.ImageService;
import analysislunch.domain.service.LunchFlowService;
import analysislunch.infrastructure.client.GeminiClient;
import analysislunch.infrastructure.client.GitHubClient;
import analysislunch.infrastructure.client.GoogleChatClient;
import analysislunch.infrastructure.client.SlackClient;
import analysislunch.infrastructure.crawler.BlogCrawler;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 프로그램 시작: 점심 메뉴 확인 (Refactored)");
        
        try {
            // 1. Load Configuration
            AppConfig config = AppConfig.load();

            // 2. Initialize Infrastructure
            BlogCrawler blogCrawler = new BlogCrawler();
            ImageService imageService = new ImageService();
            GeminiClient geminiClient = new GeminiClient(config.getGeminiApiKey());
            SlackClient slackClient = new SlackClient(config.getBotToken());
            GitHubClient gitHubClient = new GitHubClient(config.getGithubToken(), config.getGithubRepo());
            GoogleChatClient googleChatClient = new GoogleChatClient(config.getGoogleChatWebhook());

            // 3. Initialize Service
            LunchFlowService flowService = new LunchFlowService(
                config,
                imageService,
                blogCrawler,
                geminiClient,
                slackClient,
                gitHubClient,
                googleChatClient
            );

            // 4. Run Application Flow
            flowService.run();

        } catch (Exception e) {
            System.err.println("❌ Critical Error during initialization or execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
