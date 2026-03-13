package com.example.lightci.service;

import com.example.lightci.executor.ShellExecutor;
import com.example.lightci.model.ExecutionStatus;
import com.example.lightci.model.Job;
import com.example.lightci.model.JobExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Central service that owns the job registry and execution history.
 *
 * <p><strong>Startup:</strong> {@code @PostConstruct init()} reads {@code jobs.yml}
 * from the classpath and creates the logs directory.</p>
 *
 * <p><strong>Job trigger:</strong> {@code triggerJob()} is annotated with
 * {@code @Async} so the HTTP request returns immediately while the shell
 * commands run in a Spring-managed thread pool in the background.</p>
 *
 * <p><strong>History:</strong> Stored in a {@link CopyOnWriteArrayList} which
 * is thread-safe for concurrent reads (many requests) and occasional writes
 * (one write per job trigger).</p>
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ShellExecutor shellExecutor;

    /** Loaded once at startup from classpath:jobs.yml */
    private List<Job> jobs = new ArrayList<>();

    /** Thread-safe execution history (newest first when retrieved) */
    private final List<JobExecution> executions = new CopyOnWriteArrayList<>();

    @Value("${lightci.logs-dir:logs}")
    private String logsDir;

    public JobService(ShellExecutor shellExecutor) {
        this.shellExecutor = shellExecutor;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by Spring after the bean is created.
     * Loads job definitions and ensures the logs directory exists.
     */
    @PostConstruct
    public void init() throws IOException {
        loadJobs();
        Files.createDirectories(Paths.get(logsDir));
        log.info("LightCI started — {} job(s) loaded, logs dir: {}", jobs.size(), logsDir);
    }

    /**
     * Parses {@code classpath:jobs.yml} using Jackson YAML into a
     * {@link JobsWrapper} (which holds the {@code jobs:} list).
     */
    private void loadJobs() throws IOException {
        ClassPathResource resource = new ClassPathResource("jobs.yml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobsWrapper wrapper = mapper.readValue(resource.getInputStream(), JobsWrapper.class);
        this.jobs = (wrapper.getJobs() != null) ? wrapper.getJobs() : new ArrayList<>();
        jobs.forEach(j -> log.debug("  Loaded job: {} ({})", j.getName(), j.getId()));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Job> getJobs() {
        return Collections.unmodifiableList(jobs);
    }

    public Optional<Job> findJobById(String id) {
        return jobs.stream().filter(j -> j.getId().equals(id)).findFirst();
    }

    /** Returns the 20 most-recent executions, newest first. */
    public List<JobExecution> getRecentExecutions() {
        return executions.stream()
                .sorted(Comparator.comparing(JobExecution::getStartTime).reversed())
                .limit(20)
                .collect(Collectors.toList());
    }

    public Optional<JobExecution> getExecution(String id) {
        return executions.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    /** True if any execution is currently in RUNNING or PENDING state. */
    public boolean hasRunningJobs() {
        return executions.stream().anyMatch(JobExecution::isRunning);
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    /**
     * Triggers a job asynchronously.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Find the job by ID (throws if not found).</li>
     *   <li>Create a {@link JobExecution} with status RUNNING and add it to history.</li>
     *   <li>Delegate to {@link ShellExecutor} — this blocks the background thread
     *       until all commands complete.</li>
     *   <li>Set final status to SUCCESS or FAILED and record the end time.</li>
     * </ol>
     *
     * <p>Because the method is {@code @Async}, the caller (the HTTP request thread)
     * returns immediately with the execution ID while commands run in the background.</p>
     *
     * @param jobId the {@link Job#getId()} to run
     * @return a future that completes with the finished {@link JobExecution}
     */
    @Async
    public CompletableFuture<JobExecution> triggerJob(String jobId) {
        Job job = findJobById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        String       execId  = UUID.randomUUID().toString();
        Path         logFile = Paths.get(logsDir, execId + ".log");

        // Build the execution record and register it immediately so the
        // dashboard can show RUNNING status before the job finishes.
        JobExecution execution = new JobExecution();
        execution.setId(execId);
        execution.setJobId(job.getId());
        execution.setJobName(job.getName());
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartTime(LocalDateTime.now());
        execution.setLogFile(logFile.toString());
        executions.add(execution);

        log.info("▶ Job '{}' started (execution: {})", job.getName(), execId);

        // Build initial log header
        StringBuilder logBuffer = new StringBuilder();
        logBuffer.append("=".repeat(60)).append("\n");
        logBuffer.append("  LightCI — Job: ").append(job.getName()).append("\n");
        logBuffer.append("  Started: ").append(execution.getStartTime()).append("\n");
        logBuffer.append("=".repeat(60)).append("\n\n");

        // Run the commands (blocking in this background thread)
        boolean success = shellExecutor.execute(job.getCommands(), logBuffer, logFile);

        // Update execution with final result
        execution.setEndTime(LocalDateTime.now());
        execution.setStatus(success ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
        execution.setLogs(logBuffer.toString());

        log.info("■ Job '{}' finished → {} ({})",
                job.getName(), execution.getStatus(), execution.getDuration());

        return CompletableFuture.completedFuture(execution);
    }

    // ── Inner YAML wrapper ────────────────────────────────────────────────────

    /**
     * Jackson maps the top-level {@code jobs:} key in jobs.yml to this wrapper.
     */
    public static class JobsWrapper {
        private List<Job> jobs;
        public List<Job> getJobs()           { return jobs; }
        public void setJobs(List<Job> jobs)  { this.jobs = jobs; }
    }
}
