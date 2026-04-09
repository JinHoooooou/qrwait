package com.qrwait.api.shared.web;

import com.qrwait.api.domain.model.DuplicateEmailException;
import com.qrwait.api.domain.model.InvalidCredentialsException;
import com.qrwait.api.store.domain.StoreNotAvailableException;
import com.qrwait.api.store.domain.StoreNotFoundException;
import com.qrwait.api.waiting.domain.WaitingNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InvalidCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorResponse handleInvalidCredentials(InvalidCredentialsException e) {
    return ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage());
  }

  @ExceptionHandler(DuplicateEmailException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleDuplicateEmail(DuplicateEmailException e) {
    return ErrorResponse.of("DUPLICATE_EMAIL", e.getMessage());
  }

  @ExceptionHandler(WaitingNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleWaitingNotFound(WaitingNotFoundException e) {
    return ErrorResponse.of("WAITING_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(StoreNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleStoreNotFound(StoreNotFoundException e) {
    return ErrorResponse.of("STORE_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining(", "));
    return ErrorResponse.of("INVALID_REQUEST", message);
  }

  @ExceptionHandler(StoreNotAvailableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleStoreNotAvailable(StoreNotAvailableException e) {
    return ErrorResponse.of("STORE_NOT_AVAILABLE", e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleIllegalState(IllegalStateException e) {
    return ErrorResponse.of("INVALID_STATUS_TRANSITION", e.getMessage());
  }
}
