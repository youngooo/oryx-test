package org.oryxos.storage.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.oryxos.core.model.Message;
import org.springframework.stereotype.Component;

@Component
public final class SessionJsonConverter {

    private final ObjectMapper objectMapper;

    public SessionJsonConverter() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public String write(List<Message> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Session messages", exception);
        }
    }

    public List<Message> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize Session messages", exception);
        }
    }
}
