package analysislunch.infrastructure.client;

import analysislunch.utils.HttpUtils;
import analysislunch.utils.JsonUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Slack API와 통신하는 클라이언트 클래스.
 *
 * <p>메시지 전송, 이미지 URL 기반 메시지, 파일 업로드 기능을 제공합니다.
 */
public class SlackClient {

    private static final Logger logger = Logger.getLogger(SlackClient.class.getName());

    private static final String API_GET_URL = "https://slack.com/api/files.getUploadURLExternal";
    private static final String API_COMPLETE = "https://slack.com/api/files.completeUploadExternal";
    private static final String API_POST_MESSAGE = "https://slack.com/api/chat.postMessage";
    private static final String DEFAULT_ALT_TEXT = "lunch image";
    private static final String RESPONSE_OK = "\"ok\":true";

    private static final Gson GSON = new Gson();

    private final String token;

    /**
     * SlackClient 생성자.
     *
     * @param token Slack 봇 토큰
     */
    public SlackClient(String token) {
        this.token = token;
    }

    /**
     * Slack 채널에 텍스트 메시지를 전송합니다.
     *
     * @param channelId 메시지를 전송할 채널 ID
     * @param text      전송할 텍스트
     * @return 전송된 메시지의 타임스탬프(ts), 실패 시 {@code null}
     * @throws IOException API 호출 실패 시
     */
    public String postMessage(String channelId, String text) throws IOException {
        return postMessage(channelId, text, null);
    }

