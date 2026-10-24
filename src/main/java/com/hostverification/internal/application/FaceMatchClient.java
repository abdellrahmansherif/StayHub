package com.hostverification.internal.application;

import com.common.exception.BadRequestException;
import com.hostverification.internal.web.dto.FaceMatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class FaceMatchClient {

    //RestClient: أداة من Spring لإرسال HTTP requests إلى خدمات خارجية.
    private final RestClient restClient;

    public FaceMatchClient(@Value("${host-verification.ai.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    //- idFront: صورة الوجه الموجودة في البطاقة.
    //- selfiePhoto: صورة السيلفي اللي المستخدم رفعها.
    public FaceMatchResponse match(MultipartFile idFront, MultipartFile selfiePhoto) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("id_front", asResource(idFront));
        body.add("selfie_photo", asResource(selfiePhoto));

        // NOTE: "/face-match" is an assumption. Ask the AI team for the real path.
        return restClient.post()
                .uri("/face-match")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new BadRequestException("No face detected in one of the images");
                })
                .body(FaceMatchResponse.class);
    }
//change photos format to byte
    private ByteArrayResource asResource(MultipartFile file) {
        try {
            return new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (IOException e) {
            throw new BadRequestException("Cannot read uploaded file");
        }
    }
}
