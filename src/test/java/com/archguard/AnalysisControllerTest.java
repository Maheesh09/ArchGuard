package com.archguard;

import com.archguard.models.AnalysisJob;
import com.archguard.models.Violation;
import com.archguard.repositories.AnalysisJobRepository;
import com.archguard.repositories.ViolationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RabbitTemplate rabbitTemplate;
    @MockBean
    private AnalysisJobRepository jobRepository;
    @MockBean
    private ViolationRepository violationRepository;

    private static AnalysisJob jobWithId(UUID id, String url) {
        AnalysisJob job = new AnalysisJob(url);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    @Test
    void submitQueuesAValidGitHubRepository() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobRepository.save(any(AnalysisJob.class)))
                .thenAnswer(inv -> jobWithId(id, inv.<AnalysisJob>getArgument(0).getRepositoryUrl()));

        mvc.perform(post("/api/jobs").contentType(MediaType.TEXT_PLAIN).content("https://github.com/Maheesh09/ArchGuard"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/jobs/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/Maheesh09/ArchGuard.git"));

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.QUEUE_NAME, id.toString());
    }

    @Test
    void submitRejectsLocalPathsAndFileUrls() throws Exception {
        for (String url : List.of("file:///etc/passwd", "/etc", "C:\\Windows")) {
            mvc.perform(post("/api/jobs").contentType(MediaType.TEXT_PLAIN).content(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("github.com")));
        }

        verify(jobRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void submitRejectsAnEmptyBody() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobReturnsStatusAndViolationCount() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob job = jobWithId(id, "https://github.com/o/r.git");
        job.markInProgress();
        job.markCompleted(10, 1);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(violationRepository.countByJobId(id)).thenReturn(3L);

        mvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.filesAnalyzed").value(10))
                .andExpect(jsonPath("$.filesSkipped").value(1))
                .andExpect(jsonPath("$.violationCount").value(3));
    }

    @Test
    void getJobReturns404ForUnknownJobAnd400ForMalformedId() throws Exception {
        when(jobRepository.findById(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/jobs/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void getViolationsListsTheJobsViolations() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(jobWithId(id, "https://github.com/o/r.git")));
        when(violationRepository.findByJobIdOrderByFilePathAscLineNumberAsc(eq(id))).thenReturn(List.of(
                new Violation(id, "app/views/a.py", "DB_CALL_IN_VIEW_LAYER", 2)));

        mvc.perform(get("/api/jobs/{id}/violations", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filePath").value("app/views/a.py"))
                .andExpect(jsonPath("$[0].lineNumber").value(2))
                .andExpect(jsonPath("$[0].rule").value("DB_CALL_IN_VIEW_LAYER"));
    }

    @Test
    void getViolationsReturns404ForUnknownJob() throws Exception {
        when(jobRepository.findById(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/jobs/{id}/violations", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
