package analysislunch.infrastructure.crawler;

import analysislunch.utils.HttpUtils;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlogCrawler {

    /**
     * 네이버 블로그 페이지에서 se-module-image 클래스의 img 태그 URL을 추출
     */
    public String extractImageUrlFromBlog(String blogUrl) throws IOException {
        // 네이버 모바일 블로그 HTML 가져오기
        String html = HttpUtils.getHtml(blogUrl);
        
        // se-module se-module-image 클래스를 찾고 그 안의 img 태그의 src 추출
        // 패턴: class="se-module se-module-image" ... <img ... src="..." 또는 data-lazy-src="..."
        Pattern modulePattern = Pattern.compile(
            "class=\"se-module se-module-image\"[^>]*>[\\s\\S]*?<img[^>]+(?:data-lazy-src|src)=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = modulePattern.matcher(html);
        if (matcher.find()) {
            String imageUrl = matcher.group(1);
            // URL이 상대경로인 경우 처리
            if (imageUrl.startsWith("//")) {
                imageUrl = "https:" + imageUrl;
            }
            return imageUrl;
        }
        
        throw new IOException("블로그 페이지에서 이미지를 찾을 수 없습니다: " + blogUrl);
    }
}
