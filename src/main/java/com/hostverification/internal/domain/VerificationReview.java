package com.hostverification.internal.domain;

// save the admin review response

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "verification_review",
        indexes = @Index(name = "idx_verification_review_verification_id", columnList = "verification_id"))

public class VerificationReview {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id",nullable = false,updatable = false)
    private UUID id;

    // The request this decision belongs to.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "verification_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_verification_review_verification")
    )
    private HostVerification verification;


    // Always taken from the JWT never from the request body.
    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;


    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private ReviewDecision decision;

    //Rejection reason this is "null" when approved
    @Column(name = "reason", length = 1000)
    private String reason;

    // Only for REJECTED  Null for the other decisions
    @Column(name = "retry_allowed")
    private Boolean retryAllowed;

    // Only for NEEDS_CLARIFICATION
    @ElementCollection
    @CollectionTable(
            name = "verification_review_corrections",
            joinColumns = @JoinColumn(name = "review_id")
    )

    @Enumerated(EnumType.STRING)
    @Column(name = "correction", nullable = false, length = 30)
    @Builder.Default
    private Set<CorrectionField> requiredCorrections = new LinkedHashSet<>();

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;
}
