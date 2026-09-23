package co.orion.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.api.ClassroomResponse;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * El reloj de la antesala: cuándo se puede entrar, cuándo hay llave y quién manda.
 *
 * <p>Lo que se fija aquí es que <strong>estar fuera de la ventana no es un error</strong>. La
 * antesala de quien llegó diez minutos antes porque está nervioso tiene que poder dibujarse: con la
 * cara de la otra persona, la cuenta atrás y la prueba de micrófono. Lo único que falta en ese
 * estado es la llave.
 */
class ClassroomServiceTest {

    private static final Instant INICIO = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant FIN = INICIO.plusSeconds(55 * 60);

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProfessorProfileRepository profiles = mock(ProfessorProfileRepository.class);
    private final JaasTokenMinter minter = mock(JaasTokenMinter.class);
    private final RoomPresence presence = mock(RoomPresence.class);

    private final UUID bookingId = UUID.randomUUID();
    // Los ids se fijan fuera de los mocks: pasar `otroMock.getId()` como argumento de un `when(...)`
    // es una llamada a mock dentro de otra, y Mockito lo lee como un stubbing a medias.
    private final UUID idEstudiante = UUID.randomUUID();
    private final UUID idProfesor = UUID.randomUUID();

    private User estudiante;
    private User profesor;

    @BeforeEach
    void sembrar() {
        estudiante = usuario(idEstudiante, "ana@orion.test", "Ana Ramírez");
        profesor = usuario(idProfesor, "maria@orion.test", "María Fernanda Gómez");

        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getStudentId()).thenReturn(idEstudiante);
        when(booking.getProfessorId()).thenReturn(idProfesor);
        when(booking.getStartsAt()).thenReturn(INICIO);
        when(booking.getEndsAt()).thenReturn(FIN);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));

        when(users.findById(idEstudiante)).thenReturn(Optional.of(estudiante));
        when(users.findById(idProfesor)).thenReturn(Optional.of(profesor));
        when(profiles.findById(any())).thenReturn(Optional.empty());
        when(minter.disponible()).thenReturn(true);
        when(minter.mint(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(),
                any(), any())).thenReturn("token-falso");
    }

    private static User usuario(UUID id, String correo, String nombre) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getEmail()).thenReturn(correo);
        when(u.getFullName()).thenReturn(nombre);
        when(u.getPhotoUrl()).thenReturn(null);
        return u;
    }

    private ClassroomResponse en(Instant ahora, User quien) {
        ClassroomService service = new ClassroomService(bookings, users, profiles, minter,
                new JaasProperties("app", "app/kid", "x", null, null), presence,
                Clock.fixed(ahora, ZoneOffset.UTC));
        return service.enter(bookingId, quien);
    }

    @Test
    @DisplayName("Once minutos antes la sala está cerrada, pero la antesala tiene todo lo suyo")
    void antesDeLaVentanaNoHayLlavePeroSiAntesala() {
        ClassroomResponse r = en(INICIO.minusSeconds(11 * 60), estudiante);

        assertThat(r.state()).isEqualTo("CLOSED");
        assertThat(r.token()).isNull();
        // Y sin embargo la pantalla se puede dibujar entera: es el punto.
        assertThat(r.counterpart().firstName()).isEqualTo("María");
        assertThat(r.classMinutes()).isEqualTo(55);
        assertThat(r.opensAt().toInstant()).isEqualTo(INICIO.minusSeconds(10 * 60));
    }

    @Test
    @DisplayName("Justo a los diez minutos la sala abre, no un segundo después")
    void alFiloDeLosDiezMinutosYaSePuedeEntrar() {
        // El mismo error que ya cometimos con «mínimo 6 horas»: un futuro estricto convierte
        // «desde los diez minutos» en «a partir de los nueve y pico».
        ClassroomResponse r = en(INICIO.minusSeconds(10 * 60), estudiante);

        assertThat(r.state()).isEqualTo("OPEN");
        assertThat(r.token()).isEqualTo("token-falso");
    }

    @Test
    @DisplayName("Durante la clase el estado es STARTED")
    void duranteLaClase() {
        assertThat(en(INICIO.plusSeconds(60), estudiante).state()).isEqualTo("STARTED");
        assertThat(en(FIN.plusSeconds(14 * 60), estudiante).state()).isEqualTo("STARTED");
    }

    @Test
    @DisplayName("Quince minutos después del final se acabó y la llave deja de existir")
    void pasadaLaColaSeAcaba() {
        ClassroomResponse r = en(FIN.plusSeconds(15 * 60), estudiante);

        assertThat(r.state()).isEqualTo("ENDED");
        assertThat(r.token()).isNull();
        assertThat(r.domain()).isNull();
    }

    @Test
    @DisplayName("El profesor modera la sala y el estudiante no")
    void soloElProfesorModera() {
        // Es el motivo entero de haber dejado meet.jit.si, donde mandaba quien entrara primero.
        assertThat(en(INICIO, profesor).moderator()).isTrue();
        assertThat(en(INICIO, estudiante).moderator()).isFalse();
    }

    @Test
    @DisplayName("Cada uno ve a la otra persona, no a sí mismo")
    void laContraparteEsLaOtraPersona() {
        assertThat(en(INICIO, estudiante).counterpart().name()).isEqualTo("María Fernanda Gómez");
        assertThat(en(INICIO, profesor).counterpart().name()).isEqualTo("Ana Ramírez");
    }

    @Test
    @DisplayName("Quien no es de esta clase recibe un 404, no un 403")
    void unExtranoNoSabeQueLaClaseExiste() {
        UUID idExtrano = UUID.randomUUID();
        User extrano = usuario(idExtrano, "otro@orion.test", "Otro");
        when(users.findById(idExtrano)).thenReturn(Optional.of(extrano));

        assertThatThrownBy(() -> en(INICIO, extrano))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Sin JaaS configurado la antesala sigue viva, solo que no abre")
    void sinCredencialesNoHayLlavePeroSiPantalla() {
        when(minter.disponible()).thenReturn(false);

        ClassroomResponse r = en(INICIO, estudiante);

        assertThat(r.state()).isEqualTo("STARTED");
        assertThat(r.token()).isNull();
        assertThat(r.counterpart()).isNotNull();
    }
}
