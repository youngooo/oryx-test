package org.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.oryxos.core.port.Sandbox;

/**
 * Application policy boundary; this is deliberately not described as OS isolation.
 */
public final class WhitelistSandbox implements Sandbox {

    private final Set<String> allowedHosts;
    private final Set<String> allowedCommands;
    private final Set<Path> workspaceRoots;

    public WhitelistSandbox(Set<String> allowedHosts, Set<String> allowedCommands,
            Set<Path> workspaceRoots) {
        this.allowedHosts = normalizeHosts(allowedHosts);
        this.allowedCommands = (allowedCommands == null ? Set.<String>of() : allowedCommands)
                .stream().map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.workspaceRoots = (workspaceRoots == null ? Set.<Path>of() : workspaceRoots)
                .stream().map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void enforce(Action action) throws DeniedException {
        if (action == null || action.type() == null) {
            throw new DeniedException("Missing Sandbox action");
        }
        switch (action.type().toUpperCase(Locale.ROOT)) {
            case "HTTP" -> enforceHttp(action.target());
            case "FILE" -> enforceFile(action.target());
            case "SHELL" -> enforceShell(action.target());
            case "TOOL" -> {
                // Tools without an external resource still pass this explicit boundary.
            }
            default -> throw new DeniedException("Unsupported Sandbox action");
        }
    }

    private void enforceHttp(String target) {
        final URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException invalid) {
            throw new DeniedException("Invalid HTTP URL");
        }
        var scheme = uri.getScheme();
        var host = uri.getHost();
        if (scheme == null || host == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new DeniedException("Only HTTP(S) URLs are allowed");
        }
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new DeniedException("HTTP host is not allowlisted: " + host);
        }
    }

    private void enforceFile(String target) {
        final Path supplied;
        try {
            supplied = Path.of(target);
        } catch (RuntimeException invalid) {
            throw new DeniedException("Invalid file path");
        }
        var accepted = workspaceRoots.stream().anyMatch(root -> {
            var candidate = supplied.isAbsolute()
                    ? supplied.toAbsolutePath().normalize()
                    : root.resolve(supplied).normalize();
            if (!candidate.startsWith(root)) {
                return false;
            }
            try {
                var realRoot = root.toRealPath();
                var existing = candidate;
                while (existing != null && !Files.exists(existing)) {
                    existing = existing.getParent();
                }
                return existing != null
                        && existing.toRealPath().startsWith(realRoot);
            } catch (IOException failure) {
                return false;
            }
        });
        if (!accepted) {
            throw new DeniedException("File path is outside the workspace");
        }
    }

    private void enforceShell(String target) {
        var command = target == null ? "" : target.strip();
        if (command.isEmpty() || command.contains(";") || command.contains("|")
                || command.contains("&")) {
            throw new DeniedException("Shell action must name one executable");
        }
        var executable = Path.of(command).getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if (!allowedCommands.contains(executable)) {
            throw new DeniedException("Shell executable is not allowlisted");
        }
    }

    private Set<String> normalizeHosts(Set<String> hosts) {
        return (hosts == null ? Set.<String>of() : hosts).stream()
                .map(value -> value.toLowerCase(Locale.ROOT).strip())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
