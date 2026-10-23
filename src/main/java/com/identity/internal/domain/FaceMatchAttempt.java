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
@Table(name = "face_match_attempts",
indexes = @Index(name = "idx_face_match_attempts_verification_id",columnList = "verification_id")
)
public class FaceMatchAttempt {
    //column for random unique id for face_match_session
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id",nullable = false,updatable = false)
    private UUID id;

    /*

    @many to one
      معناها إن كل Face Match Attempt واحدة مرتبطة بـ Host Verification واحدة
      ، لكن الـ Host Verification الواحدة ممكن يكون ليها محاولات Face Matching كتير
     @fetch
       * Fetches the associated HostVerification lazily to avoid loading it
        unnecessarily when retrieving face match attempts.
     optional >> when you make a host verification you have to make a faceMatch
     */

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(
            name = "verification_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_face_match_attempt_verification")
    )
    private HostVerification verification;

    @Column(name = "similarity", nullable = false)
    private double similarity;

    @Column(name = "threshold", nullable = false)
    private double threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private FaceMatchResult result;

    @Column(name = "execution_time_seconds", nullable = false)
    private double executionTimeSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

//    قبل ما Hibernate يحفظ الـ Entity لأول مرة، بيشغّل الميثود onCreate() تلقائيًا بسبب @PrePersist
//    this.createdAt: المتغير الموجود في الـ Entity
//    Instant.now(): بترجع الوقت والتاريخ الحاليين كـ Instant، وده بيمثل لحظة زمنية محددة بالنسبة لتوقيت UTC.
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }


}