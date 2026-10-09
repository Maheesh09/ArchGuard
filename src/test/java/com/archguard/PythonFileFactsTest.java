package com.archguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonFileFactsTest {

    @Test
    void parsesImportsWithLevelAndNames() throws Exception {
        String json = """
                {"file": "/tmp/x.py", "imports": [
                  {"module": "os.path", "names": [], "level": 0, "line": 1},
                  {"module": null, "names": ["models"], "level": 1, "line": 2}
                ], "error": null}
                """;

        PythonFileFacts facts = PythonFileFacts.fromJson(json);

        assertThat(facts.hasError()).isFalse();
        assertThat(facts.imports()).containsExactly(
                new ImportStatement("os.path", List.of(), 0, 1),
                new ImportStatement(null, List.of("models"), 1, 2));
    }

    @Test
    void parsesErrorEntry() throws Exception {
        PythonFileFacts facts = PythonFileFacts.fromJson(
                "{\"file\": \"bad.py\", \"imports\": [], \"error\": \"SyntaxError: invalid syntax (line 3)\"}");

        assertThat(facts.hasError()).isTrue();
        assertThat(facts.error()).contains("SyntaxError");
        assertThat(facts.imports()).isEmpty();
    }

    @Test
    void rejectsNonObjectOutput() {
        assertThatThrownBy(() -> PythonFileFacts.fromJson("[1, 2]")).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> PythonFileFacts.fromJson("not json")).isInstanceOf(JsonProcessingException.class);
    }
}
