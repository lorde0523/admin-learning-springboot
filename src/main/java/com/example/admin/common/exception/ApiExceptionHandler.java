package com.example.admin.common.exception;

import com.example.admin.common.response.ErrorResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("NOT_FOUND", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::validationDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed.", details));
    }

    private ErrorResponse error(String code, String message, List<String> details) {
        return new ErrorResponse(code, message, details, LocalDateTime.now());
    }

    private String validationDetail(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}

