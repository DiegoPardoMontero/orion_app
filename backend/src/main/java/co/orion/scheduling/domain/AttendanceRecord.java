package co.orion.scheduling.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Asistencia a una clase ya terminada. El UNIQUE sobre booking_id es la garantía estructural de
 * que no se registra dos veces la misma clase: el 409 del servicio es el mensaje amable, esto
 * es el árbitro — el mismo patrón que la doble reserva.
 */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false, unique = true)
    private UUID bookingId;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected AttendanceRecord() {
        // exigido por JPA
    }

    public AttendanceRecord(UUID bookingId, boolean present, String notes, Instant recordedAt) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.present = present;
        this.notes = notes;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public boolean isPresent() {
        return present;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
