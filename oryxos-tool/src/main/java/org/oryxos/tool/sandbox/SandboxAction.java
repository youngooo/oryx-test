package org.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.Path;
import org.oryxos.core.port.Sandbox;

public record SandboxAction(Type type, String target) {

    public enum Type { HTTP, FILE, SHELL, TOOL }

    public SandboxAction {
        if (type == null || target == null || target.isBlank()) {
            throw new IllegalArgumentException("Sandbox action requires type and target");
        }
    }

    public static SandboxAction http(URI uri) {
        return new SandboxAction(Type.HTTP, uri.toString());
    }

    public static SandboxAction file(Path path) {
        return new SandboxAction(Type.FILE, path.toString());
    }

    public Sandbox.Action toCoreAction() {
        return new Sandbox.Action(type.name(), target);
    }
}
