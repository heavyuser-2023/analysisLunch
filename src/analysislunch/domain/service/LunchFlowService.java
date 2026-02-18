package analysislunch.domain.service;

import analysislunch.config.AppConfig;
import analysislunch.domain.model.MenuInfo;
import analysislunch.infrastructure.client.GeminiClient;
import analysislunch.infrastructure.client.GitHubClient;
import analysislunch.infrastructure.client.GoogleChatClient;
import analysislunch.infrastructure.client.SlackClient;
import analysislunch.infrastructure.crawler.BlogCrawler;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * 점심 메뉴 분석 전체 흐름을 조율하는 서비스 클래스.
 *
 * <p>블로그 크롤링 → 이미지 다운로드 → 메뉴 추출 → 이미지 생성 → 칼로리 분석
 * → Slack/Google Chat 전송 → 해시 업데이트 순서로 실행됩니다.
 */
public class LunchFlowService {

    private static final Logger logger = Logger.getLogger(LunchFlowService.class.getName());

    private static final String BLOG_URL = "https://m.blog.naver.com/yjm3038/222191646255";
    private static final String TEMP_ORIGINAL_FILE = "temp_original.png";
    private static final String TEMP_PROCESSED_FILE = "lunch_menu_white_bg.jpg";
    private static final String GENERATED_FOOD_FILE = "generated_food.png";
    private static final String CALORIE_CARD_FILE = "calorie_card.png";
    private static final String LEGACY_FOOD_FILE = "final_food_with_calories.png";
    private static final String HASH_FILE = "menu_hash.txt";
    private static final String FOOD_IMAGE_PREFIX = "lunch_food_";
    private static final String CARD_IMAGE_PREFIX = "lunch_card_";
    private static final String IMAGE_EXTENSION = ".png";
    private static final String MENU_TITLE_SUFFIX = " - 점심 메뉴";
    private static final long GOOGLE_CHAT_SEND_DELAY_MS = 1000L;

    private final AppConfig config;
    private final ImageService imageService;
    private final BlogCrawler blogCrawler;
    private final GeminiClient geminiClient;
    private final SlackClient slackClient;
    private final GitHubClient gitHubClient;
    private final GoogleChatClient googleChatClient;

    /**
     * LunchFlowService 생성자.
     *
     * @param config           애플리케이션 설정
     * @param imageService     이미지 처리 서비스
     * @param blogCrawler      블로그 크롤러
     * @param geminiClient     Gemini API 클라이언트
     * @param slackClient      Slack API 클라이언트
     * @param gitHubClient     GitHub API 클라이언트
     * @param googleChatClient Google Chat API 클라이언트
     */
    public LunchFlowService(
            AppConfig config,
            ImageService imageService,
            BlogCrawler blogCrawler,
            GeminiClient geminiClient,
            SlackClient slackClient,
            GitHubClient gitHubClient,
            GoogleChatClient googleChatClient) {
        this.config = config;
        this.imageService = imageService;
        this.blogCrawler = blogCrawler;
        this.geminiClient = geminiClient;
        this.slackClient = slackClient;
        this.gitHubClient = gitHubClient;
        this.googleChatClient = googleChatClient;
    }

