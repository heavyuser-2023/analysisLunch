import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SlackSender {
    public static void main(String[] args) {
        // GitHub Actions의 Secret에서 환경 변수로 전달받을 URL
        String slackUrl = System.getenv("SLACK_WEBHOOK_URL");
        String message = "{\"text\": \"📢 GitHub Actions에서 보낸 정기 메시지입니다! (Java 실행)\"}";

        if (slackUrl == null || slackUrl.isEmpty()) {
            System.err.println("환경 변수 SLACK_WEBHOOK_URL이 설정되지 않았습니다.");
            System.exit(1);
        }

        try {
            URL url = new URL(slackUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = message.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            System.out.println("슬랙 전송 결과 코드: " + code);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}