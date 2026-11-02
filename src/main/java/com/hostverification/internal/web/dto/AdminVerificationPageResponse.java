package com.hostverification.internal.web.dto;

import java.util.List;

// Response of GET /admin/host-verifications
// ائمة طلبات الـ Verification، ومعاها معلومات الـ Pagination (رقم الصفحة، عدد العناصر، إجمالي الصفحات)
public record AdminVerificationPageResponse(
        List<AdminVerificationSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
