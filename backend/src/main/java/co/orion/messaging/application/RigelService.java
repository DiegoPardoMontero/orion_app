package co.orion.messaging.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.ProfessorAccessService;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.messaging.domain.RigelKind;
import co.orion.messaging.persistence.RigelMessages;
import co.orion.messaging.persistence.RigelMessages.Guardado;
import co.orion.shared.time.FechasEnPalabras;

/**
 * El hilo de Rigel en «Mensajes» (24/09/2026): mensajes oficiales de Orión, de solo lectura, a
 * estudiantes y profesores. Pocos a propósito —«no quiero que sea excesivamente invasivo»—: cada
 * uno sale una vez, sin campana, sin correo y sin aviso al dispositivo. Lo único que avisan es el
 * número sin leer de «Mensajes».
 *
 * <p>La bienvenida se deja al abrir el hilo, y no al crear la cuenta: una cuenta nace por seis
 * puertas (registro, Google, la invitación del admin, la postulación aprobada…), y así le llega a
 * todas —también a quien ya usaba Orión antes de que Rigel escribiera— sin tocar ninguna.
 */
@Service
public class RigelService {

    public record Hilo(int noLeidos, List<Leido> mensajes) {
    }

    public record Leido(Guardado guardado, TextosDeRigel.Mensaje mensaje) {
    }

    private final RigelMessages mensajes;
    private final ProfessorAccessService access;
    private final Clock clock;

    public RigelService(RigelMessages mensajes, ProfessorAccessService access, Clock clock) {
        this.mensajes = mensajes;
        this.access = access;
        this.clock = clock;
    }

    /** El hilo de quien lo abre, con la bienvenida si todavía no la tenía. */
    @Transactional
    public Hilo hilo(User lector) {
        bienvenidaSiFalta(lector);
        String nombre = FechasEnPalabras.primerNombre(lector.getFullName());
        List<Leido> leidos = mensajes.de(lector.getId()).stream()
                .map(g -> new Leido(g, TextosDeRigel.de(g.kind(), nombre, g.params())))
                .toList();
        int noLeidos = (int) leidos.stream().filter(l -> l.guardado().readAt() == null).count();
        return new Hilo(noLeidos, leidos);
    }

    @Transactional
    public void marcarLeidos(User lector) {
        mensajes.marcarLeidos(lector.getId(), clock.instant());
    }

    /** Guarda un mensaje; si esa persona ya lo tenía (los de una sola vez), no pasa nada. */
    @Transactional
    public boolean enviar(UUID userId, RigelKind tipo, Map<String, String> datos) {
        return mensajes.guardar(userId, tipo, datos, clock.instant());
    }

    /**
     * El profesor recibe la suya cuando ya está aprobado: antes, «publica tu perfil» sería
     * pedirle algo que todavía no puede hacer. El admin y el aspirante no tienen hilo.
     */
    private void bienvenidaSiFalta(User lector) {
        RigelKind tipo = lector.getRole() == UserRole.STUDENT ? RigelKind.WELCOME_STUDENT
                : lector.getRole() == UserRole.PROFESSOR && access.isApproved(lector.getId())
                        ? RigelKind.WELCOME_PROFESSOR
                        : null;
        if (tipo != null && !mensajes.tiene(lector.getId(), tipo)) {
            mensajes.guardar(lector.getId(), tipo, Map.of(), clock.instant());
        }
    }
}
