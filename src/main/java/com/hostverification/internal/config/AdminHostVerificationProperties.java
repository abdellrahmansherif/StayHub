package com.hostverification.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Paging limits for GET /admin/host-verifications.

//   admin-host-verification.default-page-size=10
//   admin-host-verification.max-page-size=50
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin-host-verification")
public class AdminHostVerificationProperties {

    private int defaultPageSize = 10;

    private int maxPageSize = 50;
}
