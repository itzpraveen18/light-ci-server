package com.example.lightci.model;

/**
 * Lifecycle states of a single job execution.
 *
 * PENDING  → job is queued but not yet started
 * RUNNING  → shell commands are currently executing
 * SUCCESS  → all commands exited with code 0
 * FAILED   → at least one command exited with a non-zero code
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED;

    /** Bootstrap CSS colour class used in the dashboard badge. */
    public String badgeClass() {
        return switch (this) {
            case PENDING -> "bg-secondary";
            case RUNNING -> "bg-warning text-dark";
            case SUCCESS -> "bg-success";
            case FAILED  -> "bg-danger";
        };
    }

    /** Bootstrap icon used next to the badge. */
    public String icon() {
        return switch (this) {
            case PENDING -> "bi-hourglass";
            case RUNNING -> "bi-arrow-repeat";
            case SUCCESS -> "bi-check-circle-fill";
            case FAILED  -> "bi-x-circle-fill";
        };
    }
}
