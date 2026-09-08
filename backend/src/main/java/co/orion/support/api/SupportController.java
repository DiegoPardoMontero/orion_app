package co.orion.support.api;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.shared.error.UnprocessableException;
import co.orion.shared.security.OrionUserDetails;
import co.orion.support.application.SupportService;
import co.orion.support.domain.TicketCategory;
import jakarta.validation.Valid;

/**
 * Las solicitudes de quien está dentro. Estudiantes y profesores: un profesor que no puede reclamar
 * formalmente su pago es un problema que vuelve por otro lado, y peor.
 */
@RestController
@RequestMapping("/api/v1/me/support")
public class SupportController {

    private final SupportService support;
    private final Clock clock;

    public SupportController(SupportService support, Clock clock) {
        this.support = support;
        this.clock = clock;
    }

    /** Las categorías, con su etiqueta y si llevan plazo legal. El formulario se dibuja con esto. */
    @GetMapping("/categories")
    public List<CategoryOption> categories() {
        return Arrays.stream(TicketCategory.values())
                .map(c -> new CategoryOption(c.name(), c.getEtiqueta(), c.tienePlazoLegal()))
                .toList();
    }

    @GetMapping("/tickets")
    public List<TicketSummary> mine(@AuthenticationPrincipal OrionUserDetails principal) {
        var now = clock.instant();
        return support.mios(principal.user().getId()).stream()
                .map(t -> TicketSummary.from(t, now))
                .toList();
    }

    @PostMapping("/tickets")
    public TicketThread open(@AuthenticationPrincipal OrionUserDetails principal,
                             @Valid @RequestBody OpenTicketRequest body) {
        return TicketThread.from(
                support.abrir(principal.user().getId(), parse(body.category()), body.subject(),
                        body.body(), body.bookingId()),
                principal.user().getId(), clock.instant());
    }

    @GetMapping("/tickets/{code}")
    public TicketThread one(@AuthenticationPrincipal OrionUserDetails principal,
                            @PathVariable String code) {
        return TicketThread.from(support.verComoDueno(principal.user().getId(), code),
                principal.user().getId(), clock.instant());
    }

    @PostMapping("/tickets/{code}/replies")
    public TicketThread reply(@AuthenticationPrincipal OrionUserDetails principal,
                              @PathVariable String code,
                              @Valid @RequestBody ReplyRequest body) {
        return TicketThread.from(
                support.responder(principal.user().getId(), code, body.body(), false),
                principal.user().getId(), clock.instant());
    }

    /** 422 con los válidos, no un 500: un código de catálogo inexistente es un error de entrada. */
    private TicketCategory parse(String category) {
        try {
            return TicketCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnprocessableException("Esa categoría no existe. Válidas: "
                    + Arrays.toString(TicketCategory.values()));
        }
    }

    public record CategoryOption(String code, String label, boolean hasLegalDeadline) {
    }
}
