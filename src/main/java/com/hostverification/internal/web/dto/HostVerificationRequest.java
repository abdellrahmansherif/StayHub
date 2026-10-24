package com.hostverification.internal.web.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.multipart.MultipartFile;

public record HostVerificationRequest(
        @BindParam("full_name")
        @NotBlank
        @Size(max = 150)
        String fullName,

        @BindParam("phone_number")
        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @BindParam("id_front")
        @NotNull
        MultipartFile idFront,

        @BindParam("id_back")
        @NotNull
        MultipartFile idBack,

        @BindParam("selfie_photo")
        @NotNull
        MultipartFile selfiePhoto
) {}

