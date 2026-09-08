package co.orion.legal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una versión de un documento legal: los Términos y condiciones o la Política de tratamiento.
 *
 * <p>El texto vive aquí y no en el frontend porque lo que importa es poder probar <em>qué</em>
 * aceptó cada quien. Cuando alguien pregunte por lo que firmó en marzo, la respuesta tiene que ser
 * el texto de marzo — y un texto desplegado no recuerda sus versiones anteriores.
 *
 * <p>Las versiones no se editan: publicar un cambio es crear una fila nueva con
 * {@code effective_from} posterior. La anterior se queda para siempre, porque sigue siendo lo que
 * aceptaron quienes la aceptaron.
 */
@Entity
@Table(name = "legal_documents")
public class LegalDocument {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "version", nullable = false, length = 20, updatable = false)
    private String version;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected LegalDocument() {
        // exigido por JPA
    }

    public LegalDocument(String code, String version, String title, String body,
                         LocalDate effectiveFrom) {
        this.code = code;
        this.version = version;
        this.title = title;
        this.body = body;
        this.effectiveFrom = effectiveFrom;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    /**
     * El texto se puede corregir dentro de la misma versión mientras nadie la haya aceptado — una
     * errata de redacción no es un cambio de contrato. Cambiar el fondo exige versión nueva, y eso
     * lo decide una persona, no este método.
     */
    public void rewrite(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
