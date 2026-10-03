package com.example.backend.exception;

import com.example.backend.api.ApiError;
import com.example.backend.api.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Central mapping from exceptions to HTTP responses (owner: B01-F1).
 *
 * <ul>
 *   <li>Requests under {@code /api/v1/**} get the contract error shape
 *       {@code {"error": {"code", "message", "fields"?, "traceId"?}}}.</li>
 *   <li>Legacy {@code /api/**} requests keep {@code {"message": ...}} and today's status codes.</li>
 *   <li>Unknown exceptions never leak their text: 500 with a generic message on every path; the real exception is
 *       logged server-side together with a random trace id (returned to the client as {@code error.traceId}
 *       on /api/v1 only).</li>
 * </ul>
 * Messages of the application exceptions (NotFound/BadRequest/...) are written by our own services for end users
 * and are passed through unchanged. Validation errors carry only constraint messages, never rejected values.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String GENERIC_ERROR = "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.";
    static final String INVALID_REQUEST = "Dữ liệu gửi lên không hợp lệ.";
    static final String VALIDATION_FAILED = "Dữ liệu không hợp lệ. Vui lòng kiểm tra lại các trường được nêu.";
    static final String INVALID_FIELD = "Giá trị không hợp lệ.";

    private static final String V1_PREFIX = "/api/v1";

    // ── helpers ──────────────────────────────────────────────────────────────

    /** True for /api/v1 and /api/v1/** (context path stripped). */
    static boolean isV1(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return false;
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return path.equals(V1_PREFIX) || path.startsWith(V1_PREFIX + "/");
    }

    private ResponseEntity<Object> legacy(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }

    /** Contract error on /api/v1, {@code {message}} on legacy paths; status comes from the code on /api/v1. */
    private ResponseEntity<Object> respond(HttpServletRequest request, HttpStatus legacyStatus,
                                           ApiErrorCode code, String message) {
        if (isV1(request)) {
            return ResponseEntity.status(code.httpStatus()).body(ApiError.of(code, message));
        }
        return legacy(legacyStatus, message);
    }

    private ResponseEntity<Object> validation(HttpServletRequest request, Map<String, String> fields) {
        if (isV1(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiError.of(ApiErrorCode.VALIDATION_ERROR, VALIDATION_FAILED, fields));
        }
        return legacy(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
    }

    /** Logs the real exception server-side (never the request body/query) and returns a generic 500. */
    private ResponseEntity<Object> internalError(HttpServletRequest request, Throwable ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception traceId={} method={} path={}",
                traceId, request.getMethod(), request.getRequestURI(), ex);
        if (isV1(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiError.internal(GENERIC_ERROR, traceId));
        }
        return legacy(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }

    /** Never let a secret-looking field name carry a (possibly value-interpolated) message. */
    private static String safeFieldMessage(String field, String message) {
        String f = field == null ? "" : field.toLowerCase(Locale.ROOT);
        if (f.contains("password") || f.contains("token") || f.contains("secret") || f.contains("credential")
                || message == null || message.isBlank()) {
            return INVALID_FIELD;
        }
        return message;
    }

    private static void putField(Map<String, String> fields, String field, String message) {
        String name = (field == null || field.isBlank()) ? "_global" : field;
        fields.putIfAbsent(name, safeFieldMessage(name, message));
    }

    // ── application exceptions (existing classes, unchanged messages) ────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, ex.getMessage());
    }

    /** On /api/v1 a BadRequestException is a VALIDATION_ERROR (V2); legacy stays 400 {message}. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Object> handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, ApiErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHENTICATED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, ex.getMessage());
    }

    // ── validation / malformed input (message text of the framework is NEVER returned) ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            putField(fields, fe.getField(), fe.getDefaultMessage()); // never fe.getRejectedValue()
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            putField(fields, oe.getObjectName(), oe.getDefaultMessage());
        }
        return validation(request, fields);
    }

    /** Spring 6.1+: constraint annotations directly on controller parameters (e.g. {@code @Min} on a request param). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Object> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                for (FieldError fe : errors.getFieldErrors()) {
                    putField(fields, fe.getField(), fe.getDefaultMessage());
                }
            } else {
                String name = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    putField(fields, name, error.getDefaultMessage());
                }
            }
        }
        return validation(request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = String.valueOf(v.getPropertyPath());
            String name = path.substring(path.lastIndexOf('.') + 1); // "create.request.name" -> "name"
            putField(fields, name, v.getMessage()); // never v.getInvalidValue()
        }
        return validation(request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Jackson's message can contain class names / raw input: not returned.
        return respond(request, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "Nội dung yêu cầu bị thiếu hoặc không đúng định dạng.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, ex.getName(), INVALID_FIELD); // do not echo ex.getValue()
        return validation(request, fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParameter(MissingServletRequestParameterException ex,
                                                         HttpServletRequest request) {
        if (!isV1(request)) {
            return internalError(request, ex); // legacy: unchanged status (500), now without leaking text
        }
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, ex.getParameterName(), "Tham số bắt buộc.");
        return validation(request, fields);
    }

    // ── catch-alls: never expose exception text ──────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return internalError(request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex, HttpServletRequest request) {
        return internalError(request, ex);
    }
}
