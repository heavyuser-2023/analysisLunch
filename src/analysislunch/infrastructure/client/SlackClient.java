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
    private static final String API_POST_MESSAGE = "https://slack.com/api/chat.postMessage";
    private final String token;

    public SlackClient(String token) {
        this.token = token;
    }

    public String postMessage(String channelId, String text) throws IOException {
        return postMessage(channelId, text, null);
    }

    public String postMessage(String channelId, String text, String threadTs) throws IOException {
        String escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        StringBuilder jsonBody = new StringBuilder();
        jsonBody.append("{\"channel\":\"")
            .append(channelId)
            .append("\",\"text\":\"")
            .append(escapedText)
            .append("\"");
        if (threadTs != null && !threadTs.isEmpty()) {
            jsonBody.append(",\"thread_ts\":\"").append(threadTs).append("\"");
        }
        jsonBody.append("}");
        String response = HttpUtils.postJson(API_POST_MESSAGE, token, jsonBody.toString());
        if (!response.contains("\"ok\":true")) {
            throw new IOException("Failed to post message: " + response);
        }
        String messageTs = JsonUtils.extract(response, "ts");
        if (messageTs == null) {
            System.out.println("  WARN: Slack message ts not found in response.");
        }
        return messageTs;
    }

    public String postImageMessage(String channelId, String text, String imageUrl, String threadTs) throws IOException {
        String escapedText = text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String escapedUrl = imageUrl.replace("\\", "\\\\").replace("\"", "\\\"");
        StringBuilder blocks = new StringBuilder();
        blocks.append("[");
        if (!escapedText.isEmpty()) {
            blocks.append("{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"")
                .append(escapedText)
                .append("\"}},");
        }
        blocks.append("{\"type\":\"image\",\"image_url\":\"")
            .append(escapedUrl)
            .append("\",\"alt_text\":\"lunch image\"}");
        blocks.append("]");

        StringBuilder jsonBody = new StringBuilder();
        jsonBody.append("{\"channel\":\"")
            .append(channelId)
            .append("\",\"text\":\"")
            .append(escapedText.isEmpty() ? "lunch image" : escapedText)
            .append("\",\"blocks\":")
            .append(blocks);
        if (threadTs != null && !threadTs.isEmpty()) {
            jsonBody.append(",\"thread_ts\":\"").append(threadTs).append("\"");
        }
        jsonBody.append("}");

        String response = HttpUtils.postJson(API_POST_MESSAGE, token, jsonBody.toString());
        if (!response.contains("\"ok\":true")) {
            throw new IOException("Failed to post image message: " + response);
        }
        String messageTs = JsonUtils.extract(response, "ts");
        if (messageTs == null) {
            System.out.println("  WARN: Slack message ts not found in response.");
        }
        return messageTs;
    }

    public String uploadFile(String channelId, File file, String title, String initialComment) throws IOException {
        return uploadFile(channelId, file, title, initialComment, null);
    }

    public String uploadFile(String channelId, File file, String title, String initialComment, String threadTs) throws IOException {
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
        String completeResponse = callCompleteUpload(fileId, title, initialComment, channelId, threadTs);
        if (!completeResponse.contains("\"ok\":true")) {
            throw new IOException("Failed to complete upload: " + completeResponse);
        }
        String messageTs = JsonUtils.extractSlackShareTs(completeResponse);
        if (messageTs == null) {
            System.out.println("  WARN: Slack thread ts not found in response.");
        }
        return messageTs;
    }

    private String callGetUploadUrl(String filename, long length) throws IOException {
        String params = String.format("filename=%s&length=%d", 
            URLEncoder.encode(filename, StandardCharsets.UTF_8), length);
        return HttpUtils.get(API_GET_URL + "?" + params, token);
    }

    private String callCompleteUpload(String fileId, String title, String initialComment, String channelId, String threadTs) throws IOException {
        // JSON Escaping for initialComment
        String escapedComment = initialComment.replace("\"", "\\\"").replace("\n", "\\n");
        String escapedTitle = title.replace("\"", "\\\"");
        
        StringBuilder jsonBody = new StringBuilder();
        jsonBody.append("{\"files\":[{\"id\":\"")
            .append(fileId)
            .append("\",\"title\":\"")
            .append(escapedTitle)
            .append("\"}],\"channel_id\":\"")
            .append(channelId)
            .append("\",\"initial_comment\":\"")
            .append(escapedComment)
            .append("\"");
        if (threadTs != null && !threadTs.isEmpty()) {
            jsonBody.append(",\"thread_ts\":\"").append(threadTs).append("\"");
        }
        jsonBody.append("}");
        return HttpUtils.postJson(API_COMPLETE, token, jsonBody.toString());
    }
}
