package net.snobo.rest;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import net.snobo.service.exception.ClaimNotFoundException;
import net.snobo.service.exception.DuplicateClaimException;
import net.snobo.service.exception.DuplicatePaymentReferenceException;
import net.snobo.service.exception.DuplicateRefundReferenceException;
import net.snobo.service.exception.InsufficientCreditException;
import net.snobo.service.exception.InvalidClaimRequestException;
import net.snobo.service.exception.InvalidPaymentPlanRequestException;
import net.snobo.service.exception.InvalidPaymentRequestException;
import net.snobo.service.exception.InvalidRefundRequestException;
import net.snobo.service.exception.PaymentConflictException;
import net.snobo.service.exception.PaymentNotFoundException;
import net.snobo.service.exception.PaymentPlanAlreadyExistsException;
import net.snobo.service.exception.PaymentPlanNotFoundException;
import net.snobo.service.exception.RefundConflictException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID_REQUEST = "Invalid request";

    @ExceptionHandler(ClaimNotFoundException.class)
    public ProblemDetail handleClaimNotFound(ClaimNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Claim not found", exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentPlanNotFoundException.class)
    public ProblemDetail handlePaymentPlanNotFound(PaymentPlanNotFoundException exception,
                                                   HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Payment plan not found", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateClaimException.class)
    public ProblemDetail handleDuplicateClaim(DuplicateClaimException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Claim already exists", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidClaimRequestException.class)
    public ProblemDetail handleInvalidClaimRequest(InvalidClaimRequestException exception,
                                                   HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentPlanAlreadyExistsException.class)
    public ProblemDetail handlePaymentPlanAlreadyExists(PaymentPlanAlreadyExistsException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Payment plan already exists",
                exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidPaymentPlanRequestException.class)
    public ProblemDetail handleInvalidPaymentPlanRequest(InvalidPaymentPlanRequestException exception,
                                                         HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "claimNumber must be a valid number", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                      HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body",
                "Request body is missing or malformed", request);
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ProblemDetail handleInvalidPaymentRequest(InvalidPaymentRequestException exception,
                                                     HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid payment request",
                exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicatePaymentReferenceException.class)
    public ProblemDetail handleDuplicatePaymentReference(DuplicatePaymentReferenceException exception,
                                                         HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Payment reference already exists",
                exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ProblemDetail handlePaymentConflict(PaymentConflictException exception,
                                               HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Payment conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handlePessimisticLocking(PessimisticLockingFailureException exception,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Payment conflict",
                "Another payment is being recorded; please retry", request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException exception,
                                               HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefundRequestException.class)
    public ProblemDetail handleInvalidRefundRequest(InvalidRefundRequestException exception,
                                                    HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid refund request",
                exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRefundReferenceException.class)
    public ProblemDetail handleDuplicateRefundReference(DuplicateRefundReferenceException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Refund reference already exists",
                exception.getMessage(), request);
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ProblemDetail handleInsufficientCredit(InsufficientCreditException exception,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Insufficient credit",
                exception.getMessage(), request);
    }

    @ExceptionHandler(RefundConflictException.class)
    public ProblemDetail handleRefundConflict(RefundConflictException exception,
                                              HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Refund conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found",
                "No resource for " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "HTTP method is not supported for this resource", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
                                                     HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "Content type is not supported", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception,
                                                      HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data",
                "The request conflicts with existing data", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
