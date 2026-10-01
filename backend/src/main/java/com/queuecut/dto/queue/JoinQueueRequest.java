package com.queuecut.dto.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class JoinQueueRequest {

    @NotBlank(message = "Student name is required")
    @Size(min = 2, max = 100, message = "Student name must be between 2 and 100 characters")
    private String studentName;

    /**
     * FAST roll number: batch year 21–29, campus letter (F, M, K, L, I, P — any case), dash, 4 digits.
     * e.g. 24F-3089. Surrounding spaces are allowed (trimmed by the service).
     */
    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^\\s*2[1-9][fFmMkKlLiIpP]-[0-9]{4}\\s*$",
            message = "Roll number must look like 24F-3089: batch 21–29, then F/M/K/L/I/P, then a dash and 4 digits")
    private String studentId;

    public JoinQueueRequest() {
    }

    public JoinQueueRequest(String studentName, String studentId) {
        this.studentName = studentName;
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
}
