package com.archguard;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleEngine {

    public static final String DB_CALL_IN_VIEW_LAYER = "DB_CALL_IN_VIEW_LAYER";

    /** Repo-relative globs that identify view-layer files. */
    static final List<String> DEFAULT_VIEW_GLOBS = List.of(
            "**/views/**", "**/views.py",
            "**/ui/**", "**/ui.py");

    /** Database drivers / ORMs a view must not import directly. */
    static final List<String> DEFAULT_DB_MODULES = List.of(
            "sqlite3", "sqlalchemy", "psycopg2", "psycopg", "pymongo", "MySQLdb", "pymysql");

    // DTO to hold rule evaluation results; filePath is repo-relative with '/' separators
    public record ViolationResult(String filePath, String ruleBroken, int lineNumber) {}

    private final GlobMatcher viewLayer;
    private final List<String> forbiddenModules;

    public RuleEngine() {
        this(DEFAULT_VIEW_GLOBS, DEFAULT_DB_MODULES);
    }

    public RuleEngine(List<String> viewGlobs, List<String> forbiddenModules) {
        this.viewLayer = new GlobMatcher(viewGlobs);
        this.forbiddenModules = List.copyOf(forbiddenModules);
    }

    /**
     * Rule: view-layer files must not import database drivers directly.
     *
     * @param relativePath file path relative to the repository root
     */
    public List<ViolationResult> evaluateViewBoundary(String relativePath, List<ImportStatement> imports) {
        String path = GlobMatcher.normalize(relativePath);
        if (!viewLayer.matches(path)) {
            return List.of();
        }

        List<ViolationResult> violations = new ArrayList<>();
        for (ImportStatement imp : imports) {
            if (importsForbiddenModule(imp)) {
                violations.add(new ViolationResult(path, DB_CALL_IN_VIEW_LAYER, imp.line()));
            }
        }
        return violations;
    }

    private boolean importsForbiddenModule(ImportStatement imp) {
        // Relative imports always point inside the analysed project, never at an installed driver
        if (imp.isRelative() || imp.module() == null) {
            return false;
        }
        return forbiddenModules.stream().anyMatch(forbidden -> isSameOrSubmodule(imp.module(), forbidden));
    }

    /** Exact or dotted-prefix match: "sqlalchemy.orm" matches "sqlalchemy", "my_sqlalchemy" does not. */
    static boolean isSameOrSubmodule(String module, String parent) {
        return module.equals(parent) || module.startsWith(parent + ".");
    }
}
