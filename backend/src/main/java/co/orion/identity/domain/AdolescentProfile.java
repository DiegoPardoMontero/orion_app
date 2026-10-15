package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un adolescente (13 a 17 años) bajo la cuenta de su representante legal (Bloque 12, V83).
 *
 * <p>No es un usuario: no tiene correo, contraseña ni teléfono. Es lo mínimo para dar la clase
 * —nombre de pila, año de nacimiento, nivel y objetivo— y nunca es público. Quien reserva, paga,
 * habla con el profe y reseña es el adulto; el perfil solo dice para quién es la clase.
 *
 * <p>El año y no la fecha de nacimiento, a propósito: basta para saber si tiene entre 13 y 17 y para
 * avisar cuando cumpla 18, y es un dato menos del menor que guardar (Ley 1581, art. 7).
 */
@Entity
@Table(name = "adolescent_profiles")
@EntityListeners(AuditingEntityListener.class)
public class AdolescentProfile {

    public static final int EDAD_MINIMA = 13;
    public static final int EDAD_MAXIMA = 17;

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "guardian_user_id", nullable = false, updatable = false)
    private UUID guardianUserId;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "birth_year", nullable = false, updatable = false)
    private short birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_declared_level", length = 20)
    private ProficiencyLevel selfDeclaredLevel;

    @Column(name = "motivation", length = 500)
    private String motivation;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected AdolescentProfile() {
    }

    /** @param anoActual el año de Bogotá hoy: la edad se razona por años, no por fechas */
    public AdolescentProfile(UUID guardianUserId, String firstName, int birthYear, int anoActual,
                             ProficiencyLevel selfDeclaredLevel, String motivation) {
        this.guardianUserId = Objects.requireNonNull(guardianUserId, "guardianUserId");
        int edad = anoActual - birthYear;
        if (edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            throw new UnprocessableException(
                    "Orión recibe adolescentes de " + EDAD_MINIMA + " a " + EDAD_MAXIMA + " años. "
                            + "Con ese año de nacimiento tendría " + edad + ".");
        }
        this.birthYear = (short) birthYear;
        rename(firstName);
        describe(selfDeclaredLevel, motivation);
    }

    public void rename(String firstName) {
        String limpio = firstName == null ? "" : firstName.strip();
        if (limpio.isEmpty() || limpio.length() > 60) {
            throw new UnprocessableException("Dinos el nombre de pila del adolescente (hasta 60 letras).");
        }
        this.firstName = limpio;
    }

    public void describe(ProficiencyLevel selfDeclaredLevel, String motivation) {
        this.selfDeclaredLevel = selfDeclaredLevel;
        String texto = motivation == null ? null : motivation.strip();
        this.motivation = texto == null || texto.isEmpty() ? null : texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    /** La edad por años: en el año en que cumple 18 deja de ser adolescente para Orión. */
    public int ageIn(int anoActual) {
        return anoActual - birthYear;
    }

    public boolean isAdolescentIn(int anoActual) {
        int edad = ageIn(anoActual);
        return edad >= EDAD_MINIMA && edad <= EDAD_MAXIMA;
    }

    public void close(Instant when) {
        if (closedAt == null) {
            closedAt = Objects.requireNonNull(when, "when");
        }
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuardianUserId() {
        return guardianUserId;
    }

    public String getFirstName() {
        return firstName;
    }

    public int getBirthYear() {
        return birthYear;
    }

    public ProficiencyLevel getSelfDeclaredLevel() {
        return selfDeclaredLevel;
    }

    public String getMotivation() {
        return motivation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
