package com.j2ee.HaircutSchedule.exception;

import com.j2ee.HaircutSchedule.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handler for ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Generic Handler for exceptions that were not handled
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("INTERNAL SERVER ERROR: " + ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
