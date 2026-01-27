package analysislunch.domain.service;

import analysislunch.config.AppConfig;
import analysislunch.domain.model.MenuInfo;
import analysislunch.infrastructure.client.GeminiClient;
import analysislunch.infrastructure.client.GitHubClient;
import analysislunch.infrastructure.client.GoogleChatClient;
import analysislunch.infrastructure.client.SlackClient;
import analysislunch.infrastructure.crawler.BlogCrawler;
import java.io.File;

public class LunchFlowService {
    private static final String BLOG_URL = "https://m.blog.naver.com/yjm3038/222191646255";
    private static final String TEMP_ORIGINAL_FILE = "temp_original.png";
    private static final String TEMP_PROCESSED_FILE = "lunch_menu_white_bg.jpg";
    private static final String HASH_FILE = "menu_hash.txt";

    private final AppConfig config;
    private final ImageService imageService;
    private final BlogCrawler blogCrawler;
    private final GeminiClient geminiClient;
    private final SlackClient slackClient;
    private final GitHubClient gitHubClient;
    private final GoogleChatClient googleChatClient;

    public LunchFlowService(AppConfig config,
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

    public void run() {
        try {
            System.out.println("Processing started...");

            // 1. Extract image URL from blog page
            System.out.println("- Extracting image URL from blog...");
            String imageUrl = blogCrawler.extractImageUrlFromBlog(BLOG_URL);
            System.out.println("  Found image URL: " + imageUrl);

            // 2. Download Image
            System.out.println("- Downloading image...");
            File originalFile = new File(TEMP_ORIGINAL_FILE);
            imageService.download(imageUrl, originalFile);

            // Hash Check Logic
            String currentHash = imageService.calculateFileHash(originalFile);
            String lastHash = imageService.loadLastHash();

            if (currentHash.equals(lastHash)) {
                System.out.println("✅ 이미지가 변경되지 않았습니다. 작업을 중단합니다. (Hash: " + currentHash + ")");
                return;
            }

            System.out.println("🔄 이미지가 변경되었습니다. (New Hash: " + currentHash + ")");
            // Hash saving moved to the end of the process


            // 3. Process Image (Remove Transparency)
            System.out.println("- Processing image (adding white background)...");
            File processedFile = new File(TEMP_PROCESSED_FILE);
            imageService.convertPngToWhiteBgJpg(originalFile, processedFile);

            // 4. Extract menu text from image
            System.out.println("- Extracting menu text from image...");
            MenuInfo menuInfo = geminiClient.extractMenuInfo(processedFile);
            System.out.println("Extracted Date: " + menuInfo.date());
            System.out.println("Extracted Menu: " + menuInfo.menu());

            // 5. Generate food tray image
            System.out.println("- Generating food tray image with Gemini...");
            File generatedImage = geminiClient.generateFoodImage(menuInfo.menu());

            // 6. Upload to Slack
            System.out.println("- Uploading generated image to Slack...");
            String title = "오늘의 점심 메뉴 (" + menuInfo.date() + ")";
            String initialComment = "📢 *" + title + "*" + "\n\n AI가 생성한 이미지 입니다. 실제 음식과 다를 수 있습니다." + "\n\n" + menuInfo.menu();
            
            slackClient.uploadFile(config.getChannelId(), generatedImage, title, initialComment);

            // 7. Upload to google chat
            System.out.println("- Uploading image to GitHub...");
            String imageFilename = "lunch_" + System.currentTimeMillis() + ".png";
            gitHubClient.uploadImage(generatedImage, imageFilename);
            String githubImageUrl = gitHubClient.getRawUrl(imageFilename);
            System.out.println("  Image URL: " + githubImageUrl);

            System.out.println("- Sending to Google Chat...");
            googleChatClient.sendCard(githubImageUrl, title, initialComment);

            // 8. Save and Upload Hash (Only if everything succeeded)
            System.out.println("🔄 All tasks completed. Updating hash...");
            imageService.saveHash(currentHash);
            System.out.println("- Uploading menu_hash.txt to GitHub...");
            gitHubClient.uploadTextFile(currentHash, HASH_FILE);

            System.out.println("✅ Task completed successfully.");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup temp files
            imageService.deleteFile(TEMP_ORIGINAL_FILE);
            imageService.deleteFile(TEMP_PROCESSED_FILE);
            imageService.deleteFile("generated_food.png");
        }
    }
}
