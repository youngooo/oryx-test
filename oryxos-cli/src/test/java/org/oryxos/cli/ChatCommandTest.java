package org.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.io.Writer;
import org.junit.jupiter.api.Test;
import org.oryxos.channel.cli.CliChannel;

class ChatCommandTest {

    @Test
    void parsesChatOptionsAndDelegatesToCliChannel() {
        var channel = mock(CliChannel.class);
        when(channel.chat(eq("weather"), eq("operator"),
                any(Reader.class), any(Writer.class))).thenReturn(0);

        var exitCode = OryxOsCommand.commandLine(channel).execute(
                "chat", "--profile", "weather",
                "--user", "operator", "--workspace", ".");

        assertThat(exitCode).isZero();
        verify(channel).chat(eq("weather"), eq("operator"),
                any(Reader.class), any(Writer.class));
    }
}
