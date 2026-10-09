package com.archguard;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches repo-relative, forward-slash paths against glob patterns.
 * <ul>
 *   <li>{@code *} matches anything except {@code /}</li>
 *   <li>{@code ?} matches one character except {@code /}</li>
 *   <li>{@code **} matches across directories; {@code **}{@code /} may also match nothing,
 *       so {@code **}{@code /views/**} matches {@code views/a.py} at the repo root</li>
 * </ul>
 */
public final class GlobMatcher {

    private final List<String> globs;
    private final List<Pattern> patterns;

    public GlobMatcher(List<String> globs) {
        this.globs = List.copyOf(globs);
        this.patterns = this.globs.stream().map(GlobMatcher::toPattern).toList();
    }

    public boolean matches(String path) {
        String normalized = normalize(path);
        return patterns.stream().anyMatch(p -> p.matcher(normalized).matches());
    }

    public List<String> globs() {
        return globs;
    }

    /** Converts OS separators to {@code /} and strips a leading {@code ./} or {@code /}. */
    public static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    static Pattern toPattern(String glob) {
        String g = normalize(glob);
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < g.length()) {
            char c = g.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < g.length() && g.charAt(i + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = i + 2 < g.length() && g.charAt(i + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?"); // "**/" = zero or more directories
                        i += 3;
                    } else {
                        regex.append(".*");
                        i += 2;
                    }
                } else {
                    regex.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return Pattern.compile(regex.toString());
    }
}
