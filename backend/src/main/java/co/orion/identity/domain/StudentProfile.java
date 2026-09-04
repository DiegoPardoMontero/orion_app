package co.orion.identity.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Lo que el estudiante declara de sí mismo: su nivel, para qué aprende, en qué idioma, y cómo
 * quiere que se vea su avatar.
 *
 * <p>Todo aquí es <strong>declarado</strong>, no calculado. Es la diferencia con el panel de
 * progreso, donde cada cifra sale de reservas reales. El nivel lo pone y lo cambia el estudiante:
 * un nivel que la plataforma asigna y él no puede mover convierte una herramienta de motivación en
 * un veredicto.
 *
 * <p>La ficha existe siempre —se crea con el registro y la V21 la creó para los estudiantes que ya
 * estaban—, así que ningún código tiene que manejar el caso "estudiante sin ficha".
 */
@Entity
@Table(name = "student_profiles")
@EntityListeners(AuditingEntityListener.class)
public class StudentProfile {

    /** Edad mínima para tener perfil público. Ver {@link #enablePublicProfile}. */
    public static final int EDAD_MINIMA_PERFIL_PUBLICO = 18;

    public static final int MAX_MOTIVACION = 280;

    /** Los cosméticos con los que nace toda ficha: los tres iniciales del catálogo. */
    private static final String FRAME_INICIAL = "trazo";
    private static final String PALETTE_INICIAL = "trazo";
    private static final String SKY_INICIAL = "crema";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_declared_level", length = 20)
    private ProficiencyLevel selfDeclaredLevel;

    @Column(name = "primary_language", length = 5)
    private String primaryLanguage;

    @Column(name = "motivation", length = MAX_MOTIVACION)
    private String motivation;

    @Column(name = "is_public", nullable = false)
    private boolean publicProfile;

    /**
     * Solo se pide cuando alguien intenta activar el perfil público. Pedirla en el registro sería
     * cobrarle el dato a todo el mundo por una función que casi nadie usará.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "frame_code", nullable = false, length = 40)
    private String frameCode = FRAME_INICIAL;

    @Column(name = "palette_code", nullable = false, length = 40)
    private String paletteCode = PALETTE_INICIAL;

    @Column(name = "sky_code", nullable = false, length = 40)
    private String skyCode = SKY_INICIAL;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentProfile() {
        // exigido por JPA
    }

    public StudentProfile(User user) {
        // El id NO se asigna aquí: @MapsId lo copia desde la relación al persistir. Ponerlo a
        // mano hace que Hibernate vea un identificador que él no derivó y aborte con
        // "null identifier".
        this.user = Objects.requireNonNull(user, "user");
    }

    /** Lo que el estudiante cuenta de sí mismo. Todo opcional: una ficha vacía es válida. */
    public void describe(ProficiencyLevel level, String primaryLanguage, String motivation) {
        if (motivation != null && motivation.length() > MAX_MOTIVACION) {
            throw new UnprocessableException(
                    "Tu motivación no puede pasar de " + MAX_MOTIVACION + " caracteres.");
        }
        this.selfDeclaredLevel = level;
        this.primaryLanguage = primaryLanguage;
        this.motivation = motivation == null || motivation.isBlank() ? null : motivation.trim();
    }

    /**
     * Activa el perfil público. Exige la fecha de nacimiento y que sea mayor de edad.
     *
     * <p>El público de Orión es 16+, y un perfil público con foto, nombre y metas de un menor exige
     * tratamiento reforzado bajo la Ley 1581 de 2012. La solución más simple y más defendible es no
     * ofrecerlo. Esta comprobación es la que manda: el switch deshabilitado en el frontend es
     * cortesía, no seguridad.
     */
    public void enablePublicProfile(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            throw new UnprocessableException(
                    "Necesitamos tu fecha de nacimiento para activar tu perfil público.");
        }
        if (birthDate.isAfter(today)) {
            throw new UnprocessableException("Esa fecha de nacimiento está en el futuro.");
        }
        if (Period.between(birthDate, today).getYears() < EDAD_MINIMA_PERFIL_PUBLICO) {
            throw new UnprocessableException(
                    "El perfil público está disponible desde los " + EDAD_MINIMA_PERFIL_PUBLICO
                            + " años. Todo lo demás de Orión sigue igual para ti.");
        }
        this.birthDate = birthDate;
        this.publicProfile = true;
    }

    /** Desactivarlo no pide nada: retirar el consentimiento tiene que ser más fácil que darlo. */
    public void disablePublicProfile() {
        this.publicProfile = false;
    }

    /** Los cosméticos equipados. Que estén desbloqueados lo comprueba el servicio, no la entidad. */
    public void equip(String frameCode, String paletteCode, String skyCode) {
        this.frameCode = Objects.requireNonNull(frameCode, "frameCode");
        this.paletteCode = Objects.requireNonNull(paletteCode, "paletteCode");
        this.skyCode = Objects.requireNonNull(skyCode, "skyCode");
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public ProficiencyLevel getSelfDeclaredLevel() {
        return selfDeclaredLevel;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getMotivation() {
        return motivation;
    }

    public boolean isPublicProfile() {
        return publicProfile;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getFrameCode() {
        return frameCode;
    }

    public String getPaletteCode() {
        return paletteCode;
    }

    public String getSkyCode() {
        return skyCode;
    }
}
