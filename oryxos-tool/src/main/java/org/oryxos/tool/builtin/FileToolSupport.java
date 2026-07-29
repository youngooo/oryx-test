package org.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

final class FileToolSupport {

    private FileToolSupport() {
    }

    static Path resolve(Path workspace, String value) {
        var supplied = Path.of(value);
        return (supplied.isAbsolute() ? supplied : workspace.resolve(supplied))
                .toAbsolutePath().normalize();
    }

    static ObjectNode schema(ObjectMapper mapper) {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }
}
