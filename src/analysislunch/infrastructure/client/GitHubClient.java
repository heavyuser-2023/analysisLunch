package analysislunch.infrastructure.client;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import analysislunch.utils.JsonUtils;

/**
 * GitHub Contents API와 통신하는 클라이언트 클래스.
 *
 * <p>이미지 및 텍스트 파일을 GitHub 저장소에 업로드하고 Raw URL을 생성합니다.
 */
public class GitHubClient {

    private static final Logger logger = Logger.getLogger(GitHubClient.class.getName());

    private static final String API_BASE = "https://api.github.com";
    private static final String BRANCH = "main";
    private static final String IMAGE_PATH = "images";
    private static final String COMMIT_MSG_UPDATE_IMAGE = "Update lunch image";
    private static final String COMMIT_MSG_ADD_IMAGE = "Add lunch image";
    private static final String COMMIT_MSG_UPDATE_HASH = "Update menu hash";
    private static final String COMMIT_MSG_CREATE_HASH = "Create menu hash";
    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;

    private static final Gson GSON = new Gson();

    private final String token;
    private final String repo;

    /**
     * GitHubClient 생성자.
     *
     * @param token GitHub 개인 액세스 토큰
     * @param repo  GitHub 저장소 (예: "owner/repo")
     */
    public GitHubClient(String token, String repo) {
        this.token = token;
        this.repo = repo;
    }

    /**
     * GitHub Contents API를 이용해 이미지 파일을 업로드합니다.
     *
     * <p>파일이 이미 존재하면 업데이트하고, 없으면 새로 생성합니다.
     *
     * @param file     업로드할 이미지 파일
     * @param filename 저장소 내 파일명
     * @throws IOException 파일 읽기 또는 API 호출 실패 시
     */
    public void uploadImage(File file, String filename) throws IOException {
        String base64Content = encodeFileToBase64(file);
        String path = IMAGE_PATH + "/" + filename;
        String apiUrl = String.format("%s/repos/%s/contents/%s", API_BASE, repo, path);

        String existingSha = getExistingFileSha(apiUrl);

        JsonObject body = new JsonObject();
        body.addProperty("message", existingSha != null ? COMMIT_MSG_UPDATE_IMAGE : COMMIT_MSG_ADD_IMAGE);
        body.addProperty("content", base64Content);
        body.addProperty("branch", BRANCH);
        if (existingSha != null) {
            body.addProperty("sha", existingSha);
        }

        uploadToGitHub(apiUrl, GSON.toJson(body));
        logger.info("GitHub 업로드 성공: " + path);
    }

    /**
     * 텍스트 파일(해시 등)을 저장소 루트에 업로드합니다.
     *
     * <p>파일이 이미 존재하면 업데이트하고, 없으면 새로 생성합니다.
     *
     * @param content  업로드할 텍스트 내용
     * @param filename 저장소 내 파일명
     * @throws IOException API 호출 실패 시
     */
    public void uploadTextFile(String content, String filename) throws IOException {
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String apiUrl = String.format("%s/repos/%s/contents/%s", API_BASE, repo, filename);

        String existingSha = getExistingFileSha(apiUrl);

        JsonObject body = new JsonObject();
        body.addProperty("message", existingSha != null ? COMMIT_MSG_UPDATE_HASH : COMMIT_MSG_CREATE_HASH);
        body.addProperty("content", base64Content);
        body.addProperty("branch", BRANCH);
        if (existingSha != null) {
            body.addProperty("sha", existingSha);
        }

        uploadToGitHub(apiUrl, GSON.toJson(body));
        logger.info("GitHub 업로드 성공: " + filename);
    }

    /**
     * 저장소 내 이미지 파일의 Raw URL을 생성합니다.
     *
     * @param filename 이미지 파일명
     * @return Raw 콘텐츠 URL 문자열
     */
    public String getRawUrl(String filename) {
        return String.format(
            "https://raw.githubusercontent.com/%s/%s/%s/%s",
            repo, BRANCH, IMAGE_PATH, filename
        );
    }

    /**
     * GitHub API로 파일의 현재 SHA를 조회합니다.
     *
     * @param apiUrl 조회할 파일의 GitHub Contents API URL
     * @return 파일이 존재하면 SHA 문자열, 없으면 {@code null}
     */
    private String getExistingFileSha(String apiUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() == HTTP_OK) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return JsonUtils.extract(response, "sha");
            }
        } catch (IOException e) {
            logger.fine("파일 SHA 조회 실패 (신규 파일로 처리): " + e.getMessage());
        }
        return null;
    }

    /**
     * GitHub Contents API에 PUT 요청으로 파일을 업로드합니다.
     *
     * @param apiUrl   업로드 대상 API URL
     * @param jsonBody 요청 본문 JSON 문자열
     * @throws IOException 업로드 실패 또는 응답 코드가 200/201이 아닐 때
     */
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
        if (responseCode != HTTP_OK && responseCode != HTTP_CREATED) {
            String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("GitHub 업로드 실패 (" + responseCode + "): " + error);
        }
    }

    /**
     * 파일을 Base64 문자열로 인코딩합니다.
     *
     * @param file 인코딩할 파일
     * @return Base64 인코딩된 문자열
     * @throws IOException 파일 읽기 실패 시
     */
    private String encodeFileToBase64(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }
}
