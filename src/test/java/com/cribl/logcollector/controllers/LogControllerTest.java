package com.cribl.logcollector.controllers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LogControllerTest {

    @Test
    void testValidateStringInput() {
        LogController controller = new LogController();

        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("../../invalid/path/test.txt"));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("/sys/invalidfileName.txt"));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("\\invalidfileName.txt"));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateStringInput("sys/invalidfileName.txt"));
        Assertions.assertDoesNotThrow(() ->controller.validateStringInput("Valid file name.txt"));
    }

    @Test
    void testValidateNumEntriesRequested() {
        LogController controller = new LogController();

        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateNumEntriesRequested(-100));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateNumEntriesRequested(0));
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.validateNumEntriesRequested(Integer.MAX_VALUE));
        Assertions.assertDoesNotThrow(() ->controller.validateNumEntriesRequested(20));
    }
}