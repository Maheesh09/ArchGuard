package com.archguard;

import com.archguard.models.Violation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class GitHubReportingService {

    private static final Logger log = LoggerFactory.getLogger(GitHubReportingService.class);

    @Value("${github.api.token}")
    private String githubToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public void postViolationsToPullRequest(String repoOwner, String repoName, String prNumber, List<Violation> violations) {
        
        // 1. Format the violations into a readable Markdown table
        StringBuilder markdownComment = new StringBuilder();
        markdownComment.append("### 🚨 ArchGuard Architectural Analysis\n");
        
        if (violations.isEmpty()) {
            markdownComment.append("✅ No architectural violations detected. Great job!");
        } else {
            markdownComment.append("The following system design violations were detected in this PR:\n\n");
            markdownComment.append("| File | Line | Architectural Rule Broken |\n");
            markdownComment.append("|---|---|---|\n");
            
            for (Violation v : violations) {
                markdownComment.append(String.format("| `%s` | %d | **%s** |\n", 
                        v.getFilePath(), v.getLineNumber(), v.getRuleBroken()));
            }
        }

        // 2. Set up the GitHub API request headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        // 3. Construct the JSON payload required by GitHub
        String requestBody = String.format("{\"body\": \"%s\"}", markdownComment.toString().replace("\n", "\\n"));
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // 4. Send the POST request to GitHub's issue comment endpoint
        String url = String.format("https://api.github.com/repos/%s/%s/issues/%s/comments", repoOwner, repoName, prNumber);
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("[GITHUB] Successfully posted report to PR #" + prNumber);
        } catch (Exception e) {
            log.error("[GITHUB ERROR] Failed to post comment: " + e.getMessage());
        }
    }
}