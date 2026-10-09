package com.archguard;

import com.archguard.models.Violation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class GitHubReportingService {

    private static final Logger log = LoggerFactory.getLogger(GitHubReportingService.class);
    private static final String COMMENTS_URL = "https://api.github.com/repos/{owner}/{repo}/issues/{pr}/comments";

    private final String githubToken;
    private final RestTemplate restTemplate;

    @Autowired
    public GitHubReportingService(@Value("${github.api.token:}") String githubToken, RestTemplateBuilder builder) {
        this(githubToken, builder.build());
    }

    GitHubReportingService(String githubToken, RestTemplate restTemplate) {
        this.githubToken = githubToken == null ? "" : githubToken.trim();
        this.restTemplate = restTemplate;
        if (!isEnabled()) {
            log.info("[GITHUB] No GITHUB_API_TOKEN configured; pull request reporting is disabled.");
        }
    }

    public boolean isEnabled() {
        return !githubToken.isEmpty();
    }

    /**
     * Posts the violations as a comment on the pull request.
     *
     * @return true if the comment was posted, false if reporting is disabled or the request failed
     */
    public boolean postViolationsToPullRequest(String repoOwner, String repoName, String prNumber, List<Violation> violations) {
        if (!isEnabled()) {
            log.debug("[GITHUB] Skipping PR report for {}/{}#{}: no token configured.", repoOwner, repoName, prNumber);
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");

        // Serialised by Jackson, so quotes, backslashes and newlines are escaped correctly
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("body", buildMarkdown(violations)), headers);

        try {
            // URI template variables are percent-encoded, so they can't alter the path
            restTemplate.exchange(COMMENTS_URL, HttpMethod.POST, entity, String.class, repoOwner, repoName, prNumber);
            log.info("[GITHUB] Successfully posted report to {}/{}#{}", repoOwner, repoName, prNumber);
            return true;
        } catch (RestClientException e) {
            log.error("[GITHUB ERROR] Failed to post comment to {}/{}#{}: {}", repoOwner, repoName, prNumber, e.getMessage());
            return false;
        }
    }

    static String buildMarkdown(List<Violation> violations) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("### 🚨 ArchGuard Architectural Analysis\n");

        if (violations.isEmpty()) {
            markdown.append("✅ No architectural violations detected. Great job!");
        } else {
            markdown.append("The following system design violations were detected in this PR:\n\n");
            markdown.append("| File | Line | Architectural Rule Broken |\n");
            markdown.append("|---|---|---|\n");

            for (Violation v : violations) {
                markdown.append(String.format("| `%s` | %d | **%s** |\n",
                        escapeTableCell(v.getFilePath()), v.getLineNumber(), escapeTableCell(v.getRuleBroken())));
            }
        }
        return markdown.toString();
    }

    private static String escapeTableCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("`", "'").replace("\r", " ").replace("\n", " ");
    }
}
