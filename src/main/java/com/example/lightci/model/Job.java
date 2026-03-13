package com.example.lightci.model;

import java.util.List;

/**
 * Represents a CI job definition loaded from jobs.yml.
 *
 * A Job is a named collection of shell commands that are executed
 * sequentially when triggered from the dashboard.
 *
 * Example jobs.yml entry:
 * <pre>
 *   - id: build-demo
 *     name: Demo Build
 *     description: Runs a sample build
 *     commands:
 *       - echo "Building..."
 *       - mvn clean package
 * </pre>
 */
public class Job {

    /** Unique identifier used in URLs, e.g. /jobs/build-demo/run */
    private String id;

    /** Human-readable name shown on the dashboard */
    private String name;

    /** Short description displayed under the job name */
    private String description;

    /** Ordered list of shell commands to execute */
    private List<String> commands;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Job() {}

    public Job(String id, String name, String description, List<String> commands) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.commands    = commands;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                    { return id; }
    public void   setId(String id)           { this.id = id; }

    public String getName()                  { return name; }
    public void   setName(String name)       { this.name = name; }

    public String getDescription()           { return description; }
    public void   setDescription(String d)   { this.description = d; }

    public List<String> getCommands()        { return commands; }
    public void setCommands(List<String> c)  { this.commands = c; }
}
