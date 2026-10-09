package com.archguard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only public GitHub HTTPS repositories may be analysed. JGit would happily clone
 * local paths and file:// URLs, which would let a caller read the server's own disk.
 */
public final class RepositoryUrlValidator {

    // owner: GitHub usernames are 1-39 alphanumerics or single hyphens; repo: letters, digits, '.', '_', '-'
    private static final Pattern GITHUB_HTTPS = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?/?$");

    public static class InvalidRepositoryUrlException extends IllegalArgumentException {
        public InvalidRepositoryUrlException(String message) {
            super(message);
        }
    }

    private RepositoryUrlValidator() {}

    /**
     * @return the canonical clone URL, {@code https://github.com/<owner>/<repo>.git}
     * @throws InvalidRepositoryUrlException if the URL is not an HTTPS GitHub repository URL
     */
    public static String validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidRepositoryUrlException("Repository URL is required.");
        }
        String trimmed = url.strip();
        Matcher m = GITHUB_HTTPS.matcher(trimmed);
        if (!m.matches()) {
            throw new InvalidRepositoryUrlException(
                    "Only https://github.com/<owner>/<repo> URLs are supported, got: " + abbreviate(trimmed));
        }
        String repo = m.group(2);
        if (repo.equals(".") || repo.equals("..")) {
            throw new InvalidRepositoryUrlException("Invalid repository name: " + repo);
        }
        return "https://github.com/" + m.group(1) + "/" + repo + ".git";
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
