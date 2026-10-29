package com.hostverification.internal.web.dto;


import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.multipart.MultipartFile;

public record FaceMatchRequest(
        @BindParam("id_front")
        @NotNull
        MultipartFile idFront,

        @BindParam("selfie_photo")
        @NotNull
        MultipartFile selfiePhoto
) {}
