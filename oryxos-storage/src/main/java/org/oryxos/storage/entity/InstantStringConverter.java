package org.oryxos.storage.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

@Converter(autoApply = true)
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public Instant convertToEntityAttribute(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
