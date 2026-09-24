package co.orion.practice.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un ejercicio de un set. Guarda la última respuesta y cuántos intentos lleva. */
@Entity
@Table(name = "practice_items")
public class PracticeItem {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private UUID id;

    @Column(name = "practice_set_id", nullable = false, updatable = false)
    private UUID practiceSetId;

    @Column(name = "item_index", nullable = false, updatable = false)
    private short itemIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false, length = 30)
    private PracticeItemType itemType;

    @Column(nullable = false, updatable = false, length = 600)
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(length = 600, updatable = false)
    private String expected;

    @Column(nullable = false, updatable = false, length = 400)
    private String explanation;

    @Column(name = "source_term", length = 120, updatable = false)
    private String sourceTerm;

    /** La pista del «Casi…»: ayuda a acertar sin dar la respuesta. Nula en los sets de antes. */
    @Column(length = 400, updatable = false)
    private String hint;

    @Column(length = 600)
    private String answer;

    /** La del primer intento: con dos, la última la tapa, y es la que dice qué no sabía todavía. */
    @Column(name = "first_answer", length = 600)
    private String firstAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    protected PracticeItem() {
    }

    public PracticeItem(UUID practiceSetId, int itemIndex, PracticeItemType itemType, String prompt,
                        String payload, String expected, String explanation, String sourceTerm) {
        this(practiceSetId, itemIndex, itemType, prompt, payload, expected, explanation, sourceTerm, null);
    }

    public PracticeItem(UUID practiceSetId, int itemIndex, PracticeItemType itemType, String prompt,
                        String payload, String expected, String explanation, String sourceTerm, String hint) {
        this.practiceSetId = practiceSetId;
        this.itemIndex = (short) itemIndex;
        this.itemType = itemType;
        this.prompt = prompt;
        this.payload = payload;
        this.expected = expected;
        this.explanation = explanation;
        this.sourceTerm = sourceTerm;
        this.hint = hint;
    }

    /**
     * Registra un intento. Pasado el máximo, 422: ya se mostró la respuesta con su explicación y
     * se sigue (brief, paso B3). Un ítem ya acertado tampoco se vuelve a responder.
     */
    public void responder(String respuesta, boolean acerto, int maxIntentos, Instant ahora) {
        if (Boolean.TRUE.equals(correct)) {
            throw new UnprocessableException("Este ya lo resolviste. Sigue con el próximo.");
        }
        if (skippedAt != null) {
            throw new UnprocessableException("Este lo saltaste. Sigue con el próximo.");
        }
        if (attempts >= maxIntentos) {
            throw new UnprocessableException("Ya usaste los intentos de este ejercicio. Sigue con el próximo.");
        }
        if (attempts == 0) {
            this.firstAnswer = respuesta;
        }
        this.attempts++;
        this.answer = respuesta;
        this.correct = acerto;
        this.answeredAt = ahora;
    }

    /**
     * Parejas, par por par (diseño del 24/09/2026): cada par se comprueba al unirlo. Uno que va se
     * queda unido sin gastar intento; uno que no va gasta un intento. Con todos unidos, acertado —al
     * primer intento si no falló ninguno—; sin intentos, se muestra lo que faltaba.
     *
     * @param unidos los pares ya unidos, en JSON, con el nuevo si fue
     * @param fallo  el par que no fue, en JSON, para que el profesor vea qué intentó
     */
    public void pareja(boolean va, boolean completa, String unidos, String fallo, int maxIntentos, Instant ahora) {
        if (itemType != PracticeItemType.MATCH_MEANING) {
            throw new UnprocessableException("Solo las parejas se unen de a una.");
        }
        if (cerrado(maxIntentos)) {
            throw new UnprocessableException("Este ejercicio ya está cerrado. Sigue con el próximo.");
        }
        this.answer = unidos;
        this.answeredAt = ahora;
        if (!va) {
            if (attempts == 0) {
                this.firstAnswer = fallo;
            }
            this.attempts++;
            this.correct = false;
        } else if (completa) {
            this.attempts++;
            this.correct = true;
        }
    }

    /** Terminado: acertado, sin intentos o saltado. Se muestra la respuesta y se sigue. */
    public boolean cerrado(int maxIntentos) {
        return Boolean.TRUE.equals(correct) || attempts >= maxIntentos || skippedAt != null;
    }

    /** Acertado a la primera: lo que cuenta para una constelación perfecta y para la racha del set. */
    public boolean alPrimerIntento() {
        return Boolean.TRUE.equals(correct) && attempts == 1;
    }

    /** Acertado al segundo intento: lo que celebra el logro «Segunda oportunidad». */
    public boolean alSegundoIntento() {
        return Boolean.TRUE.equals(correct) && attempts == 2;
    }

    /**
     * Una constelación perfecta: todos al primer intento. Uno saltado también la apaga (diseño,
     * 24/09/2026): no cuenta como error, pero tampoco como estrella a la primera.
     */
    public static boolean perfecta(List<PracticeItem> items) {
        return !items.isEmpty() && items.stream().allMatch(PracticeItem::alPrimerIntento);
    }

    /** Lo que costó: resuelto al segundo intento o mostrado. Es lo que va a «Para repasar» y «Le costó». */
    public boolean costo(int maxIntentos) {
        return alSegundoIntento() || (cerrado(maxIntentos) && Boolean.FALSE.equals(correct) && skippedAt == null);
    }

    /**
     * Saltar un ejercicio de escucha porque el dispositivo no tiene voz en inglés. No es un fallo:
     * {@code correct} queda como estaba, así que no aparece entre lo que costó ni resta en nada.
     * Solo los de escucha, y solo mientras siguen abiertos.
     */
    public void saltar(int maxIntentos, Instant ahora) {
        if (!itemType.seOye()) {
            throw new UnprocessableException("Solo se pueden saltar los ejercicios de escucha.");
        }
        if (cerrado(maxIntentos)) {
            throw new UnprocessableException("Este ejercicio ya está cerrado. Sigue con el próximo.");
        }
        this.skippedAt = ahora;
    }

    public UUID getId() { return id; }
    public UUID getPracticeSetId() { return practiceSetId; }
    public int getItemIndex() { return itemIndex; }
    public PracticeItemType getItemType() { return itemType; }
    public String getPrompt() { return prompt; }
    public String getPayload() { return payload; }
    public String getExpected() { return expected; }
    public String getExplanation() { return explanation; }
    public String getSourceTerm() { return sourceTerm; }
    public String getHint() { return hint; }
    public String getAnswer() { return answer; }
    public String getFirstAnswer() { return firstAnswer; }
    public Boolean getCorrect() { return correct; }
    public int getAttempts() { return attempts; }
    public Instant getAnsweredAt() { return answeredAt; }
    public Instant getSkippedAt() { return skippedAt; }
}
