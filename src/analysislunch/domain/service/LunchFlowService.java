package analysislunch.domain.service;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import analysislunch.config.AppConfig;
import analysislunch.domain.model.MenuInfo;
import analysislunch.infrastructure.client.GeminiClient;
import analysislunch.infrastructure.client.GitHubClient;
import analysislunch.infrastructure.client.GoogleChatClient;
import analysislunch.infrastructure.client.SlackClient;
import analysislunch.infrastructure.crawler.BlogCrawler;

/**
 * 점심 메뉴 분석 전체 흐름을 조율하는 서비스 클래스.
 *
 * <p>블로그 크롤링 → 이미지 다운로드 → 메뉴 추출 → 이미지 생성 → 칼로리 분석
 * → Slack/Google Chat 전송 → 해시 업데이트 순서로 실행됩니다.
 */
@Slf4j
public class LunchFlowService {

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
    private static final String CALORIE_CARD_TITLE = "상세 칼로리 분석";
    private static final String CALORIE_CARD_COMMENT = "📊 *상세 칼로리 분석표*";
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
            log.info("처리 시작...");

            // 1. 블로그에서 이미지 URL 추출
            log.info("블로그에서 이미지 URL 추출 중...");
            String imageUrl = blogCrawler.extractImageUrlFromBlog(BLOG_URL);
            log.info("이미지 URL 발견: {}", imageUrl);

            // 2. 이미지 다운로드
            log.info("이미지 다운로드 중...");
            File originalFile = new File(TEMP_ORIGINAL_FILE);
            imageService.download(imageUrl, originalFile);

            // 3. 해시 비교 (변경 없으면 조기 종료)
            String currentHash = imageService.calculateFileHash(originalFile);
            String lastHash = imageService.loadLastHash();
            if (currentHash.equals(lastHash)) {
                log.info("✅ 이미지가 변경되지 않았습니다. 작업을 중단합니다. (Hash: {})", currentHash);
                return;
            }
            log.info("🔄 이미지가 변경되었습니다. (새 Hash: {})", currentHash);

            // 4. 이미지 전처리 (투명 배경 → 흰색 배경)
            log.info("이미지 전처리 중 (흰색 배경 추가)...");
            File processedFile = new File(TEMP_PROCESSED_FILE);
            imageService.convertPngToWhiteBgJpg(originalFile, processedFile);

            // 5. 이미지에서 메뉴 텍스트 추출
            log.info("이미지에서 메뉴 텍스트 추출 중...");
            MenuInfo menuInfo = geminiClient.extractMenuInfo(processedFile);
            log.info("추출된 날짜: {}", menuInfo.date());
            log.info("추출된 메뉴: {}", menuInfo.menu());

            // 6. 식판 이미지 생성
            log.info("Gemini로 식판 이미지 생성 중...");
            File generatedImage = geminiClient.generateFoodImage(menuInfo.menu());

            // 7. 칼로리 분석
            log.info("칼로리 분석 중...");
            String calorieAnalysis = geminiClient.analyzeCalories(generatedImage, menuInfo.menu());
            log.info("{}", calorieAnalysis);

            // 8. 칼로리 카드 이미지 생성
            log.info("칼로리 카드 이미지 생성 중...");
            File calorieCardFile = new File(CALORIE_CARD_FILE);
            imageService.createCalorieCard(calorieAnalysis, calorieCardFile);

            // 9. 메시지 구성
            String title = menuInfo.date() + MENU_TITLE_SUFFIX;
            String foodMessage = "📢 *" + title + "*\n\n AI가 생성한 이미지 입니다. 실제 음식과 다를 수 있습니다.\n\n"
                + menuInfo.menu();

            // 10~11. Slack / Google Chat 전송 (채널 독립 처리: 한쪽 실패가 다른 쪽을 막지 않음)
            boolean slackSent = sendToSlack(generatedImage, calorieCardFile, title, foodMessage);
            boolean googleChatSent = sendToGoogleChat(generatedImage, calorieCardFile, title, foodMessage);

