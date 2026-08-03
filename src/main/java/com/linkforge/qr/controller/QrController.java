package com.linkforge.qr.controller;

import com.linkforge.qr.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/urls/{urlId}/qr")
@RequiredArgsConstructor
@Tag(name = "QR Codes", description = "Generate, customize, and download QR codes for shortened URLs")
@SecurityRequirement(name = "Bearer Authentication")
public class QrController {

    private final QrService qrService;

    @Operation(summary = "Generate PNG QR code")
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generatePng(
            @PathVariable UUID urlId,
            @RequestParam(defaultValue = "512") int size,
            @RequestParam(defaultValue = "000000") String fgColor,
            @RequestParam(defaultValue = "FFFFFF") String bgColor,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        // Validate size (128, 256, 512, 1024)
        size = validateSize(size);
        byte[] qrBytes = qrService.generatePng(urlId, size, fgColor, bgColor);

        HttpHeaders headers = new HttpHeaders();
        if (download) {
            headers.setContentDispositionFormData("attachment", "qrcode-" + urlId + ".png");
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.IMAGE_PNG)
                .body(qrBytes);
    }

    @Operation(summary = "Generate SVG QR code")
    @GetMapping(value = "/svg", produces = "image/svg+xml")
    public ResponseEntity<String> generateSvg(
            @PathVariable UUID urlId,
            @RequestParam(defaultValue = "512") int size
    ) {
        String svg = qrService.generateSvg(urlId, size);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg);
    }

    @Operation(summary = "Generate QR code with center logo")
    @PostMapping(value = "/with-logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateWithLogo(
            @PathVariable UUID urlId,
            @RequestParam(defaultValue = "512") int size,
            @RequestParam("logo") MultipartFile logo
    ) throws IOException {
        size = validateSize(size);
        byte[] qrBytes = qrService.generatePngWithLogo(urlId, size, logo.getBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qrcode-with-logo-" + urlId + ".png")
                .body(qrBytes);
    }

    private int validateSize(int size) {
        if (size <= 128) return 128;
        if (size <= 256) return 256;
        if (size <= 512) return 512;
        return 1024;
    }
}
