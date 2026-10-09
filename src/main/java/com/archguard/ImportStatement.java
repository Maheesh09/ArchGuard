package com.archguard;

import java.util.List;

/**
 * One import statement as reported by a language sidecar.
 *
 * @param module dotted module name ({@code null} for {@code from . import x})
 * @param names  names imported with {@code from module import a, b}; empty for plain {@code import}
 * @param level  0 for absolute imports, 1+ for relative imports ({@code from .. import x} is 2)
 * @param line   1-based source line
 */
public record ImportStatement(String module, List<String> names, int level, int line) {

    public ImportStatement {
        names = names == null ? List.of() : List.copyOf(names);
    }

    public boolean isRelative() {
        return level > 0;
    }
}
