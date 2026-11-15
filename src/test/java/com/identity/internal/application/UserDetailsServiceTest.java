package com.identity.internal.application;

import com.identity.internal.domain.User;
import com.identity.internal.domain.UserPrincipal;
import com.identity.internal.exception.EmailNotFoundException;
import com.identity.internal.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("returns a UserPrincipal when the email exists")
    void loadUserByUsername_returnsPrincipalWhenUserExists() {
        User user = new User();
        when(userRepository.findByEmailIgnoreCase("ali@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("ali@example.com");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        verify(userRepository).findByEmailIgnoreCase("ali@example.com");
    }

    @Test
    @DisplayName("throws EmailNotFoundException when the email does not exist")
    void loadUserByUsername_throwsWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(EmailNotFoundException.class)
                .hasMessage("Email Not Found");
    }
}