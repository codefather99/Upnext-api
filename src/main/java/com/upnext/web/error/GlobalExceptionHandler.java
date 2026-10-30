package com.upnext.web.error;

import com.upnext.domain.NotFoundException;
import com.upnext.domain.StaleVersionException;
import com.upnext.domain.UserRepository;
import com.upnext.domain.VotingClosedException;
import com.upnext.web.RequestIdFilter;
import com.upnext.web.dto.Dto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/** THE exception table: services throw, this decides the status and `code`. The interface switches on `code`. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return respond(problem(HttpStatus.NOT_FOUND, "Not found", "NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(VotingClosedException.class)
    ResponseEntity<ProblemDetail> votingClosed(VotingClosedException e) {
        return respond(problem(HttpStatus.CONFLICT, "Voting is closed", "VOTING_CLOSED", e.getMessage()));
    }

    @ExceptionHandler(StaleVersionException.class)
    ResponseEntity<ProblemDetail> stale(StaleVersionException e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "This card has moved", "STALE_VERSION", e.getMessage());
        p.setProperty("current", e.current());
        return respond(p);
    }

    @ExceptionHandler(UserRepository.EmailTakenException.class)
    ResponseEntity<ProblemDetail> emailTaken(UserRepository.EmailTakenException e) {
        return respond(problem(HttpStatus.CONFLICT, "Email already in use", "EMAIL_TAKEN", e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsApiException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsApiException e) {
        return respond(problem(HttpStatus.UNAUTHORIZED, "Not signed in", "UNAUTHENTICATED", e.getMessage()));
    }

    @ExceptionHandler(ApiValidationException.class)
    ResponseEntity<ProblemDetail> apiValidation(ApiValidationException e) { return validation(e.fieldErrors()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> beanValidation(MethodArgumentNotValidException e) {
        return validation(e.getBindingResult().getFieldErrors().stream()
                .map(f -> new Dto.FieldError(f.getField(), f.getDefaultMessage())).toList());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new Dto.FieldError(e.getName(), "Invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> missingParam(MissingServletRequestParameterException e) {
        return validation(List.of(new Dto.FieldError(e.getParameterName(), "This parameter is required")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e) {
        return validation(List.of(new Dto.FieldError("body", "The request body is missing or malformed")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> fallback(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            String code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.is4xxClientError() ? "VALIDATION_FAILED" : "INTERNAL_ERROR";
            ProblemDetail p = er.getBody();
            p.setProperty("code", code);
            p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
            return respond(p);
        }
        log.error("Unhandled exception", e);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", "INTERNAL_ERROR",
                "Something went wrong on our side. Voting and moving cards are safe to retry. Quote the request id if it keeps happening."));
    }

    private ResponseEntity<ProblemDetail> validation(List<Dto.FieldError> errors) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", "VALIDATION_FAILED", "Some fields need attention.");
        p.setProperty("fieldErrors", errors);
        return respond(p);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setProperty("code", code);
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return p;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail p) {
        return ResponseEntity.status(p.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }
}
