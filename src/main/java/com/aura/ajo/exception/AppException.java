package com.aura.ajo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public AppException(String message) {
        super(message);
        this.errorCode = "APP_ERROR";
        this.status = HttpStatus.BAD_REQUEST;
    }

    public AppException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public static AppException notFound(String entity, Object id) {
        return new AppException(
            entity.toUpperCase() + "_NOT_FOUND",
            entity + " not found: " + id,
            HttpStatus.NOT_FOUND
        );
    }

    public static AppException conflict(String errorCode, String message) {
        return new AppException(errorCode, message, HttpStatus.CONFLICT);
    }

    public static AppException badRequest(String errorCode, String message) {
        return new AppException(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}