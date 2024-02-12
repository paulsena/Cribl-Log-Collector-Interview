package com.cribl.logcollector.handlers;

import com.cribl.logcollector.services.CriblFileWatcherService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static Logger logger = LogManager.getLogger(CriblFileWatcherService.class);

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ErrorResponse handeAll(ResponseStatusException e) {

        logger.error("Error processing web service response. Reason: " + e.getReason(), e);

        return new ErrorResponseException(e.getStatusCode(), ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()), e);
    }

}