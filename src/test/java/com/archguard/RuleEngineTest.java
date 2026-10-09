package com.archguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    private static ImportStatement importOf(String module, int line) {
        return new ImportStatement(module, List.of(), 0, line);
    }

    private static ImportStatement fromImport(String module, int level, int line, String... names) {
        return new ImportStatement(module, List.of(names), level, line);
    }

    @Test
    void flagsDatabaseDriverImportedFromAViewsPackage() {
        var violations = engine.evaluateViewBoundary("app/views/profile.py",
                List.of(importOf("os", 1), importOf("sqlalchemy", 2)));

        assertThat(violations).containsExactly(
                new RuleEngine.ViolationResult("app/views/profile.py", RuleEngine.DB_CALL_IN_VIEW_LAYER, 2));
    }

    @Test
    void flagsSubmodulesAndFromImportsOfADriver() {
        var violations = engine.evaluateViewBoundary("views/home.py", List.of(
                importOf("sqlalchemy.orm", 3),
                fromImport("psycopg2", 0, 4, "connect"),
                fromImport("sqlite3", 0, 5, "Connection")));

        assertThat(violations).extracting(RuleEngine.ViolationResult::lineNumber).containsExactly(3, 4, 5);
    }

    @Test
    void flagsDjangoStyleViewsModule() {
        var violations = engine.evaluateViewBoundary("shop/views.py", List.of(importOf("sqlite3", 1)));

        assertThat(violations).hasSize(1);
    }

    @Test
    void flagsUiPackage() {
        var violations = engine.evaluateViewBoundary("src/ui/widgets/table.py", List.of(importOf("pymongo", 9)));

        assertThat(violations).hasSize(1);
    }

    @Test
    void doesNotFlagModulesThatOnlyContainADriverNameAsSubstring() {
        var violations = engine.evaluateViewBoundary("app/views/profile.py", List.of(
                importOf("my_sqlalchemy_helpers", 1),
                importOf("sqlite3_utils", 2),
                importOf("flask_sqlalchemy", 3)));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "build.py",
            "requirements.py",
            "guide/quick_start.py",
            "test_suite.py",
            "circuit.py",
            "app/services/user_service.py",
            "app/uikit_helpers.py",
            "reviews/models.py"
    })
    void doesNotTreatFilesWhoseNameMerelyContainsUiOrViewsAsViews(String path) {
        assertThat(engine.evaluateViewBoundary(path, List.of(importOf("sqlite3", 1)))).isEmpty();
    }

    @Test
    void ignoresRelativeImportsEvenIfTheNameLooksLikeADriver() {
        var violations = engine.evaluateViewBoundary("app/views/profile.py", List.of(
                fromImport("sqlite3", 1, 1, "helper"),
                fromImport(null, 1, 2, "sqlalchemy")));

        assertThat(violations).isEmpty();
    }

    @Test
    void normalisesWindowsSeparatorsAndReportsTheNormalisedPath() {
        var violations = engine.evaluateViewBoundary("app\\views\\profile.py", List.of(importOf("sqlite3", 1)));

        assertThat(violations).extracting(RuleEngine.ViolationResult::filePath).containsExactly("app/views/profile.py");
    }

    @Test
    void usesCustomLayerGlobsAndModules() {
        RuleEngine custom = new RuleEngine(List.of("web/handlers/**"), List.of("redis"));

        assertThat(custom.evaluateViewBoundary("web/handlers/a.py", List.of(importOf("redis.client", 1)))).hasSize(1);
        assertThat(custom.evaluateViewBoundary("app/views/a.py", List.of(importOf("redis", 1)))).isEmpty();
        assertThat(custom.evaluateViewBoundary("web/handlers/a.py", List.of(importOf("sqlite3", 1)))).isEmpty();
    }

    @Test
    void dottedPrefixMatchRequiresAFullSegment() {
        assertThat(RuleEngine.isSameOrSubmodule("sqlalchemy", "sqlalchemy")).isTrue();
        assertThat(RuleEngine.isSameOrSubmodule("sqlalchemy.orm.session", "sqlalchemy")).isTrue();
        assertThat(RuleEngine.isSameOrSubmodule("sqlalchemyx", "sqlalchemy")).isFalse();
        assertThat(RuleEngine.isSameOrSubmodule("x.sqlalchemy", "sqlalchemy")).isFalse();
    }
}
