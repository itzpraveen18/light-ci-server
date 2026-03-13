package com.example.lightci.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executes shell commands as OS sub-processes and streams their output
 * both to an in-memory {@link StringBuilder} (for the web UI) and to a
 * log file on disk (for persistence).
 *
 * <p>Commands are executed via {@code /bin/sh -c <command>} so that shell
 * built-ins, pipes, and redirects work as expected on macOS / Linux.</p>
 *
 * <p>stdout and stderr are merged into a single stream so the log reads
 * in the same order as it would appear in a real terminal.</p>
 */
@Component
public class ShellExecutor {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutor.class);

    /**
     * Executes the given commands sequentially and stops on the first failure.
     *
     * @param commands  ordered list of shell commands to run
     * @param logBuffer in-memory buffer that receives all output lines
     * @param logFile   path to the file where output is also written
     * @return {@code true} if every command exited with code 0, {@code false} otherwise
     */
    public boolean execute(List<String> commands, StringBuilder logBuffer, Path logFile) {
        try (BufferedWriter writer = Files.newBufferedWriter(logFile)) {

            for (String command : commands) {
                // Echo the command itself (like a real shell)
                writeLine(logBuffer, writer, "$ " + command);

                boolean success = runSingleCommand(command, logBuffer, writer);

                if (!success) {
                    writeLine(logBuffer, writer, "");
                    writeLine(logBuffer, writer, "✗ Command failed: " + command);
                    writeLine(logBuffer, writer, "");
                    writeLine(logBuffer, writer, "BUILD FAILED");
                    return false;
                }
            }

            writeLine(logBuffer, writer, "");
            writeLine(logBuffer, writer, "BUILD SUCCESS");
            return true;

        } catch (IOException e) {
            log.error("Failed to write log file: {}", logFile, e);
            logBuffer.append("ERROR: Could not write log file: ").append(e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Forks a sub-process for a single command and streams its output line by line.
     *
     * @return {@code true} if the process exited with code 0
     */
    private boolean runSingleCommand(String command,
                                     StringBuilder logBuffer,
                                     BufferedWriter writer) throws IOException {

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.redirectErrorStream(true); // merge stderr → stdout

        Process process = pb.start();

        // Stream output as it arrives so large jobs don't block
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writeLine(logBuffer, writer, line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Command exited with code {}: {}", exitCode, command);
            }
            return exitCode == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job execution interrupted for command: {}", command);
            return false;
        }
    }

    /** Writes a line to both the in-memory buffer and the log file. */
    private void writeLine(StringBuilder buffer, BufferedWriter writer, String line)
            throws IOException {
        buffer.append(line).append("\n");
        writer.write(line);
        writer.newLine();
        writer.flush(); // flush after each line so the file is readable while running
    }
}
