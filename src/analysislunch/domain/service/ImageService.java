package analysislunch.domain.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;

/**
 * 이미지 다운로드, 변환, 해시 관리, 칼로리 카드 생성을 담당하는 서비스 클래스.
 */
@Slf4j
public class ImageService {

    private static final String HASH_FILE = "menu_hash.txt";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String FONT_FILE_PATH = "fonts/NanumGothic.ttf";
    private static final String FALLBACK_FONT_FAMILY = "SansSerif";
    private static final String OUTPUT_FORMAT_JPG = "jpg";
    private static final String OUTPUT_FORMAT_PNG = "png";
    private static final String PIPE_DELIMITER = "\\|";
    private static final String TABLE_HEADER_MENU = "메뉴명";
    private static final String TABLE_SEPARATOR_PREFIX = "---";
    private static final String TOTAL_CALORIE_KEYWORD = "총 예상 칼로리";
    private static final String BOLD_MARKER = "**";

    private static final int DOWNLOAD_BUFFER_SIZE = 8192;
    private static final int HASH_BUFFER_SIZE = 1024;
    private static final int CARD_WIDTH = 1000;
    private static final int CARD_ROW_HEIGHT = 60;
    private static final int CARD_HEADER_HEIGHT = 120;
    private static final int CARD_FOOTER_HEIGHT = 100;
    private static final int CARD_PADDING_X = 80;
    private static final int CARD_CALORIE_X = 750;
    private static final int CARD_SEPARATOR_MARGIN = 50;
    private static final int CARD_ROW_INITIAL_Y_OFFSET = 40;
    private static final int CARD_ROW_STRIPE_Y_OFFSET = 35;
    private static final float FONT_SIZE_HEADER = 36f;
    private static final float FONT_SIZE_SUBTEXT = 18f;
    private static final float FONT_SIZE_ROW = 24f;
    private static final float FONT_SIZE_TOTAL = 32f;
    private static final int FONT_SIZE_FALLBACK = 12;
    private static final int SEPARATOR_STROKE_WIDTH = 2;
    private static final int HEADER_TEXT_Y = 75;
    private static final int SUBTEXT_X_OFFSET = 330;

    // --- 카드 배경 색상 ---
    private static final Color COLOR_BG_DARK = new Color(33, 37, 41);
    private static final Color COLOR_BG_HEADER = new Color(44, 48, 52);
    // --- 헤더 텍스트 색상 ---
    private static final Color COLOR_ACCENT_YELLOW = new Color(255, 193, 7);
    private static final Color COLOR_TEXT_MUTED = new Color(173, 181, 189);
    // --- 행 색상 ---
    private static final Color COLOR_ROW_STRIPE = new Color(255, 255, 255, 10);
    private static final Color COLOR_TEXT_LIGHT = new Color(248, 249, 250);
    private static final Color COLOR_ACCENT_CYAN = new Color(13, 202, 240);
    // --- 구분선 / 합계 색상 ---
    private static final Color COLOR_SEPARATOR = new Color(73, 80, 87);
    private static final Color COLOR_TOTAL_RED = new Color(255, 99, 71);

