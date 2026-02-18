package analysislunch;

import java.util.logging.Level;
import java.util.logging.Logger;

import analysislunch.config.AppConfig;
import analysislunch.domain.service.ImageService;
import analysislunch.domain.service.LunchFlowService;
import analysislunch.infrastructure.client.GeminiClient;
import analysislunch.infrastructure.client.GitHubClient;
import analysislunch.infrastructure.client.GoogleChatClient;
import analysislunch.infrastructure.client.SlackClient;
import analysislunch.infrastructure.crawler.BlogCrawler;


/**
 * 점심 메뉴 분석 애플리케이션 진입점.
 *
 * <p>환경 변수를 로드하고 의존성을 초기화한 뒤 {@link LunchFlowService}를 실행합니다.
 */
public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    /**
     * 애플리케이션 메인 메서드.
     *
     * @param args 커맨드라인 인수 (사용하지 않음)
     */
    public static void main(String[] args) {
        logger.info("🚀 프로그램 시작: 점심 메뉴 확인");

        try {
            // 1. 설정 로드
            AppConfig config = AppConfig.load();

            // 2. 인프라 초기화
            BlogCrawler blogCrawler = new BlogCrawler();
            ImageService imageService = new ImageService();
            GeminiClient geminiClient = new GeminiClient(config.getGeminiApiKey());
            SlackClient slackClient = new SlackClient(config.getBotToken());
            GitHubClient gitHubClient = new GitHubClient(config.getGithubToken(), config.getGithubRepo());
            GoogleChatClient googleChatClient = new GoogleChatClient(config.getGoogleChatWebhook());

            // 3. 서비스 초기화
            LunchFlowService flowService = new LunchFlowService(
                config,
                imageService,
                blogCrawler,
                geminiClient,
                slackClient,
                gitHubClient,
                googleChatClient
            );

            // 4. 애플리케이션 실행
            flowService.run();


        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "❌ 초기화 실패", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ 치명적 오류 발생: [" + e.getClass().getSimpleName() + "]", e);
        }
    }
}
