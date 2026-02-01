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

public class SlackClient {
    private static final String API_GET_URL = "https://slack.com/api/files.getUploadURLExternal";
    private static final String API_COMPLETE = "https://slack.com/api/files.completeUploadExternal";
    private static final String API_POST_MESSAGE = "https://slack.com/api/chat.postMessage";
    private static final Gson GSON = new Gson();
    private final String token;

    public SlackClient(String token) {
        this.token = token;
    }

    public String postMessage(String channelId, String text) throws IOException {
        return postMessage(channelId, text, null);
    }

    public String postMessage(String channelId, String text, String threadTs) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("channel", channelId);
        body.addProperty("text", text);
        if (threadTs != null && !threadTs.isEmpty()) {
            body.addProperty("thread_ts", threadTs);
        }
        String response = HttpUtils.postJson(API_POST_MESSAGE, token, GSON.toJson(body));
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
        String messageText = (text == null || text.isEmpty()) ? "lunch image" : text;

        JsonArray blocks = new JsonArray();
        if (text != null && !text.isEmpty()) {
            JsonObject section = new JsonObject();
            section.addProperty("type", "section");
            JsonObject sectionText = new JsonObject();
            sectionText.addProperty("type", "mrkdwn");
            sectionText.addProperty("text", text);
            section.add("text", sectionText);
            blocks.add(section);
        }
        JsonObject imageBlock = new JsonObject();
        imageBlock.addProperty("type", "image");
        imageBlock.addProperty("image_url", imageUrl);
        imageBlock.addProperty("alt_text", "lunch image");
        blocks.add(imageBlock);

        JsonObject body = new JsonObject();
        body.addProperty("channel", channelId);
        body.addProperty("text", messageText);
        body.add("blocks", blocks);
        if (threadTs != null && !threadTs.isEmpty()) {
            body.addProperty("thread_ts", threadTs);
        }

        String response = HttpUtils.postJson(API_POST_MESSAGE, token, GSON.toJson(body));
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
