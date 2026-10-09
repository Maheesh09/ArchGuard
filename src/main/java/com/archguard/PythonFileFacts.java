package com.archguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Python sidecar reports about one file.
 *
 * @param error a parse error message, or {@code null} if the file parsed
 */
public record PythonFileFacts(String file, List<ImportStatement> imports, String error) {

    private static final ObjectMapper mapper = new ObjectMapper();

    public PythonFileFacts {
        imports = imports == null ? List.of() : List.copyOf(imports);
    }

    public boolean hasError() {
        return error != null;
    }

    public static PythonFileFacts fromJson(String json) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new JsonProcessingException("Sidecar output is not a JSON object: " + json) {};
        }

        List<ImportStatement> imports = new ArrayList<>();
        for (JsonNode imp : root.path("imports")) {
            List<String> names = new ArrayList<>();
            imp.path("names").forEach(n -> names.add(n.asText()));
            imports.add(new ImportStatement(
                    textOrNull(imp.get("module")),
                    names,
                    imp.path("level").asInt(0),
                    imp.path("line").asInt(0)));
        }
        return new PythonFileFacts(textOrNull(root.get("file")), imports, textOrNull(root.get("error")));
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