            // 12. 해시 저장 및 업로드 (한 채널이라도 전송에 성공한 경우)
            if (slackSent || googleChatSent) {
                log.info("🔄 해시 업데이트 중... (Slack: {}, Google Chat: {})", slackSent, googleChatSent);
                imageService.saveHash(currentHash);
                gitHubClient.uploadTextFile(currentHash, HASH_FILE);
                log.info("✅ 작업이 완료되었습니다.");
            } else {
                log.warn("⚠️ 모든 채널 전송에 실패하여 해시를 저장하지 않습니다. 다음 실행 시 재시도합니다.");
            }

        } catch (IOException e) {
            log.error("❌ 오류 발생: {}", e.getMessage());
        } finally {
            cleanupTempFiles();
        }
    }

    /**
     * Slack에 메뉴 안내 텍스트를 부모 메시지로 올리고, 식판 이미지와 칼로리 카드를
     * 그 스레드의 답글로 전송합니다.
     *
     * <p>이미지는 GitHub raw URL을 image block으로 보내는 대신 파일을 직접
     * 업로드하여, CDN 전파 지연으로 인한 다운로드 실패("downloading image failed")를
     * 원천적으로 방지합니다.
     *
     * <p>스레드 부모로는 항상 안정적인 ts를 반환하는 {@code chat.postMessage} 텍스트
     * 메시지를 사용합니다. 그 ts를 두 파일 업로드의 {@code thread_ts}로 넘겨 답글로
     * 묶으므로, 파일 업로드 응답의 공유 ts에 의존하지 않습니다(files:read 불필요).
     *
     * @param foodImage 식판 이미지 파일
     * @param cardImage 칼로리 카드 이미지 파일
     * @param title     식판 이미지 파일 제목
     * @param message   부모 메시지(메뉴 안내) 본문
     * @return 전송에 성공하면 {@code true}, 실패하면 {@code false}
     */
    private boolean sendToSlack(File foodImage, File cardImage, String title, String message) {
        try {
            log.info("Slack에 전송 중...");
            // 부모: 메뉴 안내 텍스트
            String parentTs = slackClient.postMessage(config.getChannelId(), message);
            if (parentTs == null) {
                log.warn("Slack 부모 메시지 ts를 확보하지 못했습니다. 이미지가 답글로 묶이지 않을 수 있습니다.");
            }
            // 답글: 식판 이미지 → 칼로리 카드
            slackClient.uploadFile(config.getChannelId(), foodImage, title, null, parentTs);
            slackClient.uploadFile(config.getChannelId(), cardImage, CALORIE_CARD_TITLE, CALORIE_CARD_COMMENT, parentTs);
            log.info("✅ Slack 스레드 전송 완료.");
            return true;
        } catch (IOException e) {
            log.error("⚠️ Slack 전송 실패 (다른 채널은 계속 진행): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Google Chat에 식판 이미지와 칼로리 카드를 카드 메시지로 전송합니다.
     *
     * <p>Google Chat 카드는 이미지 URL을 참조하므로, 전송 직전에 GitHub에 이미지를
     * 업로드하고 raw URL을 사용합니다.
     *
     * @param foodImage 식판 이미지 파일
     * @param cardImage 칼로리 카드 이미지 파일
     * @param title     식판 카드 제목
     * @param message   식판 카드 본문
     * @return 전송에 성공하면 {@code true}, 실패하면 {@code false}
     */
    private boolean sendToGoogleChat(File foodImage, File cardImage, String title, String message) {
        try {
            log.info("GitHub에 이미지 업로드 중 (Google Chat용)...");
            long timestamp = System.currentTimeMillis();
            String foodImageName = FOOD_IMAGE_PREFIX + timestamp + IMAGE_EXTENSION;
            String cardImageName = CARD_IMAGE_PREFIX + timestamp + IMAGE_EXTENSION;
            gitHubClient.uploadImage(foodImage, foodImageName);
            gitHubClient.uploadImage(cardImage, cardImageName);
            String foodImageUrl = gitHubClient.getRawUrl(foodImageName);
            String cardImageUrl = gitHubClient.getRawUrl(cardImageName);

            log.info("Google Chat에 전송 중...");
            String chatThreadKey = "lunch-" + timestamp;
            googleChatClient.sendCard(foodImageUrl, title, message, chatThreadKey);
            log.info("✅ Google Chat 식판 이미지 전송 완료.");

            waitForGoogleChatOrder();

            googleChatClient.sendCard(cardImageUrl, CALORIE_CARD_TITLE, CALORIE_CARD_COMMENT, chatThreadKey);
            log.info("✅ Google Chat 칼로리 카드 전송 완료.");
            return true;
        } catch (IOException e) {
            log.error("⚠️ Google Chat 전송 실패 (다른 채널은 계속 진행): {}", e.getMessage());
            return false;
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
            log.warn("Google Chat 전송 대기 중 인터럽트 발생: {}", e.getMessage());
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
