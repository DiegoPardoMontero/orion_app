package co.orion.support.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.support.domain.CallbackRequest;
import co.orion.support.persistence.CallbackRequestRepository;

/**
 * «¿Prefieres que te llame una persona?»: guardar el pedido, avisar a la academia y marcarlo
 * atendido.
 *
 * <p>El número se normaliza a dígitos (con el + del prefijo si lo trae): así el admin puede abrir
 * el chat de WhatsApp con un clic, sin adivinar qué espacio o guion sobra.
 */
@Service
public class CallbackRequestService {

    private final CallbackRequestRepository solicitudes;
    private final CallbackRequestMailer aviso;
    private final Clock clock;

    public CallbackRequestService(CallbackRequestRepository solicitudes, CallbackRequestMailer aviso,
                                  Clock clock) {
        this.solicitudes = solicitudes;
        this.aviso = aviso;
        this.clock = clock;
    }

    @Transactional
    public CallbackRequest pedir(String nombre, String whatsapp, String ip) {
        CallbackRequest guardada = solicitudes.save(
                new CallbackRequest(nombre, normalizar(whatsapp), clock.instant(), ip));
        aviso.avisar(guardada);
        return guardada;
    }

    @Transactional(readOnly = true)
    public List<CallbackRequest> bandeja() {
        return solicitudes.findTop100ByOrderByAttendedAtDescCreatedAtAsc();
    }

    @Transactional
    public CallbackRequest atender(UUID id, UUID adminId) {
        CallbackRequest solicitud = solicitudes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
        solicitud.attend(adminId, clock.instant());
        return solicitudes.save(solicitud);
    }

    /** «+57 300 123 4567» → «+573001234567». Entre 7 y 15 dígitos, que es lo que admite E.164. */
    static String normalizar(String whatsapp) {
        String limpio = whatsapp == null ? "" : whatsapp.trim();
        boolean conPrefijo = limpio.startsWith("+");
        String digitos = limpio.replaceAll("\\D", "");
        if (digitos.length() < 7 || digitos.length() > 15) {
            throw new UnprocessableException("Revisa el número: debería tener entre 7 y 15 dígitos.");
        }
        return (conPrefijo ? "+" : "") + digitos;
    }
}
