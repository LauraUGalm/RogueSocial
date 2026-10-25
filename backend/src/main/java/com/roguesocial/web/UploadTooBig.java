package com.roguesocial.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * An upload over the limit in application.properties. The upload is refused while the request is
 * read, before any controller is chosen, so a handler inside a controller never sees it; this
 * one applies to all of them.
 */
@RestControllerAdvice
class UploadTooBig {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    Map<String, String> tooBig(MaxUploadSizeExceededException e) {
        return Map.of("error", "Pictures can be at most 10 MB");
    }
}
