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
}
