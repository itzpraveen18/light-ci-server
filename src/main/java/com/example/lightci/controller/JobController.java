package com.example.lightci.controller;

import com.example.lightci.model.JobExecution;
import com.example.lightci.service.JobService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web MVC controller — maps HTTP requests to Thymeleaf templates.
 *
 * <pre>
 *   GET  /                        → dashboard.html  (job list + history)
 *   POST /jobs/{jobId}/run        → triggers job, redirects to /
 *   GET  /executions/{id}         → execution.html  (log viewer)
 * </pre>
 *
 * The POST → redirect → GET pattern (PRG) is used for job triggers so that
 * refreshing the browser after running a job does not re-submit the form.
 */
@Controller
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    /**
     * Renders the main dashboard with all jobs and recent execution history.
     *
     * @param model Thymeleaf model — populated with:
     *              <ul>
     *                <li>{@code jobs}       – list of Job definitions</li>
     *                <li>{@code executions} – 20 most-recent JobExecutions</li>
     *                <li>{@code hasRunning} – boolean for JS auto-refresh</li>
     *              </ul>
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("jobs",       jobService.getJobs());
        model.addAttribute("executions", jobService.getRecentExecutions());
        model.addAttribute("hasRunning", jobService.hasRunningJobs());
        return "dashboard";
    }

    // ── Job trigger ───────────────────────────────────────────────────────────

    /**
     * Triggers a job asynchronously and immediately redirects back to the dashboard.
     * The job runs in a background thread; the dashboard auto-refreshes to show progress.
     *
     * @param jobId              the job ID from jobs.yml
     * @param redirectAttributes flash-scope message shown on the dashboard after redirect
     */
    @PostMapping("/jobs/{jobId}/run")
    public String runJob(@PathVariable String jobId,
                         RedirectAttributes redirectAttributes) {
        try {
            jobService.triggerJob(jobId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Job triggered successfully! Refresh to see live status.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to trigger job: " + e.getMessage());
        }
        return "redirect:/";
    }

    // ── Execution detail ──────────────────────────────────────────────────────

    /**
     * Renders the log viewer for a specific execution.
     *
     * @param id    UUID of the execution
     * @param model populated with the {@link JobExecution} object
     */
    @GetMapping("/executions/{id}")
    public String executionDetail(@PathVariable String id, Model model) {
        JobExecution execution = jobService.getExecution(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Execution not found: " + id));
        model.addAttribute("execution", execution);
        return "execution";
    }
}
