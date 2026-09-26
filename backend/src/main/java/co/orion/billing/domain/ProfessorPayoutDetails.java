package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Los datos con los que se le paga a un profe: su llave Bre-B, su documento y el titular. Una fila
 * por profe; solo el admin los ve completos, y solo al pagar una liquidación.
 */
@Entity
@Table(name = "professor_payout_details")
public class ProfessorPayoutDetails {

    @Id
    @Column(name = "professor_id", updatable = false)
    private UUID professorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20)
    private BreBKeyType keyType;

    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private IdDocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 20)
    private String documentNumber;

    @Column(name = "holder_name", nullable = false, length = 150)
    private String holderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfessorPayoutDetails() {
        // exigido por JPA
    }

    public ProfessorPayoutDetails(UUID professorId, PayoutDestination destino, Instant ahora) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.createdAt = ahora;
        cambiar(destino, ahora);
    }

    public void cambiar(PayoutDestination destino, Instant ahora) {
        this.keyType = destino.keyType();
        this.keyValue = destino.keyValue();
        this.documentType = destino.documentType();
        this.documentNumber = destino.documentNumber();
        this.holderName = destino.holderName();
        this.updatedAt = ahora;
    }

    public PayoutDestination destino() {
        return new PayoutDestination(keyType, keyValue, documentType, documentNumber, holderName);
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
