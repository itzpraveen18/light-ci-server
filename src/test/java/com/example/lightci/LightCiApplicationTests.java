package com.example.lightci;

import com.example.lightci.model.ExecutionStatus;
import com.example.lightci.model.Job;
import com.example.lightci.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LightCiApplicationTests {

    @Autowired
    private JobService jobService;

    /** Verifies that jobs.yml is parsed and at least one job is loaded. */
    @Test
    void contextLoads_andJobsAreLoaded() {
        List<Job> jobs = jobService.getJobs();
        assertFalse(jobs.isEmpty(), "Expected at least one job to be loaded from jobs.yml");
    }

    /** Verifies that a known job ID resolves correctly. */
    @Test
    void helloWorldJobExists() {
        assertTrue(jobService.findJobById("hello-world").isPresent());
    }

    /** Verifies that the hello-world job has commands. */
    @Test
    void helloWorldJobHasCommands() {
        Job job = jobService.findJobById("hello-world").orElseThrow();
        assertNotNull(job.getCommands());
        assertFalse(job.getCommands().isEmpty());
    }

    /** Verifies ExecutionStatus badge helper methods don't throw. */
    @Test
    void executionStatusBadgeAndIconAreNonNull() {
        for (ExecutionStatus s : ExecutionStatus.values()) {
            assertNotNull(s.badgeClass());
            assertNotNull(s.icon());
        }
    }
}
