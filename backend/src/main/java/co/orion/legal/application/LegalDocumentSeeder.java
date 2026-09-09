package co.orion.legal.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.legal.domain.LegalDocument;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.legal.persistence.LegalDocumentRepository;

/**
 * Siembra los documentos legales desde {@code resources/legal/}.
 *
 * <p>Va en un runner y no en una migración de Flyway a propósito: el texto es contenido, no
 * esquema, y meterlo en una migración obligaría a una migración nueva por cada errata. Aquí basta
 * con editar el markdown y desplegar.
 *
 * <p>Es idempotente y **solo actualiza el texto de una versión que nadie haya aceptado todavía**.
 * Corregir una errata antes de que exista la primera aceptación es razonable; reescribir lo que
 * alguien ya firmó, no — para eso está publicar una versión nueva.
 */
@Component
@Order(-100) // antes que cualquier semilla de desarrollo: el registro los necesita
public class LegalDocumentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentSeeder.class);

    /** Sube la versión SOLO cuando cambie el fondo, y añade una entrada nueva aquí. */
    private static final Documento[] CATALOGO = {
            new Documento(LegalDocumentCode.TERMS, "1.0", "Términos y condiciones",
                    "legal/terms-1.0.md", LocalDate.of(2026, 9, 8)),
            new Documento(LegalDocumentCode.PRIVACY, "1.0",
                    "Política de tratamiento de la información",
                    "legal/privacy-1.0.md", LocalDate.of(2026, 9, 8)),
    };

    private final LegalDocumentRepository documents;

    public LegalDocumentSeeder(LegalDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Documento doc : CATALOGO) {
            String body = read(doc.resource());
            documents.findByCodeAndVersion(doc.code().name(), doc.version())
                    .ifPresentOrElse(
                            existente -> {
                                if (!existente.getBody().equals(body)) {
                                    existente.rewrite(doc.title(), body);
                                    documents.save(existente);
                                    log.info("Documento legal {} {} actualizado.",
                                            doc.code(), doc.version());
                                }
                            },
                            () -> {
                                documents.save(new LegalDocument(doc.code().name(), doc.version(),
                                        doc.title(), body, doc.effectiveFrom()));
                                log.info("Documento legal {} {} sembrado, vigente desde {}.",
                                        doc.code(), doc.version(), doc.effectiveFrom());
                            });
        }
    }

    private String read(String resource) {
        try {
            return new String(new ClassPathResource(resource).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // Sin documentos legales no se puede registrar a nadie: es un fallo de arranque.
            throw new UncheckedIOException("No se pudo leer " + resource, ex);
        }
    }

    private record Documento(LegalDocumentCode code, String version, String title,
                             String resource, LocalDate effectiveFrom) {
    }
}
