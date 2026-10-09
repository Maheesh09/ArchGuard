package com.archguard;

import com.archguard.models.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubReportingServiceTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    @Test
    void skipsReportingWhenTokenIsBlank() {
        GitHubReportingService service = new GitHubReportingService("  ", restTemplate);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.postViolationsToPullRequest("owner", "repo", "1", List.of())).isFalse();
        server.verify(); // no request was made
    }

    @Test
    void postsCommentAsValidJsonEvenWithQuotesAndBackslashes() {
        GitHubReportingService service = new GitHubReportingService("secret", restTemplate);
        String trickyPath = "app\\views\\\"quoted\".py";
        Violation tricky = new Violation(UUID.randomUUID(), trickyPath, "DB_CALL_IN_VIEW_LAYER", 7);

        server.expect(requestTo("https://api.github.com/repos/owner/repo/issues/42/comments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.body").value(containsString(trickyPath)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(service.postViolationsToPullRequest("owner", "repo", "42", List.of(tricky))).isTrue();
        server.verify();
    }

    @Test
    void returnsFalseWhenGitHubRejectsTheRequest() {
        GitHubReportingService service = new GitHubReportingService("secret", restTemplate);
        server.expect(requestTo("https://api.github.com/repos/owner/repo/issues/1/comments"))
                .andRespond(withServerError());

        assertThat(service.postViolationsToPullRequest("owner", "repo", "1", List.of())).isFalse();
        server.verify();
    }

    @Test
    void escapesPipesSoTheMarkdownTableStaysIntact() {
        Violation v = new Violation(UUID.randomUUID(), "a|b.py", "RULE", 3);

        String markdown = GitHubReportingService.buildMarkdown(List.of(v));

        assertThat(markdown).contains("| `a\\|b.py` | 3 | **RULE** |");
    }

    @Test
    void reportsCleanResultWhenThereAreNoViolations() {
        assertThat(GitHubReportingService.buildMarkdown(List.of())).contains("No architectural violations detected");
    }
}
