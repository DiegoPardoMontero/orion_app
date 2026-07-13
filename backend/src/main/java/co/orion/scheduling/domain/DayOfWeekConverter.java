package co.orion.scheduling.domain;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * La columna weekday es SMALLINT con la convención ISO 1–7, que es exactamente
 * DayOfWeek.getValue(). Al traducir en un único lugar, el resto del código habla DayOfWeek
 * y nunca un short suelto que alguien pueda interpretar con otra base (0=domingo, etc.).
 */
@Converter(autoApply = false)
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek attribute) {
        return attribute == null ? null : (short) attribute.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : DayOfWeek.of(dbData);
    }
}
