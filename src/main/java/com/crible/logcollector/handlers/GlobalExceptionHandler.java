package com.crible.logcollector.handlers;

import com.crible.logcollector.services.CriblFileWatcherService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static Logger logger = LogManager.getLogger(CriblFileWatcherService.class);

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNotFoundError(Model model) {
        System.out.println("No handler found exception");
        String errorMessage = "OOops! Something went wrong - value passed via exception handler.";
        model.addAttribute("errorMessage", errorMessage);
        return "error"; // This will display the "error.html" Thymeleaf template
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ErrorResponse handeAll(ResponseStatusException e) {
        logger.error("Error processing web service response. Reason: " + e.getReason(), e);
        return new ErrorResponseException(e.getStatusCode(), e);
    }

}