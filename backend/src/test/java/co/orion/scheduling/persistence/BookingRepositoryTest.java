package co.orion.scheduling.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import co.orion.scheduling.TestBookings;
import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.shared.config.ClockConfig;
import co.orion.shared.config.JpaAuditingConfig;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, ClockConfig.class})
class BookingRepositoryTest {

    private static final Instant MONDAY_18 = Instant.parse("2026-07-20T23:00:00Z"); // 18:00 Bogotá
    private static final Instant MONDAY_19 = Instant.parse("2026-07-21T00:00:00Z");
    private static final Instant MONDAY_20 = Instant.parse("2026-07-21T01:00:00Z");

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private UserRepository users;

    private UUID ana;
    private UUID maria;

    @BeforeEach
    void seed() {
        ana = users.save(new User("ana@orion.test", "hash", "Ana Ramírez", UserRole.STUDENT)).getId();
        maria = users.save(new User("maria@orion.test", "hash", "María Gómez", UserRole.PROFESSOR)).getId();
    }

    @Test
    void findsTheConfirmedBookingsOfAProfessorInARange() {
        bookings.save(booking(ana, maria, MONDAY_18, MONDAY_19));

        assertThat(bookings.findByProfessorIdAndStatusInAndStartsAtBetween(
                maria, List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT),
                MONDAY_18, MONDAY_20)).hasSize(1);
    }

    @Test
    void theDatabaseRejectsASecondConfirmedBookingForTheSameProfessorAndSlot() {
        bookings.saveAndFlush(booking(ana, maria, MONDAY_18, MONDAY_19));

        UUID otherStudent = users.save(
                new User("carlos@orion.test", "hash", "Carlos Peña", UserRole.STUDENT)).getId();

        // El árbitro final de la doble reserva es el índice único parcial, no el código:
        // aunque dos requests pasen el chequeo previo a la vez, solo una fila sobrevive.
        assertThatThrownBy(() -> bookings.saveAndFlush(booking(otherStudent, maria, MONDAY_18, MONDAY_19)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Con cupos cada media hora, la de las 18:00 (hasta las 18:55) y la de las 18:30 se pisan con
     * distinto starts_at: el índice único de la V16 las dejaba entrar a las dos. La EXCLUDE de la
     * V78 mira el intervalo entero.
     */
    @Test
    void theDatabaseRejectsTwoActiveBookingsOfTheSameProfessorThatOverlap() {
        bookings.saveAndFlush(booking(ana, maria, MONDAY_18, MONDAY_18.plusSeconds(55 * 60)));
        UUID carlos = users.save(
                new User("carlos@orion.test", "hash", "Carlos Peña", UserRole.STUDENT)).getId();
        Instant halfPast = MONDAY_18.plusSeconds(30 * 60);

        assertThatThrownBy(() -> bookings.saveAndFlush(
                booking(carlos, maria, halfPast, halfPast.plusSeconds(55 * 60))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_bookings_professor_no_overlap");
    }

    /** También mientras una de las dos espera el pago: ese cupo ya está apartado. */
    @Test
    void aBookingAwaitingPaymentAlsoBlocksAnOverlappingOne() {
        bookings.saveAndFlush(TestBookings.awaitingPayment(ana, maria, MONDAY_18,
                MONDAY_18.plusSeconds(55 * 60), BookingModality.VIRTUAL, null, ana));
        UUID carlos = users.save(
                new User("carlos@orion.test", "hash", "Carlos Peña", UserRole.STUDENT)).getId();
        Instant halfPast = MONDAY_18.plusSeconds(30 * 60);

        assertThatThrownBy(() -> bookings.saveAndFlush(
                booking(carlos, maria, halfPast, halfPast.plusSeconds(55 * 60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void touchingBordersCancelledRowsAndOtherProfessorsDoNotCollide() {
        UUID carlos = users.save(
                new User("carlos@orion.test", "hash", "Carlos Peña", UserRole.STUDENT)).getId();
        UUID juan = users.save(
                new User("juan@orion.test", "hash", "Juan Torres", UserRole.PROFESSOR)).getId();

        bookings.saveAndFlush(booking(ana, maria, MONDAY_18, MONDAY_19));
        // [18:00, 19:00) y [19:00, 20:00) se tocan pero no se pisan.
        bookings.saveAndFlush(booking(carlos, maria, MONDAY_19, MONDAY_20));
        // Otro profesor a la misma hora: nada que ver.
        bookings.saveAndFlush(booking(ana, juan, MONDAY_18, MONDAY_19));

        // Una cancelada deja de ocupar: la de las 17:30 (hasta las 18:25) entra aunque la pisaba.
        Booking cancelled = bookings.findByProfessorIdAndStatusInAndStartsAtBetween(
                maria, List.of(BookingStatus.CONFIRMED), MONDAY_18, MONDAY_18).getFirst();
        cancelled.cancel(BookingStatus.CANCELLED_BY_STUDENT, ana, MONDAY_18.minusSeconds(86_400), null);
        bookings.saveAndFlush(cancelled);
        Instant halfPastFive = MONDAY_18.minusSeconds(30 * 60);
        bookings.saveAndFlush(booking(carlos, maria, halfPastFive, MONDAY_18.plusSeconds(25 * 60)));

        assertThat(bookings.count()).isEqualTo(4);
    }

    @Test
    void detectsThatTheStudentAlreadyHasAConfirmedBookingAtThatTime() {
        bookings.saveAndFlush(booking(ana, maria, MONDAY_18, MONDAY_19));

        assertThat(bookings.studentHasOverlappingBooking(ana, MONDAY_18, MONDAY_19)).isTrue();
    }

    @Test
    void aBookingThatOnlyTouchesTheBorderIsNotAnOverlap() {
        bookings.saveAndFlush(booking(ana, maria, MONDAY_18, MONDAY_19));

        // [18:00, 19:00) y [19:00, 20:00) se tocan pero no se pisan: semiabierto, como siempre.
        assertThat(bookings.studentHasOverlappingBooking(ana, MONDAY_19, MONDAY_20)).isFalse();
    }

    private Booking booking(UUID studentId, UUID professorId, Instant startsAt, Instant endsAt) {
        return TestBookings.confirmed(studentId, professorId, startsAt, endsAt,
                BookingModality.VIRTUAL, null, studentId);
    }
}
