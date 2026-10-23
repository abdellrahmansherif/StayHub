package com.identity.internal.web.Controller;

import com.identity.api.CurrentUserFacade;
import com.identity.internal.web.Service.HostVerificationService;
import com.identity.internal.web.dto.FaceMatchResponse;
import com.identity.internal.web.dto.HostVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/host-verifications")
@RequiredArgsConstructor
public class HostVerificationController {

    private final HostVerificationService service;
    private final CurrentUserFacade currentUser;


//200	OK	الطلب نجح (زي لما تجيب بيانات: GET)
//201	Created	الطلب نجح وعملنا حاجة جديدة في الداتابيز (زي POST للإنشاء)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HostVerificationResponse submit(
            @RequestParam("full_name") String fullName,
            @RequestParam("phone_number") String phoneNumber,
            @RequestPart("id_front") MultipartFile idFront,
            @RequestPart("id_back") MultipartFile idBack,
            @RequestPart("selfie_photo") MultipartFile selfiePhoto) {
        return service.submit(currentUser.getCurrentUserId(),
                fullName, phoneNumber, idFront, idBack, selfiePhoto);
    }

    @GetMapping("/me")
    public HostVerificationResponse getMine() {
        return service.getByUser(currentUser.getCurrentUserId());
    }

    @PostMapping(value = "/{id}/face-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceMatchResponse faceMatch(
            @PathVariable UUID id,
            @RequestPart("id_front") MultipartFile idFront,
            @RequestPart("selfie_photo") MultipartFile selfiePhoto) {
        return service.runFaceMatch(currentUser.getCurrentUserId(), currentUser.isAdmin(),
                id, idFront, selfiePhoto);
    }
}
