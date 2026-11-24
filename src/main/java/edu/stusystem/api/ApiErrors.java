package edu.stusystem.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class) ResponseEntity<?> known(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("success", false, "code", e.code(), "message", e.getMessage(), "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "code", "VALIDATION_ERROR", "message", "请求参数不符合要求", "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("success", false, "code", "FORBIDDEN", "message", "无权执行此操作", "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(Map.of("success", false, "code", "FILE_TOO_LARGE", "message", "上传文件超过大小限制", "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "code", "INTERNAL_ERROR", "message", "服务暂时不可用", "timestamp", Instant.now().toString()));
    }
}
