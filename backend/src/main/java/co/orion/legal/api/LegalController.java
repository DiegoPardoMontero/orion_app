package co.orion.legal.api;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.config.LegalIdentity;

/**
 * Los documentos legales y los datos de contacto, en abierto.
 *
 * <p>Públicos a propósito: el art. 50 de la Ley 1480 de 2011 exige que la identidad del proveedor
 * y las condiciones estén disponibles <em>antes</em> de contratar. Un documento que solo se ve tras
 * iniciar sesión llega tarde.
 */
@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private final LegalDocumentService legal;
    private final LegalIdentity identity;

    public LegalController(LegalDocumentService legal, LegalIdentity identity) {
        this.legal = legal;
        this.identity = identity;
    }

    /** Quién responde y por dónde se le escribe. Lo consumen la pantalla de ayuda y el pie. */
    @GetMapping("/contacto")
    public ContactoResponse contacto() {
        return new ContactoResponse(
                identity.responsable(),
                identity.documento(),
                identity.domicilio(),
                identity.ciudad(),
                identity.correo(),
                identity.whatsapp(),
                identity.whatsappDigits(),
                identity.horario());
    }

    @GetMapping("/{code}")
    public DocumentoResponse vigente(@PathVariable String code) {
        return DocumentoResponse.from(legal.vigente(parse(code)));
    }

    /** Una versión anterior, para responder «¿qué acepté cuando me registré?». */
    @GetMapping("/{code}/{version}")
    public DocumentoResponse version(@PathVariable String code, @PathVariable String version) {
        return DocumentoResponse.from(legal.version(parse(code), version));
    }

    private LegalDocumentCode parse(String code) {
        try {
            return LegalDocumentCode.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new co.orion.shared.error.ResourceNotFoundException("Documento no encontrado");
        }
    }

    public record ContactoResponse(String responsable, String documento, String domicilio,
                                   String ciudad, String correo, String whatsapp,
                                   String whatsappDigits, String horario) {
    }

    public record DocumentoResponse(String code, String version, String title, String body,
                                    LocalDate effectiveFrom) {

        static DocumentoResponse from(LegalDocumentService.Rendered r) {
            return new DocumentoResponse(r.code(), r.version(), r.title(), r.body(),
                    r.effectiveFrom());
        }
    }
}
