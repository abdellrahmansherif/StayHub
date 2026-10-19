package com.identity.internal.application;

import com.identity.internal.domain.User;
import com.identity.internal.domain.UserPrincipal;
import com.identity.internal.exceptions.EmailNotFoundException;
import com.identity.internal.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {
    @Autowired
    public UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user=userRepository.findByEmailIgnoreCase(email).orElseThrow(EmailNotFoundException::new);

        return new UserPrincipal(user);
    }
}
