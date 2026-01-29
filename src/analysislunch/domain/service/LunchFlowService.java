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

            // 6. Analyze Calories
            System.out.println("- Analyzing calories...");
            String calorieAnalysis = geminiClient.analyzeCalories(generatedImage, menuInfo.menu());
            System.out.println(calorieAnalysis);



            // 7. Create Calorie Card Image
            System.out.println("- Creating calorie card image...");
            File calorieCardFile = new File("calorie_card.png");
            imageService.createCalorieCard(calorieAnalysis, calorieCardFile);

            // 8. Upload to Slack (Two separate uploads)
            System.out.println("- Uploading to Slack...");
            String title = menuInfo.date() + " - " + "점심 메뉴";
            
            // 8-1. Food Image
            System.out.println("  - Sending Food Image to Slack...");
            String comment1 = "📢 *" + title + "*" + "\n\n AI가 생성한 이미지 입니다. 실제 음식과 다를 수 있습니다." + "\n\n" + menuInfo.menu();
            slackClient.uploadFile(config.getChannelId(), generatedImage, title, comment1);
            System.out.println("  ✅ Fast-Check: Food Image uploaded to Slack successfully.");
            
            // Wait to ensure order
            try { Thread.sleep(1000); } catch (InterruptedException ie) {}

            // 8-2. Calorie Card
            System.out.println("  - Sending Calorie Card to Slack...");
            String comment2 = "📊 *상세 칼로리 분석표*";
            slackClient.uploadFile(config.getChannelId(), calorieCardFile, "칼로리 분석", comment2);
            System.out.println("  ✅ Calorie Card uploaded to Slack successfully.");

            // 9. Upload to GitHub & Google Chat
            System.out.println("- Uploading to GitHub & Google Chat...");
            
            // 9-1. Food Image
            String foodImageName = "lunch_food_" + System.currentTimeMillis() + ".png";
            gitHubClient.uploadImage(generatedImage, foodImageName);
            String foodImageUrl = gitHubClient.getRawUrl(foodImageName);
            
            googleChatClient.sendCard(foodImageUrl, title, comment1);
            System.out.println("  ✅ Food Image sent to Google Chat successfully.");
            
            // Wait to ensure order
            try { Thread.sleep(1000); } catch (InterruptedException ie) {}

            // 9-2. Calorie Card
            String cardImageName = "lunch_card_" + System.currentTimeMillis() + ".png";
            gitHubClient.uploadImage(calorieCardFile, cardImageName);
            String cardImageUrl = gitHubClient.getRawUrl(cardImageName);
            
            googleChatClient.sendCard(cardImageUrl, "상세 칼로리 분석", comment2);
            System.out.println("  ✅ Calorie Card sent to Google Chat successfully.");

            // 10. Save and Upload Hash (Only if everything succeeded)
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
            imageService.deleteFile("calorie_card.png");
            imageService.deleteFile("final_food_with_calories.png"); // Clean up old file if exists
        }
    }
}
