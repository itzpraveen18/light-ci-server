package com.example.lightci.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Thymeleaf helper bean that converts raw log text into colour-coded HTML.
 *
 * Referenced in execution.html as {@code @logFormatter.format(logs)}.
 *
 * Line colouring rules:
 * <ul>
 *   <li>Lines starting with {@code $} → command echo (blue)</li>
 *   <li>Lines containing {@code BUILD SUCCESS} → green</li>
 *   <li>Lines containing {@code BUILD FAILED} or {@code FAILED} → red</li>
 *   <li>Lines starting with {@code =} → separator (dimmed)</li>
 * </ul>
 */
@Component("logFormatter")
public class LogFormatter {

    public String format(String rawLogs) {
        if (rawLogs == null) return "";

        StringBuilder html = new StringBuilder();
        for (String line : rawLogs.split("\n", -1)) {
            String escaped = HtmlUtils.htmlEscape(line);
            String cssClass = classFor(line);
            if (cssClass.isEmpty()) {
                html.append(escaped);
            } else {
                html.append("<span class=\"").append(cssClass).append("\">")
                    .append(escaped)
                    .append("</span>");
            }
            html.append("\n");
        }
        return html.toString();
    }

    private String classFor(String line) {
        if (line.startsWith("$"))            return "cmd-line";
        if (line.contains("BUILD SUCCESS"))  return "ok-line";
        if (line.contains("BUILD FAILED")
         || line.contains("✗ Command failed")) return "fail-line";
        if (line.startsWith("="))            return "sep-line";
        return "";
    }
}
