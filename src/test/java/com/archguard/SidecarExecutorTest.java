package com.archguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real Python sidecar, so Python 3 must be on the PATH. */
class SidecarExecutorTest {

    private static final String SCRIPT = "sidecars/python/parser.py";

    @TempDir
    Path tmp;

    private final SidecarExecutor executor = new SidecarExecutor("", SCRIPT, 30);

    private Path write(String name, String content) throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void extractsAbsoluteAndRelativeImports() throws Exception {
        Path file = write("view.py", """
                import os, sqlalchemy.orm as orm
                from flask import Flask, request
                from . import models
                from ..services.user import UserService
                """);

        PythonFileFacts facts = executor.parsePythonFile(file);

        assertThat(facts.hasError()).isFalse();
        assertThat(facts.imports()).containsExactly(
                new ImportStatement("os", List.of(), 0, 1),
                new ImportStatement("sqlalchemy.orm", List.of(), 0, 1),
                new ImportStatement("flask", List.of("Flask", "request"), 0, 2),
                new ImportStatement(null, List.of("models"), 1, 3),
                new ImportStatement("services.user", List.of("UserService"), 2, 4));
    }

    @Test
    void reportsSyntaxErrorAsDataInsteadOfFailing() throws Exception {
        Path file = write("legacy.py", "import os\nprint \"python 2\"\n");

        PythonFileFacts facts = executor.parsePythonFile(file);

        assertThat(facts.hasError()).isTrue();
        assertThat(facts.error()).startsWith("SyntaxError").contains("line 2");
        assertThat(facts.imports()).isEmpty();
    }

    @Test
    void reportsNullBytesAsDataInsteadOfFailing() throws Exception {
        Path file = tmp.resolve("binary.py");
        Files.write(file, new byte[]{'i', 'm', 'p', 'o', 'r', 't', ' ', 'o', 's', 0, '\n'});

        PythonFileFacts facts = executor.parsePythonFile(file);

        assertThat(facts.hasError()).isTrue();
    }

    @Test
    void warningsDoNotCorruptTheJsonOutput() throws Exception {
        // "\d" is an invalid escape sequence: Python emits a SyntaxWarning on stderr
        Path file = write("regex.py", "import re\nPATTERN = \"\\d+\"\n");

        PythonFileFacts facts = executor.parsePythonFile(file);

        assertThat(facts.hasError()).isFalse();
        assertThat(facts.imports()).extracting(ImportStatement::module).containsExactly("re");
    }

    @Test
    void killsTheSidecarWhenItExceedsTheTimeout() throws Exception {
        Path slowScript = write("slow.py", "import time\ntime.sleep(30)\n");
        SidecarExecutor slow = new SidecarExecutor("", slowScript.toString(), 1);

        assertThatThrownBy(() -> slow.parsePythonFile(write("a.py", "")))
                .isInstanceOf(SidecarExecutor.SidecarException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void nonZeroExitIsAPerFileFailure() throws Exception {
        Path crashingScript = write("crash.py", "import sys\nsys.stderr.write('boom')\nsys.exit(3)\n");
        SidecarExecutor crashing = new SidecarExecutor("", crashingScript.toString(), 10);

        assertThatThrownBy(() -> crashing.parsePythonFile(write("a.py", "")))
                .isInstanceOf(SidecarExecutor.SidecarException.class)
                .hasMessageContaining("code 3")
                .hasMessageContaining("boom");
    }

    @Test
    void nonJsonOutputIsAPerFileFailure() throws Exception {
        Path chattyScript = write("chatty.py", "print('hello')\n");
        SidecarExecutor chatty = new SidecarExecutor("", chattyScript.toString(), 10);

        assertThatThrownBy(() -> chatty.parsePythonFile(write("a.py", "")))
                .isInstanceOf(SidecarExecutor.SidecarException.class)
                .hasMessageContaining("invalid JSON");
    }

    @Test
    void missingInterpreterOrScriptMeansTheSidecarIsUnavailable() throws Exception {
        Path file = write("a.py", "");

        assertThatThrownBy(() -> new SidecarExecutor("definitely-not-a-python-binary", SCRIPT, 10).parsePythonFile(file))
                .isInstanceOf(SidecarExecutor.SidecarUnavailableException.class);
        assertThatThrownBy(() -> new SidecarExecutor("", tmp.resolve("missing.py").toString(), 10).parsePythonFile(file))
                .isInstanceOf(SidecarExecutor.SidecarUnavailableException.class);
    }
}
