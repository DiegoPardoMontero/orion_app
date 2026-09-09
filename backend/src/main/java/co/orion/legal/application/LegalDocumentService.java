package co.orion.legal.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.legal.domain.AgreementAcceptance;
import co.orion.legal.persistence.AgreementAcceptanceRepository;
import co.orion.legal.domain.LegalDocument;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.legal.persistence.LegalDocumentRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.shared.config.LegalIdentity;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * Sirve los documentos legales vigentes y guarda la constancia de quién aceptó qué.
 *
 * <p><strong>Por qué el texto se guarda con marcadores y se rellena al leerlo.</strong> El cuerpo
 * que vive en la base conserva {@code {{responsable}}}, {@code {{domicilio}}} y compañía, y esos
 * huecos se rellenan en cada lectura con la configuración vigente. Así, si cambia el domicilio de
 * notificaciones, el documento lo dice desde el día siguiente sin publicar una versión nueva — que
 * es lo que necesita quien va a notificar algo. Lo que sí queda congelado por versión son las
 * cláusulas, que es lo que de verdad se aceptó.
 */
@Service
public class LegalDocumentService {

    /** «8 de septiembre de 2026», que es como se lee una fecha de vigencia en español. */
    private static final DateTimeFormatter VIGENCIA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private final LegalDocumentRepository documents;
    private final AgreementAcceptanceRepository acceptances;
    private final LegalIdentity identity;
    private final Clock clock;

    public LegalDocumentService(LegalDocumentRepository documents,
                                AgreementAcceptanceRepository acceptances,
                                LegalIdentity identity,
                                Clock clock) {
        this.documents = documents;
        this.acceptances = acceptances;
        this.identity = identity;
        this.clock = clock;
    }

    /** El documento que rige hoy, con los datos del responsable ya puestos. */
    @Transactional(readOnly = true)
    public Rendered vigente(LegalDocumentCode code) {
        LegalDocument document = documents.findVigente(code.name(), hoy())
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        return render(document);
    }

    /**
     * Una versión concreta, para responder «¿qué acepté en marzo?». No se filtra por vigencia: lo
     * que se pide es precisamente una versión que ya no rige.
     */
    @Transactional(readOnly = true)
    public Rendered version(LegalDocumentCode code, String version) {
        LegalDocument document = documents.findByCodeAndVersion(code.name(), version)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        return render(document);
    }

    /**
     * Deja constancia de que alguien aceptó la versión vigente de un documento.
     *
     * <p>Es la prueba que exige el art. 9 de la Ley 1581 de 2012: sin ella, la autorización no se
     * puede demostrar y a efectos prácticos no existe. Guarda IP y user-agent porque una
     * aceptación sin circunstancias es una afirmación nuestra, no un hecho verificable.
     *
     * <p>Idempotente: aceptar dos veces la misma versión no crea dos filas. Lo respalda el índice
     * único {@code uq_acceptance}.
     */
    @Transactional
    public void record(UUID userId, LegalDocumentCode code, String ip, String userAgent) {
        LegalDocument document = documents.findVigente(code.name(), hoy())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay versión de " + code + " en vigor el " + hoy()
                                + ". O la siembra no corrió, o la única versión sembrada entra en "
                                + "vigor más tarde que esta fecha (revisa el reloj si es un test)."));

        if (acceptances.existsByUserIdAndDocumentCodeAndVersion(
                userId, code.name(), document.getVersion())) {
            return;
        }
        acceptances.save(new AgreementAcceptance(
                userId, code.name(), document.getVersion(), ip, truncate(userAgent)));
    }

    /** Qué documentos vigentes le faltan por aceptar a alguien. Vacío = está al día. */
    @Transactional(readOnly = true)
    public List<LegalDocumentCode> pendientes(UUID userId) {
        return List.of(LegalDocumentCode.values()).stream()
                .filter(code -> documents.findVigente(code.name(), hoy())
                        .map(doc -> !acceptances.existsByUserIdAndDocumentCodeAndVersion(
                                userId, code.name(), doc.getVersion()))
                        // Sin versión sembrada no se le puede exigir a nadie que la acepte.
                        .orElse(false))
                .toList();
    }

    private Rendered render(LegalDocument document) {
        Map<String, String> valores = Map.of(
                "responsable", identity.responsable(),
                "documento", identity.documento(),
                "domicilio", identity.domicilio(),
                "ciudad", identity.ciudad(),
                "correo", identity.correo(),
                "whatsapp", identity.whatsapp(),
                "horario", identity.horario(),
                "vigencia", document.getEffectiveFrom().format(VIGENCIA));

        String body = document.getBody();
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            body = body.replace("{{" + valor.getKey() + "}}", valor.getValue());
        }
        return new Rendered(document.getCode(), document.getVersion(), document.getTitle(),
                body, document.getEffectiveFrom());
    }

    private LocalDate hoy() {
        return LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
    }

    /** La columna admite 300; un user-agent largo no puede tumbar un registro de consentimiento. */
    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 300 ? userAgent : userAgent.substring(0, 300);
    }

    /** El documento listo para mostrarse: sin marcadores, con su versión y su vigencia. */
    public record Rendered(String code, String version, String title, String body,
                           LocalDate effectiveFrom) {
    }
}
