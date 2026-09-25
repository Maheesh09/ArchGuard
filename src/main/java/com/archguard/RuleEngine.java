package com.archguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class RuleEngine {

    private static final ObjectMapper mapper = new ObjectMapper();

    // DTO to hold rule evaluation results
    public record ViolationResult(String filePath, String ruleBroken, int lineNumber) {}

    public List<ViolationResult> evaluateViewBoundary(String astJson) throws Exception {
        List<ViolationResult> violations = new ArrayList<>();
        JsonNode root = mapper.readTree(astJson);
        String filePath = root.get("file").asText();
        JsonNode imports = root.get("imports");

        boolean isViewFile = filePath.contains("views") || filePath.contains("ui");

        if (isViewFile && imports.isArray()) {
            for (JsonNode imp : imports) {
                String importedModule = imp.has("module") ? imp.get("module").asText() : imp.get("name").asText();

                // Rule: Views cannot import database drivers directly
                if (importedModule.contains("sqlite3") || importedModule.contains("sqlalchemy") || importedModule.contains("psycopg2")) {
                    violations.add(new ViolationResult(
                            filePath,
                            "DB_CALL_IN_VIEW_LAYER",
                            imp.get("line").asInt()
                    ));
                }
            }
        }
        return violations;
    }
}