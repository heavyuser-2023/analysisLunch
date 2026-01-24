package analysislunch.infrastructure.client;

import analysislunch.utils.JsonUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GitHubClient {
    private static final String API_BASE = "https://api.github.com";
    private static final String BRANCH = "main";
    private static final String IMAGE_PATH = "images";
    private final String token;
    private final String repo;

    public GitHubClient(String token, String repo) {
        this.token = token;
        this.repo = repo;
    }

    /**
     * GitHub Contents API를 이용해 이미지 업로드
     */
    public void uploadImage(File file, String filename) throws IOException {
        String base64Content = encodeFileToBase64(file);
        String path = IMAGE_PATH + "/" + filename;
        String apiUrl = String.format("%s/repos/%s/contents/%s", API_BASE, repo, path);

        // Check if file exists (to get SHA for update)
        String existingSha = getExistingFileSha(apiUrl);

        String jsonBody;
        if (existingSha != null) {
            jsonBody = String.format(
                "{\"message\":\"Update lunch image\",\"content\":\"%s\",\"branch\":\"%s\",\"sha\":\"%s\"}",
                base64Content, BRANCH, existingSha
            );
        } else {
            jsonBody = String.format(
                "{\"message\":\"Add lunch image\",\"content\":\"%s\",\"branch\":\"%s\"}",
                base64Content, BRANCH
            );
        }

        uploadToGitHub(apiUrl, jsonBody);
        System.out.println("  GitHub upload successful: " + path);
    }

    /**
     * 텍스트 파일(해시 등)을 저장소 루트에 업로드
     */
    public void uploadTextFile(String content, String filename) throws IOException {
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String apiUrl = String.format("%s/repos/%s/contents/%s", API_BASE, repo, filename);

        // Check if file exists (to get SHA for update)
        String existingSha = getExistingFileSha(apiUrl);

        String jsonBody;
        if (existingSha != null) {
            jsonBody = String.format(
                "{\"message\":\"Update menu hash\",\"content\":\"%s\",\"branch\":\"%s\",\"sha\":\"%s\"}",
                base64Content, BRANCH, existingSha
            );
        } else {
            jsonBody = String.format(
                "{\"message\":\"Create menu hash\",\"content\":\"%s\",\"branch\":\"%s\"}",
                base64Content, BRANCH
            );
        }

        uploadToGitHub(apiUrl, jsonBody);
        System.out.println("  GitHub upload successful: " + filename);
    }

    /**
     * Raw URL 생성
     */
    public String getRawUrl(String filename) {
        return String.format("https://raw.githubusercontent.com/%s/%s/%s/%s",
            repo, BRANCH, IMAGE_PATH, filename);
    }

    private String getExistingFileSha(String apiUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() == 200) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return JsonUtils.extract(response, "sha");
            }
        } catch (IOException e) {
            // File doesn't exist, ignore
        }
        return null;
    }

    private void uploadToGitHub(String apiUrl, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("GitHub upload failed (" + responseCode + "): " + error);
        }
    }

    private String encodeFileToBase64(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
}
