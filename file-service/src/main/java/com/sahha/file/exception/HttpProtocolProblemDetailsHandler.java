package com.sahha.file.exception;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Protocol errors only: domain exceptions and authorisation remain service-owned. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpProtocolProblemDetailsHandler {

    @ExceptionHandler({
        ServletRequestBindingException.class, MissingServletRequestPartException.class,
        TypeMismatchException.class, HttpMessageNotReadableException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class,
        NoHandlerFoundException.class, NoResourceFoundException.class,
        MaxUploadSizeExceededException.class, HandlerMethodValidationException.class
    })
    ResponseEntity<ProblemDetail> protocol(Exception exception, HttpServletRequest request) {
        HttpStatus status = exception instanceof ErrorResponse error
                ? HttpStatus.valueOf(error.getStatusCode().value()) : HttpStatus.BAD_REQUEST;
        // Never copy exception messages, rejected values, cookies, headers or query strings.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, switch (status) {
            case BAD_REQUEST -> "The request parameters or body are invalid.";
            case NOT_FOUND -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED -> "The HTTP method is not supported for this resource.";
            case NOT_ACCEPTABLE -> "The requested response format is not supported.";
            case UNSUPPORTED_MEDIA_TYPE -> "The request content type is not supported.";
            case CONTENT_TOO_LARGE -> "The request exceeds the allowed size.";
            default -> "The request could not be completed.";
        });
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:sahha:problem:http-" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        Object requestId = request.getAttribute("sahha.requestId");
        problem.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        // Preserve the server-generated Allow contract without forwarding arbitrary error headers.
        if (exception instanceof HttpRequestMethodNotSupportedException method
                && method.getSupportedHttpMethods() != null) {
            response.allow(method.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        }
        return response.body(problem);
    }
}
