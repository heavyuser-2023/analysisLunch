package analysislunch.infrastructure.client;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

import analysislunch.utils.HttpUtils;

/**
 * Instagram Graph API(Meta)와 통신하는 클라이언트 클래스.
 *
 * <p>인스타그램은 봇 토큰 하나로 글을 올리는 텔레그램과 달리, 비즈니스/크리에이터
 * 계정 + 페이스북 페이지 연결 + Meta 앱 권한({@code instagram_content_publish})과
 * 장기 액세스 토큰이 필요합니다. 또한 로컬 파일을 직접 업로드할 수 없고 <b>공개로
 * 접근 가능한 이미지 URL</b>만 받습니다. 이 프로젝트는 {@link GitHubClient}로 이미지를
 * 업로드해 raw URL을 만든 뒤 그 URL을 넘겨 사용합니다.
 *
 * <p>게시는 컨테이너 생성 → 발행의 2~3단계로 이뤄집니다. 캐러셀(여러 장)은
 * 각 이미지로 자식 컨테이너를 만들고, 이를 묶는 캐러셀 컨테이너를 만든 뒤 발행합니다.
 */
@Slf4j
public class InstagramClient {

    private static final String API_BASE = "https://graph.facebook.com/v21.0";
    private static final String MEDIA_ENDPOINT = "media";
    private static final String PUBLISH_ENDPOINT = "media_publish";
    private static final String MEDIA_TYPE_CAROUSEL = "CAROUSEL";
    /** 인스타그램 캐러셀 최소 이미지 수. */
    private static final int CAROUSEL_MIN = 2;
    /** 인스타그램 캐러셀 최대 이미지 수. */
    private static final int CAROUSEL_MAX = 10;

    private static final Gson GSON = new Gson();

    private final String accessToken;
    private final String igUserId;

    /**
     * InstagramClient 생성자.
     *
     * @param accessToken Instagram Graph API 장기 액세스 토큰
     * @param igUserId    Instagram 비즈니스 계정 ID (IG User ID)
     */
    public InstagramClient(String accessToken, String igUserId) {
        this.accessToken = accessToken;
        this.igUserId = igUserId;
    }

    /**
     * 여러 장의 이미지를 캐러셀 게시물로 올립니다.
     *
     * <p>각 이미지로 자식 컨테이너를 만들고, 이를 묶는 캐러셀 컨테이너를 생성한 뒤
     * 발행합니다. 인스타그램 캐러셀은 2~10장만 허용합니다.
     *
     * @param imageUrls 공개 접근 가능한 이미지 URL 목록 (2~10개)
     * @param caption   게시물 캡션 (인스타그램은 마크다운 미지원, 평문)
     * @throws IOException API 호출 실패 또는 이미지 수가 허용 범위를 벗어날 때
     */
    public void postCarousel(List<String> imageUrls, String caption) throws IOException {
        if (imageUrls == null || imageUrls.size() < CAROUSEL_MIN || imageUrls.size() > CAROUSEL_MAX) {
            throw new IOException(
                "Instagram 캐러셀은 이미지 " + CAROUSEL_MIN + "~" + CAROUSEL_MAX + "장만 허용합니다. (요청: "
                    + (imageUrls == null ? 0 : imageUrls.size()) + "장)");
        }

        // 1. 각 이미지로 자식 컨테이너 생성
        List<String> childIds = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("image_url", imageUrl);
            params.put("is_carousel_item", "true");
            childIds.add(extractId(post(MEDIA_ENDPOINT, params)));
        }

        // 2. 자식들을 묶는 캐러셀 컨테이너 생성
        Map<String, String> carouselParams = new LinkedHashMap<>();
        carouselParams.put("media_type", MEDIA_TYPE_CAROUSEL);
        carouselParams.put("children", String.join(",", childIds));
        if (caption != null && !caption.isEmpty()) {
            carouselParams.put("caption", caption);
        }
        String carouselId = extractId(post(MEDIA_ENDPOINT, carouselParams));

        // 3. 발행
        publish(carouselId);
        log.info("Instagram 캐러셀 게시 완료 ({}장)", imageUrls.size());
    }

    /**
     * 단일 이미지를 게시물로 올립니다.
     *
     * @param imageUrl 공개 접근 가능한 이미지 URL
     * @param caption  게시물 캡션 (평문)
     * @throws IOException API 호출 실패 시
     */
    public void postImage(String imageUrl, String caption) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("image_url", imageUrl);
        if (caption != null && !caption.isEmpty()) {
            params.put("caption", caption);
        }
        String containerId = extractId(post(MEDIA_ENDPOINT, params));
        publish(containerId);
        log.info("Instagram 단일 이미지 게시 완료");
    }

    /**
     * 생성된 미디어 컨테이너를 발행합니다.
     *
     * @param creationId 미디어 컨테이너 ID
     * @throws IOException 발행 실패 시
     */
    private void publish(String creationId) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("creation_id", creationId);
        extractId(post(PUBLISH_ENDPOINT, params));
    }

    /**
     * IG 사용자 노드의 엔드포인트에 POST 요청을 보냅니다.
     *
     * <p>모든 파라미터(액세스 토큰 포함)는 URL 쿼리 문자열로 인코딩해 전달하고,
     * 본문은 비워 보냅니다. Graph API는 POST 요청의 쿼리 파라미터를 그대로 읽습니다.
     *
     * @param endpoint {@code media} 또는 {@code media_publish}
     * @param params   요청 파라미터 (access_token 제외)
     * @return API 응답 본문 문자열
     * @throws IOException 호출 실패 시
     */
    private String post(String endpoint, Map<String, String> params) throws IOException {
        StringBuilder url = new StringBuilder(API_BASE).append("/")
            .append(igUserId).append("/").append(endpoint)
            .append("?access_token=").append(encode(accessToken));
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append("&").append(entry.getKey()).append("=").append(encode(entry.getValue()));
        }
        return HttpUtils.postJson(url.toString(), null, "{}");
    }

    /**
     * Graph API 응답에서 {@code id} 값을 추출합니다.
     *
     * @param response API 응답 JSON 문자열
     * @return {@code id} 값
     * @throws IOException 응답에 {@code id}가 없거나 오류 응답일 때
     */
    private String extractId(String response) throws IOException {
        try {
            JsonObject json = GSON.fromJson(response, JsonObject.class);
            if (json != null && json.has("id")) {
                return json.get("id").getAsString();
            }
        } catch (RuntimeException e) {
            throw new IOException("Instagram 응답 파싱 실패: " + response, e);
        }
        throw new IOException("Instagram API 오류 응답: " + response);
    }

    /**
     * 문자열을 URL 쿼리용으로 인코딩합니다.
     *
     * @param value 인코딩할 값
     * @return UTF-8로 URL 인코딩된 문자열
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
