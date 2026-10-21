package com.identity.internal.web;

import com.identity.internal.exception.EmailAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        basePackages = "com.smartticket.identity"
)
public class IdentityExceptionHandler {
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request
    )
    {
        ProblemDetail problem= ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Email already exists");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(
                "code",
                "EMAIL_ALREADY_EXISTS"
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }
}
