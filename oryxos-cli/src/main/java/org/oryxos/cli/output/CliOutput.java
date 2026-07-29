package org.oryxos.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.util.regex.Pattern;

public final class CliOutput {
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"(?:api[_-]?key|token|secret|password|authorization)\""
                    + "\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern PLAIN_SECRET = Pattern.compile(
            "(?i)((?:api[_-]?key|token|secret|password|authorization)"
                    + "\\s*[:=]\\s*)[^\\s,;}]+");
    private static final Pattern PROVIDER_KEY = Pattern.compile(
            "(?i)sk-[a-z0-9_-]{8,}");
    private final PrintWriter out;
    private final PrintWriter err;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules();

    public CliOutput(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    public void value(Object value, boolean json) {
        if (!json) {
            out.println(redact(String.valueOf(value)));
            return;
        }
        try {
            out.println(redact(mapper.writeValueAsString(value)));
        } catch (Exception failure) {
            error("Unable to serialize CLI output");
        }
    }

    public void error(String message) {
        err.println("ERROR: " + redact(message));
    }

    public static String redact(String value) {
        if (value == null) {
            return "";
        }
        var jsonSafe = JSON_SECRET.matcher(value)
                .replaceAll("$1[REDACTED]$2");
        var plainSafe = PLAIN_SECRET.matcher(jsonSafe)
                .replaceAll("$1[REDACTED]");
        return PROVIDER_KEY.matcher(plainSafe).replaceAll("[REDACTED]");
    }
}
