package analysislunch.domain.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import javax.imageio.ImageIO;

public class ImageService {
    private static final String HASH_FILE = "menu_hash.txt";

    public void download(String imageUrl, File destination) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    public void convertPngToWhiteBgJpg(File input, File output) throws IOException {
        BufferedImage original = ImageIO.read(input);
        if (original == null) throw new IOException("Failed to read image: " + input.getName());

        BufferedImage newImage = new BufferedImage(
            original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = newImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();

        ImageIO.write(newImage, "jpg", output);
    }

    public String loadLastHash() {
        File file = new File(HASH_FILE);
        if (!file.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.readLine();
        } catch (IOException e) {
            System.err.println("Failed to read hash file: " + e.getMessage());
            return null;
        }
    }

    public void saveHash(String hash) {
        try (FileWriter fw = new FileWriter(HASH_FILE)) {
            fw.write(hash);
        } catch (IOException e) {
            System.err.println("Failed to write hash file: " + e.getMessage());
        }
    }

    public String calculateFileHash(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[1024];
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
        } catch (Exception e) {
            throw new IOException("Hash calculation failed", e);
        }
    }

    public void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 칼로리 정보를 담은 독립적인 카드 이미지 생성
     */
    public void createCalorieCard(String calorieInfo, File output) throws IOException {
        // 1. Parsing Logic
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        String totalLine = "";
        
        String[] lines = calorieInfo.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("|---") || line.startsWith("|-")) continue;
            
            if (line.startsWith("|")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                     String menu = parts[1].trim();
                     String cal = parts[2].trim();
                     if (!menu.equals("메뉴명") && !menu.contains("---")) {
                         rows.add(new String[]{menu, cal});
                     }
                }
            } else if (line.contains("총 예상 칼로리")) {
                totalLine = line.replace("**", "").trim();
            }
        }
        
        // 2. Load Font
        java.awt.Font font;
        try {
            font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new File("fonts/NanumGothic.ttf"));
        } catch (java.awt.FontFormatException e) {
            System.err.println("Font format error, using default: " + e.getMessage());
            font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12);
        }

        // 3. Layout Calculation
        int width = 1000;
        int rowHeight = 60;
        int headerHeight = 120;
        int footerHeight = 100;
        int contentHeight = rows.size() * rowHeight;
        int height = headerHeight + contentHeight + footerHeight;
        
        BufferedImage cardImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = cardImage.createGraphics();
        
        // High Quality Rendering Hints
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2d.setColor(new Color(33, 37, 41)); // Dark Slate
        g2d.fillRect(0, 0, width, height);
        
        // Header Area
        g2d.setColor(new Color(44, 48, 52)); // Slightly lighter header
        g2d.fillRect(0, 0, width, headerHeight);
        
        // Header Text
        g2d.setColor(new Color(255, 193, 7)); // Amber color
        g2d.setFont(font.deriveFont(java.awt.Font.BOLD, 36f));
        g2d.drawString("\uD83D\uDCCA 오늘의 영양 분석", 50, 75);
        
        g2d.setColor(new Color(173, 181, 189)); // Grey subtext
        g2d.setFont(font.deriveFont(java.awt.Font.PLAIN, 18f));
        // Right-alignedish
        g2d.drawString("AI가 분석한 예상 칼로리 정보입니다", width - 330, 75);

        // Table Content
        int y = headerHeight + 40;
        int xMenu = 80;
        int xCal = 750;
        
        // Font setup for rows
        java.awt.Font menuFont = font.deriveFont(java.awt.Font.PLAIN, 24f);
        java.awt.Font calFont = font.deriveFont(java.awt.Font.BOLD, 24f);

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            
            // Alternating row background (very subtle)
            if (i % 2 == 0) {
                g2d.setColor(new Color(255, 255, 255, 10)); // Very faint white overlay
                g2d.fillRect(30, y - 35, width - 60, rowHeight);
            }

            g2d.setColor(new Color(248, 249, 250)); // White text
            g2d.setFont(menuFont);
            g2d.drawString(row[0], xMenu, y);
            
            g2d.setColor(new Color(13, 202, 240)); // Cyan text for calories
            g2d.setFont(calFont);
            // Right align logic for calories could be added, but left align at xCal is simple
            g2d.drawString(row[1], xCal, y);
            
            y += rowHeight;
        }
        
        // Separator line before total
        g2d.setColor(new Color(73, 80, 87));
        g2d.setStroke(new java.awt.BasicStroke(2));
        g2d.drawLine(50, height - footerHeight, width - 50, height - footerHeight);
        
        // Total Section
        if (!totalLine.isEmpty()) {
            g2d.setColor(new Color(255, 99, 71)); // Tomato/Red color
            g2d.setFont(font.deriveFont(java.awt.Font.BOLD, 32f));
            // Centered-ish or right-aligned layout
            g2d.drawString(totalLine, xMenu, height - 35);
        }
        
        g2d.dispose();
        ImageIO.write(cardImage, "png", output);
    }
}
