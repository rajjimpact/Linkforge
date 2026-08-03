package com.linkforge.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.linkforge.exception.LinkForgeException;
import com.linkforge.urls.entity.ShortUrl;
import com.linkforge.urls.repository.ShortUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * QR Code generation service using ZXing.
 * Supports:
 * - PNG generation (128, 256, 512, 1024px)
 * - SVG generation
 * - Custom foreground/background colors
 * - Center logo overlay
 * - High resolution (anti-aliased)
 */
@Service
@Slf4j
public class QrService {

    private final ShortUrlRepository shortUrlRepository;

    @Value("${application.base-url}")
    private String baseUrl;

    @Value("${application.storage.qr-dir:./uploads/qr-codes}")
    private String qrDir;

    public QrService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    /**
     * Generates a QR code PNG as byte array.
     *
     * @param urlId      The ShortUrl ID
     * @param size       Image size in pixels (default 512)
     * @param fgColor    Foreground hex color (e.g., "000000")
     * @param bgColor    Background hex color (e.g., "FFFFFF")
     * @return PNG byte array
     */
    public byte[] generatePng(UUID urlId, int size, String fgColor, String bgColor) {
        ShortUrl url = getUrl(urlId);
        String qrContent = baseUrl + "/" + url.getShortCode();

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = buildHints();
            BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints);

            Color fg = parseColor(fgColor, Color.BLACK);
            Color bg = parseColor(bgColor, Color.WHITE);

            BufferedImage image = createColoredImage(matrix, size, fg, bg);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            // Update URL record to note QR code exists
            url.setHasQrCode(true);
            shortUrlRepository.save(url);

            return baos.toByteArray();

        } catch (WriterException | IOException e) {
            throw new LinkForgeException("Failed to generate QR code: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generates a QR code with a logo/image in the center.
     * Uses Error Correction Level H to ensure readability with logo overlay.
     */
    public byte[] generatePngWithLogo(UUID urlId, int size, byte[] logoBytes) {
        ShortUrl url = getUrl(urlId);
        String qrContent = baseUrl + "/" + url.getShortCode();

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = buildHints();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High EC for logo overlay

            BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage qrImage = createColoredImage(matrix, size, Color.BLACK, Color.WHITE);

            // Overlay logo in center
            if (logoBytes != null && logoBytes.length > 0) {
                BufferedImage logo = ImageIO.read(new java.io.ByteArrayInputStream(logoBytes));
                int logoSize = size / 5; // Logo takes up 20% of QR
                Image scaledLogo = logo.getScaledInstance(logoSize, logoSize, Image.SCALE_SMOOTH);

                Graphics2D g = qrImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int logoX = (size - logoSize) / 2;
                int logoY = (size - logoSize) / 2;
                g.drawImage(scaledLogo, logoX, logoY, null);
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            return baos.toByteArray();

        } catch (WriterException | IOException e) {
            throw new LinkForgeException("Failed to generate QR code with logo: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generates SVG QR code as string.
     */
    public String generateSvg(UUID urlId, int size) {
        ShortUrl url = getUrl(urlId);
        String qrContent = baseUrl + "/" + url.getShortCode();

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, buildHints());

            StringBuilder svg = new StringBuilder();
            svg.append(String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                <rect width="%d" height="%d" fill="white"/>
                """, size, size, size, size, size, size));

            int moduleSize = Math.max(1, size / matrix.getWidth());
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        svg.append(String.format(
                            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"black\"/>",
                            x * moduleSize, y * moduleSize, moduleSize, moduleSize
                        ));
                    }
                }
            }
            svg.append("</svg>");
            return svg.toString();

        } catch (WriterException e) {
            throw new LinkForgeException("Failed to generate SVG QR code: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ShortUrl getUrl(UUID urlId) {
        return shortUrlRepository.findById(urlId)
                .orElseThrow(() -> new LinkForgeException("URL not found", HttpStatus.NOT_FOUND));
    }

    private Map<EncodeHintType, Object> buildHints() {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    private BufferedImage createColoredImage(BitMatrix matrix, int size, Color fg, Color bg) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int moduleSize = size / width;
        g.setColor(bg);
        g.fillRect(0, 0, size, size);

        g.setColor(fg);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    g.fillRect(x * moduleSize, y * moduleSize, moduleSize, moduleSize);
                }
            }
        }
        g.dispose();
        return image;
    }

    private Color parseColor(String hex, Color defaultColor) {
        if (hex == null || hex.isBlank()) return defaultColor;
        try {
            return Color.decode("#" + hex.replace("#", ""));
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
