package com.cribl.logcollector.controllers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LogControllerTest {

    @Test
    void testValidateStringInput() {
        LogController controller = new LogController();

        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("../../invalid/path/test.txt"));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("!@#$%%^invalidfileName.txt"));
        Assertions.assertDoesNotThrow(() ->controller.validateStringInput("Valid file name.txt"));
    }
}