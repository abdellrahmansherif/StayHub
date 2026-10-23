package com.identity.internal.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "host_verifications",
indexes = @Index(name = "idx_host_verifications_user_id", columnList = "user_id"))
public class HostVerification {
    // this id for the Identify the verification request
    @Id
    @Column(name = "id",nullable = false,updatable = false)
    private UUID id;

    //  UUID (not @ManyToOne to User) so this is for the user
    @Column(name = "user_id",nullable = false,updatable = false)
    private UUID userId;

    @Column(name = "full_name",nullable = false,length = 150)
    private String fullName;

    @Column(name ="phone_number",nullable = false,length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false,length = 30)
    private  VerificationStatus status;

    @Column(name = "clarification_message", length = 1000)
    private String clarificationMessage;

    @Column(name = "id_front_key", nullable = false, length = 500)
    private String idFrontKey;

    @Column(name = "id_back_key", nullable = false, length = 500)
    private String idBackKey;

    @Column(name = "selfie_photo_key", nullable = false, length = 500)
    private String selfiePhotoKey;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.submittedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}