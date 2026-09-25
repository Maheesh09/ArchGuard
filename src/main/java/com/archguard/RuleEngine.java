package com.archguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RuleEngine {

    private static final ObjectMapper mapper = new ObjectMapper();

    public void evaluateViewBoundary(String astJson) throws Exception {
        JsonNode root = mapper.readTree(astJson);
        String filePath = root.get("file").asText();
        JsonNode imports = root.get("imports");

        boolean isViewFile = filePath.contains("views") || filePath.contains("ui");

        if (isViewFile && imports.isArray()) {
            for (JsonNode imp : imports) {
                String importedModule = imp.has("module") ? imp.get("module").asText() : imp.get("name").asText();
                
                // Rule: Views cannot import database drivers directly
                if (importedModule.contains("sqlite3") || importedModule.contains("sqlalchemy") || importedModule.contains("psycopg2")) {
                    System.out.printf("[VIOLATION DETECTED] File: %s | Line: %d | Forbidden Import: %s in View Layer%n",
                            filePath, imp.get("line").asInt(), importedModule);
                }
            }
        }
    }
}