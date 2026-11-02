package com.hostverification.internal.persistence;

import com.hostverification.internal.domain.HostVerification;
import com.hostverification.internal.domain.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminHostVerificationRepository extends JpaRepository<HostVerification, UUID> {

    // GET /admin/host-verifications
    Page<HostVerification> findAllByStatus(VerificationStatus status, Pageable pageable);



    // PATCH /admin/host-verifications/{id}/decision
    // بعمل lock عشن مخليش اتنين ادمن عيدلو فنفس الوقت ويحصل تعارض ولما يخلص يفك الlock

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from HostVerification v where v.id = :id")
    Optional<HostVerification> findByIdForUpdate(@Param("id") UUID id);
}
