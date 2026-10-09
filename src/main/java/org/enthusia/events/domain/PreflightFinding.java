package org.enthusia.events.domain;

public record PreflightFinding(Severity severity, String subject, String problem, String repair) {
    public enum Severity { BLOCKER, WARNING, UNKNOWN }
    public String text() { return severity + " " + subject + ": " + problem + "; " + repair; }
}
