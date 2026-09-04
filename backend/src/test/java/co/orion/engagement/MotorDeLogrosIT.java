package co.orion.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import co.orion.TestcontainersConfiguration;
import co.orion.engagement.application.AchievementService;
import co.orion.engagement.domain.UserAchievement;
import co.orion.engagement.persistence.PointEventRepository;
import co.orion.engagement.persistence.StreakProtectionRepository;
import co.orion.engagement.persistence.UserAchievementRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * El motor de logros. El test que importa es el último: que recalcular desde cero produzca
 * exactamente el mismo estado que el procesamiento incremental. Es lo que protege todo el bloque —
 * si eso se cumple, un evento perdido se arregla con un botón en vez de con una migración.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, MotorDeLogrosIT.FrozenClockConfiguration.class})
class MotorDeLogrosIT extends ApiIntegrationSupport {

    /** Miércoles 22 de julio de 2026, mediodía en Bogotá. */
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-22T17:00:00Z");

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AchievementService motor;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private UserAchievementRepository userAchievements;

    @Autowired
    private PointEventRepository pointEvents;

    @Autowired
    private StreakProtectionRepository protections;

    private User ana;
    private User maria;
    private User juan;
    private User gratuito;

    @BeforeEach
    void seed() {
        pointEvents.deleteAll();
        userAchievements.deleteAll();
        protections.deleteAll();
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = profesor("maria@orion.test", "María Gómez", 45_000L);
        juan = profesor("juan@orion.test", "Juan Torres", 55_000L);
        gratuito = profesor("free@orion.test", "Profe Gratis", 0L);
    }

    private User profesor(String email, String nombre, long tarifa) {
        User profesor = createUser(email, nombre, UserRole.PROFESSOR);
        ProfessorProfile perfil = new ProfessorProfile(profesor);
        perfil.changeRate(tarifa);
        profiles.save(perfil);
        return profesor;
    }

