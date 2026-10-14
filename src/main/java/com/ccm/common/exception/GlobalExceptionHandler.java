package com.ccm.common.exception;

import com.ccm.common.pojo.ExceptionRespone;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UpdateException.class)
    public ResponseEntity<ExceptionRespone> updateException(UpdateException ex) {

        return new ResponseEntity<>(
                ExceptionRespone.builder().errorMessage(ex.getMessage()).errorCode(HttpStatus.NOT_FOUND).build(),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidClientIdException.class)
    public ResponseEntity<ExceptionRespone> invalidClientIdException(InvalidClientIdException e) {

        return new ResponseEntity<>(
                ExceptionRespone.builder().errorMessage(e.getMessage()).errorCode(HttpStatus.FORBIDDEN).build(),
                HttpStatus.FORBIDDEN);
    }
}
