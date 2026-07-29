package org.oryxos.channel.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import org.oryxos.core.service.AgentService;

/**
 * Interactive channel adapter. Command parsing remains owned by oryxos-cli.
 */
public final class CliChannel {

    private final AgentService agentService;

    public CliChannel(AgentService agentService) {
        this.agentService = Objects.requireNonNull(agentService);
    }

    public int chat(String profileName, String userId, Reader input, Writer output) {
        var reader = input instanceof BufferedReader buffered
                ? buffered : new BufferedReader(input);
        var writer = output instanceof PrintWriter printWriter
                ? printWriter : new PrintWriter(output, true);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.equalsIgnoreCase("/exit")
                        || line.equalsIgnoreCase("/quit")) {
                    return 0;
                }
                var result = agentService.invoke(
                        profileName, "cli", userId, line);
                writer.println(result.response());
                writer.flush();
                if (result.terminationReason()
                        != org.oryxos.core.react.ReActLoop.TerminationReason.FINAL_RESPONSE) {
                    return 5;
                }
            }
            return 0;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            writer.println("ERROR: " + failure.getMessage());
            writer.flush();
            return 5;
        } catch (IOException failure) {
            writer.println("ERROR: Unable to read CLI input");
            writer.flush();
            return 5;
        }
    }
}
