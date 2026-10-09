package io.github.gagann06.internshiptracker.error;

import io.github.gagann06.internshiptracker.account.IncorrectPasswordException;
import io.github.gagann06.internshiptracker.application.ApplicationNotFoundException;
import io.github.gagann06.internshiptracker.application.StatusUnchangedException;
import io.github.gagann06.internshiptracker.application.UnknownCompanyException;
import io.github.gagann06.internshiptracker.auth.DuplicateEmailException;
import io.github.gagann06.internshiptracker.auth.InvalidCredentialsException;
import io.github.gagann06.internshiptracker.company.CompanyHasApplicationsException;
import io.github.gagann06.internshiptracker.company.CompanyNotFoundException;
import io.github.gagann06.internshiptracker.company.DuplicateCompanyNameException;


import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public record FieldViolation(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();

        ProblemDetail problem = ex.getBody();
        problem.setDetail(violations.stream()
                .map(violation -> violation.field() + ": " + violation.message())
                .collect(Collectors.joining("; ")));
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail companyNotFound(CompanyNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateCompanyNameException.class)
    public ProblemDetail duplicateCompany(DuplicateCompanyNameException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataViolation(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This request conflicts with existing data.");
    }

    @ExceptionHandler(UnknownCompanyException.class)
    public ProblemDetail unknownCompany(UnknownCompanyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ProblemDetail applicationNotFound(ApplicationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,ex.getMessage());
    }

    @ExceptionHandler(CompanyHasApplicationsException.class)
    public ProblemDetail companyHasApplications(CompanyHasApplicationsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,ex.getMessage());
    }

    @ExceptionHandler(StatusUnchangedException.class)
    public ProblemDetail applicationStatusUnchanged(StatusUnchangedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail duplicateEmail(DuplicateEmailException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IncorrectPasswordException.class)
    public ProblemDetail incorrectPassword(IncorrectPasswordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}