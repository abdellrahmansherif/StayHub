package com.identity.internal.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserFacadeImplTest {

    private CurrentUserFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new CurrentUserFacadeImpl();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwtWithSubject(String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("scope", "test");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    private static void authenticateWithJwt(String subject, String... roles) {
        Collection<GrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwtWithSubject(subject), authorities));
        SecurityContextHolder.setContext(context);
    }

    private static void authenticateWithRoles(String... roles) {
        authenticateWithJwt(UUID.randomUUID().toString(), roles);
    }



    @Nested
    @DisplayName("getCurrentUserId()")
    class GetCurrentUserId {

        @Test
        @DisplayName("returns the UUID stored in the JWT subject")
        void returnsUuidFromJwtSubject() {
            UUID expected = UUID.randomUUID();
            authenticateWithJwt(expected.toString(), "ROLE_CUSTOMER");

            UUID actual = facade.getCurrentUserId();

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws IllegalStateException when there is no authentication")
        void throwsWhenNoAuthentication() {
            // context is empty -> getAuthentication() == null
            assertThatThrownBy(() -> facade.getCurrentUserId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("User is not authenticated");
        }

        @Test
        @DisplayName("throws IllegalStateException when authentication is not a JwtAuthenticationToken")
        void throwsWhenAuthenticationIsNotJwt() {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    "user", "password", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            SecurityContextHolder.setContext(context);

            assertThatThrownBy(() -> facade.getCurrentUserId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("User is not authenticated");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when the subject is not a valid UUID")
        void throwsWhenSubjectIsNotUuid() {
            authenticateWithJwt("not-a-uuid", "ROLE_CUSTOMER");

            assertThatThrownBy(() -> facade.getCurrentUserId())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws NullPointerException when the JWT has no subject")
        void throwsWhenSubjectIsMissing() {
            authenticateWithJwt(null, "ROLE_CUSTOMER");

            assertThatThrownBy(() -> facade.getCurrentUserId())
                    .isInstanceOf(NullPointerException.class);
        }
    }


    @Nested
    @DisplayName("isAdmin()")
    class IsAdmin {

        @Test
        void returnsTrueWhenUserHasAdminRole() {
            authenticateWithRoles("ROLE_ADMIN");

            assertThat(facade.isAdmin()).isTrue();
        }

        @Test
        void returnsTrueWhenAdminIsOneOfSeveralRoles() {
            authenticateWithRoles("ROLE_CUSTOMER", "ROLE_ADMIN");

            assertThat(facade.isAdmin()).isTrue();
        }

        @Test
        void returnsFalseWhenUserIsOnlyCustomer() {
            authenticateWithRoles("ROLE_CUSTOMER");

            assertThat(facade.isAdmin()).isFalse();
        }

        @Test
        void returnsFalseWhenUserHasNoAuthorities() {
            authenticateWithRoles();

            assertThat(facade.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("requires the exact 'ROLE_ADMIN' string (no prefix-less match)")
        void returnsFalseForRoleWithoutPrefix() {
            authenticateWithRoles("ADMIN");

            assertThat(facade.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("current behaviour: NPE when there is no authentication")
        void throwsNpeWhenNoAuthentication() {
            assertThatThrownBy(() -> facade.isAdmin())
                    .isInstanceOf(NullPointerException.class);
        }
    }


    @Nested
    @DisplayName("isCustomer()")
    class IsCustomer {

        @Test
        void returnsTrueWhenUserHasCustomerRole() {
            authenticateWithRoles("ROLE_CUSTOMER");

            assertThat(facade.isCustomer()).isTrue();
        }

        @Test
        void returnsTrueWhenCustomerIsOneOfSeveralRoles() {
            authenticateWithRoles("ROLE_ADMIN", "ROLE_CUSTOMER");

            assertThat(facade.isCustomer()).isTrue();
        }

        @Test
        void returnsFalseWhenUserIsOnlyAdmin() {
            authenticateWithRoles("ROLE_ADMIN");

            assertThat(facade.isCustomer()).isFalse();
        }

        @Test
        void returnsFalseWhenUserHasNoAuthorities() {
            authenticateWithRoles();

            assertThat(facade.isCustomer()).isFalse();
        }

        @Test
        void returnsFalseForRoleWithoutPrefix() {
            authenticateWithRoles("CUSTOMER");

            assertThat(facade.isCustomer()).isFalse();
        }

        @Test
        @DisplayName("current behaviour: NPE when there is no authentication")
        void throwsNpeWhenNoAuthentication() {
            assertThatThrownBy(() -> facade.isCustomer())
                    .isInstanceOf(NullPointerException.class);
        }
    }
}