    private Instant enBogota(LocalDate dia, int hora) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, 0), BusinessZone.BOGOTA).toInstant();
    }

    /** Una clase ya cerrada, como la deja el cierre automático. */
    private Booking claseTomada(User profesor, LocalDate dia, BookingModality modalidad, String idioma) {
        Booking booking = TestBookings.confirmed(ana.getId(), profesor.getId(),
                enBogota(dia, 18), enBogota(dia, 19), modalidad, null, idioma, ana.getId());
        booking.autoComplete(FROZEN_NOW);
        return bookings.save(booking);
    }

    private Booking claseTomada(User profesor, LocalDate dia) {
        return claseTomada(profesor, dia, BookingModality.VIRTUAL, "EN");
    }

    private Map<String, UserAchievement> estadoDe(UUID studentId) {
        return userAchievements.findByUserId(studentId).stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementCode, u -> u));
    }

    /* ---- Conceder puntos ---- */

    @Test
    void unaClaseCompletadaConcedeSusPuntosYEnciendeLaPrimeraEstrella() {
        Booking clase = claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onLessonCompleted(ana.getId(), clase.getId(), FROZEN_NOW);

        assertThat(pointEvents.totalPointsOf(ana.getId()))
                // 25 de la clase + 25 de «Primera clase» + 10 de «Primera reserva»
                .isEqualTo(60);
        assertThat(estadoDe(ana.getId()).get("primeros-primera-clase").isUnlocked()).isTrue();
    }

    /** El evento se puede reenviar. Procesarlo dos veces no puede pagar dos veces. */
    @Test
    void procesarDosVecesElMismoEventoConcedeUnaSolaVez() {
        Booking clase = claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onLessonCompleted(ana.getId(), clase.getId(), FROZEN_NOW);
        long despuesDeLaPrimera = pointEvents.totalPointsOf(ana.getId());
        motor.onLessonCompleted(ana.getId(), clase.getId(), FROZEN_NOW);

        assertThat(pointEvents.totalPointsOf(ana.getId())).isEqualTo(despuesDeLaPrimera);
    }

    /** Una estrella encendida no se vuelve a encender, ni vuelve a pagar. */
    @Test
    void unLogroNoSeDesbloqueaDosVeces() {
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        Booking segunda = claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onLessonCompleted(ana.getId(), segunda.getId(), FROZEN_NOW);
        Instant primerEncendido = estadoDe(ana.getId()).get("primeros-primera-clase").getUnlockedAt();

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("primeros-primera-clase").getUnlockedAt())
                .isEqualTo(primerEncendido);
    }

    /* ---- Lo que NO cuenta ---- */

    @Test
    void unaClaseCanceladaNoSuma() {
        Booking cancelada = TestBookings.confirmed(ana.getId(), maria.getId(),
                enBogota(LocalDate.of(2026, 7, 20), 18), BookingModality.VIRTUAL, null, ana.getId());
        cancelada.cancel(BookingStatus.CANCELLED_BY_STUDENT, ana.getId(),
                enBogota(LocalDate.of(2026, 7, 19), 10), "Imprevisto");
        bookings.save(cancelada);

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("primeros-primera-clase").isUnlocked()).isFalse();
    }

    @Test
    void unNoShowNoSuma() {
        Booking noShow = TestBookings.confirmed(ana.getId(), maria.getId(),
                enBogota(LocalDate.of(2026, 7, 20), 18), BookingModality.VIRTUAL, null, ana.getId());
        noShow.closeWithAttendance(false, FROZEN_NOW);
        bookings.save(noShow);

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("primeros-primera-clase").isUnlocked()).isFalse();
    }

    /**
     * Las clases gratuitas las fija un administrador para probar el flujo en producción. Que las
     * pruebas enciendan estrellas contaminaría el perfil de alguien real.
     */
    @Test
    void unaClaseGratuitaNoSumaPorDefecto() {
        Booking gratis = claseTomada(gratuito, LocalDate.of(2026, 7, 20));

        motor.onLessonCompleted(ana.getId(), gratis.getId(), FROZEN_NOW);

        assertThat(estadoDe(ana.getId()).get("primeros-primera-clase").isUnlocked()).isFalse();
        // Tampoco paga los 25 de la clase; sí enciende «Primera reserva», que solo mira que exista.
        assertThat(pointEvents.findByUserIdOrderByOccurredAtDesc(ana.getId()))
                .noneMatch(e -> e.getSourceType().equals("LESSON"));
    }

    /* ---- Los evaluadores ---- */

    @Test
    void tresProfesoresDistintosEnciendenTresVoces() {
        User pedro = profesor("pedro@orion.test", "Pedro", 40_000L);
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        claseTomada(juan, LocalDate.of(2026, 7, 13));
        claseTomada(pedro, LocalDate.of(2026, 7, 20));

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("amplitud-tres-voces").isUnlocked()).isTrue();
    }

    @Test
    void dosIdiomasDistintosEnciendenDosIdiomas() {
        claseTomada(maria, LocalDate.of(2026, 7, 13), BookingModality.VIRTUAL, "EN");
        claseTomada(juan, LocalDate.of(2026, 7, 20), BookingModality.VIRTUAL, "FR");

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("amplitud-dos-idiomas").isUnlocked()).isTrue();
    }

    /** Una reserva sin idioma no puede contar como «otro idioma»: no sabemos cuál era. */
    @Test
    void unaClaseSinIdiomaNoCuentaComoOtroIdioma() {
        claseTomada(maria, LocalDate.of(2026, 7, 13), BookingModality.VIRTUAL, "EN");
        claseTomada(juan, LocalDate.of(2026, 7, 20), BookingModality.VIRTUAL, null);

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("amplitud-dos-idiomas").isUnlocked()).isFalse();
    }

    @Test
    void unaClasePresencialEnciendeCaraACara() {
        claseTomada(maria, LocalDate.of(2026, 7, 20), BookingModality.IN_PERSON, "EN");

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("amplitud-presencial").isUnlocked()).isTrue();
    }

    @Test
    void elProgresoSeGuardaAunqueNoSeAlcanceLaMeta() {
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onSomethingHappened(ana.getId());

        var cincoClases = estadoDe(ana.getId()).get("volumen-5-clases");
        assertThat(cincoClases.isUnlocked()).isFalse();
        // «2 de 5» es justo lo que hace que el siguiente paso parezca alcanzable.
        assertThat(cincoClases.getProgress()).isEqualTo(2);
    }

    /* ---- La racha ---- */

    @Test
    void dosSemanasSeguidasEnciendenLaConstancia() {
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onSomethingHappened(ana.getId());

        assertThat(estadoDe(ana.getId()).get("constancia-2-semanas").isUnlocked()).isTrue();
    }

    @Test
    void unaSemanaVaciaSeProtegeYQuedaRegistrada() {
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        // sin clase la semana del 13
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onSomethingHappened(ana.getId());

        assertThat(protections.findByUserId(ana.getId()))
                .singleElement()
                .satisfies(p -> assertThat(p.getWeekStart()).isEqualTo(LocalDate.of(2026, 7, 13)));
        assertThat(estadoDe(ana.getId()).get("constancia-2-semanas").isUnlocked()).isTrue();
    }

    /** Reevaluar dos veces no concede dos protecciones para la misma semana. */
    @Test
    void laProteccionNoSeConcedeDosVeces() {
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.onSomethingHappened(ana.getId());
        motor.onSomethingHappened(ana.getId());

        assertThat(protections.findByUserId(ana.getId())).hasSize(1);
    }

    /* ---- El test que protege el bloque ---- */

    /**
     * Recalcular desde cero tiene que llegar exactamente al mismo sitio que el camino incremental:
     * mismos logros, mismo progreso, mismos puntos y mismas protecciones. Si esto se cumple, un
     * evento perdido se arregla con un botón; si no, hace falta una migración de datos.
     */
    @Test
    void recomputeProduceElMismoEstadoQueElProcesamientoIncremental() {
        User pedro = profesor("pedro@orion.test", "Pedro", 40_000L);
        List<Booking> clases = List.of(
                claseTomada(maria, LocalDate.of(2026, 6, 8)),
                claseTomada(juan, LocalDate.of(2026, 6, 15)),
                // hueco: semana del 22 de junio → protegida
                claseTomada(maria, LocalDate.of(2026, 6, 29)),
                claseTomada(pedro, LocalDate.of(2026, 7, 6), BookingModality.IN_PERSON, "FR"),
                claseTomada(maria, LocalDate.of(2026, 7, 13)),
                claseTomada(maria, LocalDate.of(2026, 7, 20)));

        // Camino incremental: un evento por clase, como en la vida real.
        for (Booking clase : clases) {
            motor.onLessonCompleted(ana.getId(), clase.getId(), FROZEN_NOW);
        }

        var estadoIncremental = estadoDe(ana.getId()).values().stream()
                .map(u -> u.getAchievementCode() + ":" + u.getProgress() + ":" + u.isUnlocked())
                .sorted()
                .toList();
        long puntosIncremental = pointEvents.totalPointsOf(ana.getId());
        var proteccionesIncremental = protections.findByUserId(ana.getId()).stream()
                .map(p -> p.getWeekStart())
                .sorted(Comparator.naturalOrder())
                .toList();

        motor.recompute(ana.getId());

        var estadoRecalculado = estadoDe(ana.getId()).values().stream()
                .map(u -> u.getAchievementCode() + ":" + u.getProgress() + ":" + u.isUnlocked())
                .sorted()
                .toList();

        assertThat(estadoRecalculado).isEqualTo(estadoIncremental);
        // Los puntos no se duplican: el libro es append-only y su índice único lo garantiza.
        assertThat(pointEvents.totalPointsOf(ana.getId())).isEqualTo(puntosIncremental);
        assertThat(protections.findByUserId(ana.getId()).stream().map(p -> p.getWeekStart()).sorted())
                .containsExactlyElementsOf(proteccionesIncremental);
    }

    /** Y sobre un historial que nunca se procesó: el backfill llega al mismo sitio. */
    @Test
    void recomputeSobreUnHistorialSinProcesarEnciendeLoQueCorresponde() {
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        motor.recompute(ana.getId());

        var estado = estadoDe(ana.getId());
        assertThat(estado.get("primeros-primera-clase").isUnlocked()).isTrue();
        assertThat(estado.get("constancia-2-semanas").isUnlocked()).isTrue();
        assertThat(estado.get("volumen-5-clases").getProgress()).isEqualTo(3);
    }
}
