package analysislunch.infrastructure.crawler;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import analysislunch.utils.HttpUtils;

/**
 * 네이버 블로그 페이지에서 메뉴 이미지 URL을 크롤링하는 클래스.
 */
public class BlogCrawler {

    private static final String HTTPS_SCHEME = "https:";
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
        "class=\"se-module se-module-image\"[^>]*>[\\s\\S]*?<img[^>]+(?:data-lazy-src|src)=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 네이버 블로그 페이지에서 {@code se-module-image} 클래스의 img 태그 URL을 추출합니다.
     *
     * @param blogUrl 크롤링할 네이버 블로그 URL
     * @return 추출된 이미지 URL (절대 경로)
     * @throws IOException 페이지 로드 실패 또는 이미지를 찾을 수 없을 때
     */
    public String extractImageUrlFromBlog(String blogUrl) throws IOException {
        String html = HttpUtils.getHtml(blogUrl);

        Matcher matcher = IMAGE_URL_PATTERN.matcher(html);
        if (matcher.find()) {
            String imageUrl = matcher.group(1);
            if (imageUrl.startsWith("//")) {
                imageUrl = HTTPS_SCHEME + imageUrl;
            }
            return imageUrl;
        }

        throw new IOException("블로그 페이지에서 이미지를 찾을 수 없습니다: " + blogUrl);
    }
}