    /**
     * 이미지 URL에서 파일을 다운로드합니다.
     *
     * @param imageUrl    다운로드할 이미지 URL
     * @param destination 저장할 대상 파일
     * @throws IOException 다운로드 실패 시
     */
    public void download(String imageUrl, File destination) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * PNG 이미지를 흰색 배경의 JPG 이미지로 변환합니다.
     *
     * @param input  변환할 PNG 파일
     * @param output 저장할 JPG 파일
     * @throws IOException 이미지 읽기/쓰기 실패 시
     */
    public void convertPngToWhiteBgJpg(File input, File output) throws IOException {
        BufferedImage original = ImageIO.read(input);
        if (original == null) {
            throw new IOException("이미지 읽기 실패: " + input.getName());
        }

        BufferedImage newImage = new BufferedImage(
            original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = newImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();

        ImageIO.write(newImage, OUTPUT_FORMAT_JPG, output);
    }

    /**
     * 마지막으로 저장된 이미지 해시를 로드합니다.
     *
     * @return 저장된 해시 문자열, 파일이 없거나 읽기 실패 시 {@code null}
     */
    public String loadLastHash() {
        File file = new File(HASH_FILE);
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.readLine();
        } catch (IOException e) {
            log.warn("해시 파일 읽기 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 이미지 해시를 파일에 저장합니다.
     *
     * @param hash 저장할 해시 문자열
     */
    public void saveHash(String hash) {
        try (FileWriter fw = new FileWriter(HASH_FILE)) {
            fw.write(hash);
        } catch (IOException e) {
            log.warn("해시 파일 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 파일의 SHA-256 해시를 계산합니다.
     *
     * @param file 해시를 계산할 파일
     * @return 16진수 형식의 SHA-256 해시 문자열
     * @throws IOException 파일 읽기 또는 해시 알고리즘 초기화 실패 시
     */
    public String calculateFileHash(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] byteArray = new byte[HASH_BUFFER_SIZE];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("해시 알고리즘 초기화 실패: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * 지정된 경로의 파일을 삭제합니다.
     *
     * @param path 삭제할 파일 경로
     */
    public void deleteFile(String path) {
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            log.warn("파일 삭제 실패: {}", path);
        }
    }

    /**
     * 칼로리 분석 정보를 담은 카드 이미지를 생성합니다.
     *
     * @param calorieInfo 마크다운 표 형식의 칼로리 분석 문자열
     * @param output      생성할 PNG 이미지 파일
     * @throws IOException 이미지 생성 또는 저장 실패 시
     */
    public void createCalorieCard(String calorieInfo, File output) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String totalLine = "";

        for (String line : calorieInfo.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("|" + TABLE_SEPARATOR_PREFIX) || line.startsWith("|-")) {
                continue;
            }
            if (line.startsWith("|")) {
                String[] parts = line.split(PIPE_DELIMITER);
                if (parts.length >= 3) {
                    String menu = parts[1].trim();
                    String cal = parts[2].trim();
                    if (!menu.equals(TABLE_HEADER_MENU) && !menu.contains(TABLE_SEPARATOR_PREFIX)) {
                        rows.add(new String[]{menu, cal});
                    }
                }
            } else if (line.contains(TOTAL_CALORIE_KEYWORD)) {
                totalLine = line.replace(BOLD_MARKER, "").trim();
            }
        }

        Font font = loadFont();
        BufferedImage cardImage = renderCard(rows, totalLine, font);
        ImageIO.write(cardImage, OUTPUT_FORMAT_PNG, output);
    }

    /**
     * 폰트를 로드합니다. 커스텀 폰트 로드 실패 시 기본 폰트를 반환합니다.
     *
     * @return 로드된 {@link Font}
     */
    private Font loadFont() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(FONT_FILE_PATH)) {
            if (is == null) {
                log.warn("폰트 리소스를 찾을 수 없습니다: {} — 기본 폰트 사용", FONT_FILE_PATH);
                return new Font(FALLBACK_FONT_FAMILY, Font.PLAIN, FONT_SIZE_FALLBACK);
            }
            return Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (java.awt.FontFormatException | IOException e) {
            log.warn("폰트 로드 실패, 기본 폰트 사용: {}", e.getMessage());
            return new Font(FALLBACK_FONT_FAMILY, Font.PLAIN, FONT_SIZE_FALLBACK);
        }
    }

    /**
     * 칼로리 카드 이미지를 렌더링합니다.
     *
     * @param rows      메뉴명과 칼로리 쌍의 목록
     * @param totalLine 총 칼로리 텍스트
     * @param font      사용할 폰트
     * @return 렌더링된 {@link BufferedImage}
     */
    private BufferedImage renderCard(List<String[]> rows, String totalLine, Font font) {
        int contentHeight = rows.size() * CARD_ROW_HEIGHT;
        int height = CARD_HEADER_HEIGHT + contentHeight + CARD_FOOTER_HEIGHT;

        BufferedImage cardImage = new BufferedImage(CARD_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = cardImage.createGraphics();

        applyRenderingHints(g2d);
        drawBackground(g2d, height);
        drawHeader(g2d, font);
        drawRows(g2d, rows, font);
        drawSeparator(g2d, height);
        drawTotal(g2d, totalLine, font, height);

        g2d.dispose();
        return cardImage;
    }

    private void applyRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private void drawBackground(Graphics2D g2d, int height) {
        g2d.setColor(COLOR_BG_DARK);
        g2d.fillRect(0, 0, CARD_WIDTH, height);
        g2d.setColor(COLOR_BG_HEADER);
        g2d.fillRect(0, 0, CARD_WIDTH, CARD_HEADER_HEIGHT);
    }

    private void drawHeader(Graphics2D g2d, Font font) {
        g2d.setColor(COLOR_ACCENT_YELLOW);
        g2d.setFont(font.deriveFont(Font.BOLD, FONT_SIZE_HEADER));
        g2d.drawString("📊 오늘의 영양 분석", CARD_PADDING_X, HEADER_TEXT_Y);

        g2d.setColor(COLOR_TEXT_MUTED);
        g2d.setFont(font.deriveFont(Font.PLAIN, FONT_SIZE_SUBTEXT));
        g2d.drawString("AI가 분석한 예상 칼로리 정보입니다", CARD_WIDTH - SUBTEXT_X_OFFSET, HEADER_TEXT_Y);
    }

    private void drawRows(Graphics2D g2d, List<String[]> rows, Font font) {
        int y = CARD_HEADER_HEIGHT + CARD_ROW_INITIAL_Y_OFFSET;
        Font menuFont = font.deriveFont(Font.PLAIN, FONT_SIZE_ROW);
        Font calFont = font.deriveFont(Font.BOLD, FONT_SIZE_ROW);

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (i % 2 == 0) {
                g2d.setColor(COLOR_ROW_STRIPE);
                g2d.fillRect(CARD_SEPARATOR_MARGIN, y - CARD_ROW_STRIPE_Y_OFFSET,
                    CARD_WIDTH - CARD_SEPARATOR_MARGIN * 2, CARD_ROW_HEIGHT);
            }
            g2d.setColor(COLOR_TEXT_LIGHT);
            g2d.setFont(menuFont);
            g2d.drawString(row[0], CARD_PADDING_X, y);

            g2d.setColor(COLOR_ACCENT_CYAN);
            g2d.setFont(calFont);
            g2d.drawString(row[1], CARD_CALORIE_X, y);

            y += CARD_ROW_HEIGHT;
        }
    }

    private void drawSeparator(Graphics2D g2d, int height) {
        g2d.setColor(COLOR_SEPARATOR);
        g2d.setStroke(new BasicStroke(SEPARATOR_STROKE_WIDTH));
        g2d.drawLine(CARD_SEPARATOR_MARGIN, height - CARD_FOOTER_HEIGHT,
            CARD_WIDTH - CARD_SEPARATOR_MARGIN, height - CARD_FOOTER_HEIGHT);
    }

    private void drawTotal(Graphics2D g2d, String totalLine, Font font, int height) {
        if (!totalLine.isEmpty()) {
            g2d.setColor(COLOR_TOTAL_RED);
            g2d.setFont(font.deriveFont(Font.BOLD, FONT_SIZE_TOTAL));
            g2d.drawString(totalLine, CARD_PADDING_X, height - CARD_SEPARATOR_MARGIN / 2);
        }
    }
}
