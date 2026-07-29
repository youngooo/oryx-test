package org.oryxos.cli.runtime;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Boot-owned runtime bridge. The CLI module never depends back on Boot.
 */
public interface RuntimeLauncher {
    int chat(Path workspace, String profileName, String userId,
            Reader input, Writer output);

    int serve(Path workspace);

    int gateway(Path workspace);

    default List<Map<String, Object>> providers(Path workspace) {
        return List.of();
    }

    default List<Map<String, Object>> tools(Path workspace) {
        return List.of();
    }

    default List<Map<String, Object>> sessions(Path workspace) {
        return List.of();
    }
}
