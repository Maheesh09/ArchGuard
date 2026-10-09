package com.archguard.api;

import com.archguard.models.Violation;

public record ViolationResponse(String filePath, int lineNumber, String rule) {

    public static ViolationResponse from(Violation violation) {
        return new ViolationResponse(violation.getFilePath(), violation.getLineNumber(), violation.getRuleBroken());
    }
}
