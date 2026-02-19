package analysislunch.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 요청을 수행하는 유틸리티 클래스.
 *
 * <p>GET, POST(JSON), 바이너리 업로드 등의 공통 HTTP 작업을 제공합니다.
 */
@Slf4j
public class HttpUtils {

    private static final int BUFFER_SIZE = 8192;
    private static final int HTTP_OK = 200;
    private static final int HTTP_ERROR_THRESHOLD = 400;

    private HttpUtils() {
        // 유틸리티 클래스 - 인스턴스화 금지
    }

    /**
     * Bearer 토큰 인증을 포함한 GET 요청을 수행합니다.
     *
     * @param urlStr 요청 URL
     * @param token  Bearer 인증 토큰 (null 허용 시 인증 헤더 미포함)
     * @return 응답 본문 문자열
     * @throws IOException 네트워크 오류 또는 응답 읽기 실패 시
     */
    public static String get(String urlStr, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        return readResponse(conn);
    }

    /**
     * 브라우저 User-Agent를 포함한 HTML 페이지 GET 요청을 수행합니다.
     *
     * @param urlStr 요청 URL
     * @return HTML 응답 본문 문자열
     * @throws IOException 네트워크 오류 또는 응답 읽기 실패 시
     */
    public static String getHtml(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setInstanceFollowRedirects(true);
        return readResponse(conn);
    }

    /**
     * JSON 본문을 포함한 POST 요청을 수행합니다.
     *
     * @param urlStr   요청 URL
     * @param token    Bearer 인증 토큰 (null 허용 시 인증 헤더 미포함)
     * @param jsonBody 요청 본문 JSON 문자열
     * @return 응답 본문 문자열
     * @throws IOException 네트워크 오류 또는 응답 읽기 실패 시
     */
    public static String postJson(String urlStr, String token, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    /**
     * 파일을 바이너리 스트림으로 업로드합니다.
     *
     * @param uploadUrl 업로드 대상 URL
     * @param file      업로드할 파일
     * @throws IOException 업로드 실패 또는 응답 코드가 200이 아닐 때
     */
    public static void uploadBinary(String uploadUrl, File file) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");

        try (OutputStream os = conn.getOutputStream();
             BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        if (conn.getResponseCode() != HTTP_OK) {
            log.error("바이너리 업로드 실패 (응답 코드: {})", conn.getResponseCode());
            throw new IOException("바이너리 업로드 실패 (응답 코드: " + conn.getResponseCode() + ")");
        }
    }

    /**
     * HTTP 응답 본문을 문자열로 읽습니다.
     *
     * @param conn 연결된 {@link HttpURLConnection}
     * @return 응답 본문 문자열
     * @throws IOException 응답 읽기 실패 시
     */
    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() >= HTTP_ERROR_THRESHOLD
                    ? conn.getErrorStream()
                    : conn.getInputStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
