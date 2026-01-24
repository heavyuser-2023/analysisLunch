package analysislunch.infrastructure.client;

import analysislunch.utils.HttpUtils;
import analysislunch.utils.JsonUtils;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SlackClient {
    private static final String API_GET_URL = "https://slack.com/api/files.getUploadURLExternal";
    private static final String API_COMPLETE = "https://slack.com/api/files.completeUploadExternal";
    private final String token;

    public SlackClient(String token) {
        this.token = token;
    }

    public void uploadFile(String channelId, File file, String title, String initialComment) throws IOException {
        // Step 1: Get Upload URL
        String getUrlResponse = callGetUploadUrl(file.getName(), file.length());
        String uploadUrl = JsonUtils.extract(getUrlResponse, "upload_url");
        String fileId = JsonUtils.extract(getUrlResponse, "file_id");
        
        if (uploadUrl == null || fileId == null) {
            throw new IOException("Failed to get upload URL: " + getUrlResponse);
        }
        // Unescape URL if needed
        uploadUrl = uploadUrl.replace("\\/", "/");

        // Step 2: Upload Binary
        HttpUtils.uploadBinary(uploadUrl, file);

        // Step 3: Complete Upload
        String completeResponse = callCompleteUpload(fileId, title, initialComment, channelId);
        if (!completeResponse.contains("\"ok\":true")) {
            throw new IOException("Failed to complete upload: " + completeResponse);
        }
    }

    private String callGetUploadUrl(String filename, long length) throws IOException {
        String params = String.format("filename=%s&length=%d", 
            URLEncoder.encode(filename, StandardCharsets.UTF_8), length);
        return HttpUtils.get(API_GET_URL + "?" + params, token);
    }

    private String callCompleteUpload(String fileId, String title, String initialComment, String channelId) throws IOException {
        // JSON Escaping for initialComment
        String escapedComment = initialComment.replace("\"", "\\\"").replace("\n", "\\n");
        
        String jsonBody = String.format(
            "{\"files\":[{\"id\":\"%s\",\"title\":\"%s\"}],\"channel_id\":\"%s\",\"initial_comment\":\"%s\"}",
            fileId, title, channelId, escapedComment
        );
        return HttpUtils.postJson(API_COMPLETE, token, jsonBody);
    }
}
