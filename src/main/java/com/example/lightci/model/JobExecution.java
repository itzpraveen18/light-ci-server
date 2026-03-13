package com.example.lightci.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single run of a {@link Job}.
 *
 * Created when a job is triggered, updated in-place as the job runs,
 * and stored in memory by {@code JobService} for the history view.
 */
public class JobExecution {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss");

    /** UUID assigned at trigger time — used in the /executions/{id} URL */
    private String id;

    private String jobId;
    private String jobName;

    private ExecutionStatus status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** Full stdout/stderr log captured during execution */
    private String logs;

    /** Path to the on-disk .log file for this execution */
    private String logFile;

    // ── Computed display helpers ──────────────────────────────────────────────

    /** Human-readable elapsed time, e.g. "3s" or "1m 12s". */
    public String getDuration() {
        if (startTime == null) return "-";
        LocalDateTime end = (endTime != null) ? endTime : LocalDateTime.now();
        long seconds = Duration.between(startTime, end).getSeconds();
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    /** Formatted start time for display in the table. */
    public String getFormattedStartTime() {
        return (startTime != null) ? startTime.format(DISPLAY_FMT) : "-";
    }

    /** True when the job is still executing (used for JS auto-refresh). */
    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING || status == ExecutionStatus.PENDING;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }

    public String getJobId()                       { return jobId; }
    public void   setJobId(String jobId)           { this.jobId = jobId; }

    public String getJobName()                     { return jobName; }
    public void   setJobName(String jobName)       { this.jobName = jobName; }

    public ExecutionStatus getStatus()             { return status; }
    public void   setStatus(ExecutionStatus s)     { this.status = s; }

    public LocalDateTime getStartTime()            { return startTime; }
    public void   setStartTime(LocalDateTime t)    { this.startTime = t; }

    public LocalDateTime getEndTime()              { return endTime; }
    public void   setEndTime(LocalDateTime t)      { this.endTime = t; }

    public String getLogs()                        { return logs; }
    public void   setLogs(String logs)             { this.logs = logs; }

    public String getLogFile()                     { return logFile; }
    public void   setLogFile(String logFile)       { this.logFile = logFile; }
}
