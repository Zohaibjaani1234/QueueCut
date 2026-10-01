package com.queuecut.dto.queue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JoinQueueRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<JoinQueueRequest>> studentIdViolations(String studentId) {
        return validator.validateProperty(new JoinQueueRequest("Usman Tariq", studentId), "studentId");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "21F-0000", "29P-9999", "24F-3089", "24f-3089",
            "22M-1234", "23m-1234", "25K-1234", "26k-1234",
            "27L-1234", "28l-1234", "21I-1234", "22i-1234", "23p-1234",
            " 24F-3089 "
    })
    @DisplayName("studentId: accepts batch 21–29 + F/M/K/L/I/P (any case) + dash + 4 digits")
    void validRollNumbers(String studentId) {
        assertThat(studentIdViolations(studentId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20F-1234",   // batch 20 is not above 20
            "30F-1234",   // batch 30 is not below 30
            "19F-1234", "2F-1234", "124F-1234",
            "24X-1234",   // campus letter not allowed
            "24A-1234", "24-1234", "24FF-1234",
            "24F1234",    // missing dash
            "24F_1234", "24F-123", "24F-12345", "24F-12a4",
            "abc", "21K3890"
    })
    @DisplayName("studentId: rejects anything outside the roll-number format")
    void invalidRollNumbers(String studentId) {
        assertThat(studentIdViolations(studentId)).isNotEmpty();
    }
}
