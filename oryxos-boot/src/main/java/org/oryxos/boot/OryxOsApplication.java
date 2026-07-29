package org.oryxos.boot;

import org.oryxos.cli.OryxOsCommand;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = "org.oryxos",
        excludeName = {
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
                "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
                "org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration"
        })
@EntityScan(basePackages = {
        "org.oryxos.storage.entity", "org.oryxos.memory.sqlite"})
@EnableJpaRepositories(basePackages = {
        "org.oryxos.storage.repository", "org.oryxos.memory.sqlite"})
public class OryxOsApplication {

    public static void main(String[] args) {
        var exitCode = OryxOsCommand.commandLine(
                new SpringRuntimeLauncher()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