    /**
     * 점심 메뉴 분석 전체 흐름을 실행합니다.
     *
     * <p>이미지 변경이 없으면 조기 종료합니다.
     * 모든 작업 완료 후 임시 파일을 정리합니다.
     */
    public void run() {
        try {
            logger.info("처리 시작...");

            // 1. 블로그에서 이미지 URL 추출
            logger.info("블로그에서 이미지 URL 추출 중...");
            String imageUrl = blogCrawler.extractImageUrlFromBlog(BLOG_URL);
            logger.info("이미지 URL 발견: " + imageUrl);

            // 2. 이미지 다운로드
            logger.info("이미지 다운로드 중...");
            File originalFile = new File(TEMP_ORIGINAL_FILE);
            imageService.download(imageUrl, originalFile);

            // 3. 해시 비교 (변경 없으면 조기 종료)
            String currentHash = imageService.calculateFileHash(originalFile);
            String lastHash = imageService.loadLastHash();
            if (currentHash.equals(lastHash)) {
                logger.info("✅ 이미지가 변경되지 않았습니다. 작업을 중단합니다. (Hash: " + currentHash + ")");
                return;
            }
            logger.info("🔄 이미지가 변경되었습니다. (새 Hash: " + currentHash + ")");

            // 4. 이미지 전처리 (투명 배경 → 흰색 배경)
            logger.info("이미지 전처리 중 (흰색 배경 추가)...");
            File processedFile = new File(TEMP_PROCESSED_FILE);
            imageService.convertPngToWhiteBgJpg(originalFile, processedFile);

            // 5. 이미지에서 메뉴 텍스트 추출
            logger.info("이미지에서 메뉴 텍스트 추출 중...");
            MenuInfo menuInfo = geminiClient.extractMenuInfo(processedFile);
            logger.info("추출된 날짜: " + menuInfo.date());
            logger.info("추출된 메뉴: " + menuInfo.menu());

            // 6. 식판 이미지 생성
            logger.info("Gemini로 식판 이미지 생성 중...");
            File generatedImage = geminiClient.generateFoodImage(menuInfo.menu());

            // 7. 칼로리 분석
            logger.info("칼로리 분석 중...");
            String calorieAnalysis = geminiClient.analyzeCalories(generatedImage, menuInfo.menu());
            logger.info(calorieAnalysis);

            // 8. 칼로리 카드 이미지 생성
            logger.info("칼로리 카드 이미지 생성 중...");
            File calorieCardFile = new File(CALORIE_CARD_FILE);
            imageService.createCalorieCard(calorieAnalysis, calorieCardFile);

            // 9. GitHub에 이미지 업로드 (Slack/Google Chat URL 확보)
            logger.info("GitHub에 이미지 업로드 중...");
            String title = menuInfo.date() + MENU_TITLE_SUFFIX;
            long timestamp = System.currentTimeMillis();
            String foodImageName = FOOD_IMAGE_PREFIX + timestamp + IMAGE_EXTENSION;
            String cardImageName = CARD_IMAGE_PREFIX + timestamp + IMAGE_EXTENSION;
            gitHubClient.uploadImage(generatedImage, foodImageName);
            gitHubClient.uploadImage(calorieCardFile, cardImageName);
            String foodImageUrl = gitHubClient.getRawUrl(foodImageName);
            String cardImageUrl = gitHubClient.getRawUrl(cardImageName);

            // 10. Slack 전송 (식판 이미지 → 칼로리 카드 답글)
            logger.info("Slack에 전송 중...");
            String slackMessage = "📢 *" + title + "*\n\n AI가 생성한 이미지 입니다. 실제 음식과 다를 수 있습니다.\n\n" + menuInfo.menu();
            String slackThreadTs = slackClient.postImageMessage(
                config.getChannelId(), slackMessage, foodImageUrl, null
            );
            if (slackThreadTs == null) {
                logger.warning("Slack 부모 스레드 ts가 없습니다. 메시지가 별도로 전송됩니다.");
            }
            slackClient.postImageMessage(config.getChannelId(), "📊 *상세 칼로리 분석표*", cardImageUrl, slackThreadTs);
            logger.info("✅ Slack 스레드 전송 완료.");

            // 11. Google Chat 전송
            logger.info("Google Chat에 전송 중...");
            String chatThreadKey = "lunch-" + System.currentTimeMillis();
            googleChatClient.sendCard(foodImageUrl, title, slackMessage, chatThreadKey);
            logger.info("✅ Google Chat 식판 이미지 전송 완료.");

            waitForGoogleChatOrder();

            googleChatClient.sendCard(cardImageUrl, "상세 칼로리 분석", "📊 *상세 칼로리 분석표*", chatThreadKey);
            logger.info("✅ Google Chat 칼로리 카드 전송 완료.");

            // 12. 해시 저장 및 업로드 (모든 작업 성공 후)
            logger.info("🔄 모든 작업 완료. 해시 업데이트 중...");
            imageService.saveHash(currentHash);
            gitHubClient.uploadTextFile(currentHash, HASH_FILE);
            logger.info("✅ 작업이 성공적으로 완료되었습니다.");

        } catch (IOException e) {
            logger.severe("❌ 오류 발생: " + e.getMessage());
        } finally {
            cleanupTempFiles();
        }
    }

    /**
     * Google Chat 메시지 순서 보장을 위해 잠시 대기합니다.
     */
    private void waitForGoogleChatOrder() {
        try {
            Thread.sleep(GOOGLE_CHAT_SEND_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Google Chat 전송 대기 중 인터럽트 발생: " + e.getMessage());
        }
    }

    /**
     * 처리 과정에서 생성된 임시 파일들을 삭제합니다.
     */
    private void cleanupTempFiles() {
        imageService.deleteFile(TEMP_ORIGINAL_FILE);
        imageService.deleteFile(TEMP_PROCESSED_FILE);
        imageService.deleteFile(GENERATED_FOOD_FILE);
        imageService.deleteFile(CALORIE_CARD_FILE);
        imageService.deleteFile(LEGACY_FOOD_FILE);
    }
}
