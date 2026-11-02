
package com.hostverification.internal.application;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PrivateImageStorage {

    private final Cloudinary cloudinary;

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    // Upload a protected image to Cloudinary.
    // Return its key, not a public URL.
    public String save(
            UUID verificationId,
            String name,
            String extension,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image is required");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new BadRequestException(
                    "Image must not exceed 5 MB"
            );
        }

        if (!extension.equals("jpg") &&
                !extension.equals("png")) {
            throw new BadRequestException(
                    "Only JPG and PNG are supported"
            );
        }

        String publicId =
                "stayhub/host-verifications/"
                        + verificationId
                        + "/" + name;

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "image",
                            "type", "authenticated",
                            "overwrite", false
                    )
            );

            String savedPublicId =
                    (String) result.get("public_id");

            String format =
                    (String) result.get("format");

            if (savedPublicId == null || format == null) {
                throw new IllegalStateException(
                        "Invalid Cloudinary upload response"
                );
            }

            return savedPublicId + "." + format;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to upload image to Cloudinary", e
            );
        }
    }

    // Create a time-limited authenticated download URL.
    // Only call this after checking user permissions.
    public String urlFor(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        int dot = key.lastIndexOf('.');

        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Invalid Cloudinary image key"
            );
        }

        String publicId = key.substring(0, dot);
        String format = key.substring(dot + 1);

        long expiresAt =
                Instant.now().plusSeconds(300).getEpochSecond();

        try {
            return cloudinary.privateDownload(
                    publicId,
                    format,
                    ObjectUtils.asMap(
                            "type", "authenticated",
                            "resource_type", "image",
                            "expires_at", expiresAt,
                            "attachment", false
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate image URL", e
            );
        }
    }

    // Delete an authenticated image from Cloudinary.
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        int dot = key.lastIndexOf('.');

        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Invalid Cloudinary image key"
            );
        }

        String publicId = key.substring(0, dot);

        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "type", "authenticated",
                            "invalidate", true
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to delete Cloudinary image", e
            );
        }
    }
}