    /**
     * Slack 채널에 텍스트 메시지를 전송합니다 (스레드 답글 지원).
     *
     * @param channelId 메시지를 전송할 채널 ID
     * @param text      전송할 텍스트
     * @param threadTs  답글을 달 스레드의 타임스탬프 (null이면 새 메시지)
     * @return 전송된 메시지의 타임스탬프(ts), 실패 시 {@code null}
     * @throws IOException API 호출 실패 시
     */
    public String postMessage(String channelId, String text, String threadTs) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("channel", channelId);
        body.addProperty("text", text);
        if (threadTs != null && !threadTs.isEmpty()) {
            body.addProperty("thread_ts", threadTs);
        }
        String response = HttpUtils.postJson(API_POST_MESSAGE, token, GSON.toJson(body));
        if (!response.contains(RESPONSE_OK)) {
            throw new IOException("메시지 전송 실패: " + response);
        }
        String messageTs = JsonUtils.extract(response, "ts");
        if (messageTs == null) {
            logger.warning("Slack 메시지 ts를 응답에서 찾을 수 없습니다.");
        }
        return messageTs;
    }

    /**
     * 이미지 URL을 포함한 Slack 블록 메시지를 전송합니다.
     *
     * @param channelId 메시지를 전송할 채널 ID
     * @param text      메시지 텍스트 (마크다운 지원)
     * @param imageUrl  표시할 이미지 URL
     * @param threadTs  답글을 달 스레드의 타임스탬프 (null이면 새 메시지)
     * @return 전송된 메시지의 타임스탬프(ts), 실패 시 {@code null}
     * @throws IOException API 호출 실패 시
     */
    public String postImageMessage(String channelId, String text, String imageUrl, String threadTs)
            throws IOException {
        String messageText = (text == null || text.isEmpty()) ? DEFAULT_ALT_TEXT : text;

        JsonArray blocks = new JsonArray();
        if (text != null && !text.isEmpty()) {
            JsonObject sectionText = new JsonObject();
            sectionText.addProperty("type", "mrkdwn");
            sectionText.addProperty("text", text);
            JsonObject section = new JsonObject();
            section.addProperty("type", "section");
            section.add("text", sectionText);
            blocks.add(section);
        }
        JsonObject imageBlock = new JsonObject();
        imageBlock.addProperty("type", "image");
        imageBlock.addProperty("image_url", imageUrl);
        imageBlock.addProperty("alt_text", DEFAULT_ALT_TEXT);
        blocks.add(imageBlock);

        JsonObject body = new JsonObject();
        body.addProperty("channel", channelId);
        body.addProperty("text", messageText);
        body.add("blocks", blocks);
        if (threadTs != null && !threadTs.isEmpty()) {
            body.addProperty("thread_ts", threadTs);
        }

        String response = HttpUtils.postJson(API_POST_MESSAGE, token, GSON.toJson(body));
        if (!response.contains(RESPONSE_OK)) {
            throw new IOException("이미지 메시지 전송 실패: " + response);
        }
        String messageTs = JsonUtils.extract(response, "ts");
        if (messageTs == null) {
            logger.warning("Slack 메시지 ts를 응답에서 찾을 수 없습니다.");
        }
        return messageTs;
    }

    /**
     * Slack에 파일을 업로드합니다.
     *
     * @param channelId      업로드할 채널 ID
     * @param file           업로드할 파일
     * @param title          파일 제목
     * @param initialComment 파일과 함께 표시할 초기 코멘트
     * @return 업로드된 파일의 공유 메시지 타임스탬프(ts), 실패 시 {@code null}
     * @throws IOException API 호출 실패 시
     */
    public String uploadFile(String channelId, File file, String title, String initialComment)
            throws IOException {
        return uploadFile(channelId, file, title, initialComment, null);
    }

    /**
     * Slack에 파일을 업로드합니다 (스레드 답글 지원).
     *
     * @param channelId      업로드할 채널 ID
     * @param file           업로드할 파일
     * @param title          파일 제목
     * @param initialComment 파일과 함께 표시할 초기 코멘트
     * @param threadTs       답글을 달 스레드의 타임스탬프 (null이면 새 메시지)
     * @return 업로드된 파일의 공유 메시지 타임스탬프(ts), 실패 시 {@code null}
     * @throws IOException API 호출 실패 시
     */
    public String uploadFile(String channelId, File file, String title, String initialComment, String threadTs)
            throws IOException {
        // 1단계: 업로드 URL 획득
        String getUrlResponse = callGetUploadUrl(file.getName(), file.length());
        String uploadUrl = JsonUtils.extract(getUrlResponse, "upload_url");
        String fileId = JsonUtils.extract(getUrlResponse, "file_id");

        if (uploadUrl == null || fileId == null) {
            throw new IOException("업로드 URL 획득 실패: " + getUrlResponse);
        }
        uploadUrl = uploadUrl.replace("\\/", "/");

        // 2단계: 바이너리 업로드
        HttpUtils.uploadBinary(uploadUrl, file);

        // 3단계: 업로드 완료 처리
        String completeResponse = callCompleteUpload(fileId, title, initialComment, channelId, threadTs);
        if (!completeResponse.contains(RESPONSE_OK)) {
            throw new IOException("업로드 완료 처리 실패: " + completeResponse);
        }
        String messageTs = JsonUtils.extractSlackShareTs(completeResponse);
        if (messageTs == null) {
            logger.warning("Slack 스레드 ts를 응답에서 찾을 수 없습니다.");
        }
        return messageTs;
    }

    /**
     * Slack 파일 업로드 URL을 요청합니다.
     *
     * @param filename 업로드할 파일명
     * @param length   파일 크기 (바이트)
     * @return API 응답 JSON 문자열
     * @throws IOException API 호출 실패 시
     */
    private String callGetUploadUrl(String filename, long length) throws IOException {
        String params = String.format(
            "filename=%s&length=%d",
            URLEncoder.encode(filename, StandardCharsets.UTF_8),
            length
        );
        return HttpUtils.get(API_GET_URL + "?" + params, token);
    }

    /**
     * Slack 파일 업로드 완료를 처리합니다.
     *
     * @param fileId         업로드된 파일 ID
     * @param title          파일 제목
     * @param initialComment 초기 코멘트
     * @param channelId      채널 ID
     * @param threadTs       스레드 타임스탬프 (null 허용)
     * @return API 응답 JSON 문자열
     * @throws IOException API 호출 실패 시
     */
    private String callCompleteUpload(
            String fileId,
            String title,
            String initialComment,
            String channelId,
            String threadTs) throws IOException {
        JsonObject file = new JsonObject();
        file.addProperty("id", fileId);
        file.addProperty("title", title);
        JsonArray files = new JsonArray();
        files.add(file);

        JsonObject body = new JsonObject();
        body.add("files", files);
        body.addProperty("channel_id", channelId);
        body.addProperty("initial_comment", initialComment);
        if (threadTs != null && !threadTs.isEmpty()) {
            body.addProperty("thread_ts", threadTs);
        }

        return HttpUtils.postJson(API_COMPLETE, token, GSON.toJson(body));
    }
}
