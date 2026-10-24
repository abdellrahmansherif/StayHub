package com.hostverification.internal.application;

import com.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local-disk storage for development. In production replace the body of
 * save()/urlFor() with S3 (or similar) and keep the bucket private.
 */
@Component
public class PrivateImageStorage {

    private final Path root;
    private final String baseUrl;

    //store the image link into database
    public PrivateImageStorage(
            @Value("${host-verification.storage.root-dir}") String rootDir,
            @Value("${host-verification.storage.base-url}") String baseUrl) {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    /** Saves the file and returns its key, e.g. hv/<id>/id_front.jpg
     * - root: المجلد الأساسي للتخزين.
     * - key: المسار النسبي للملف داخل مساحة التخزين.
     * - target: المسار الكامل للملف على نظام الملفات.
     * */
    public String save(UUID verificationId, String name, String extension, MultipartFile file) {
        String key = "hv/" + verificationId + "/" + name + "." + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return key;
    }

    public String urlFor(String key) {
        return baseUrl + "/" + key;
    }
}
