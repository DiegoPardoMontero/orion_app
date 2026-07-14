package co.orion.scheduling.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Las reservas que ocupan cupos del profesor en un rango: la entrada del SlotCalculator. */
    List<Booking> findByProfessorIdAndStatusAndStartsAtBetween(UUID professorId,
                                                               BookingStatus status,
                                                               Instant from,
                                                               Instant to);

    List<Booking> findByStudentIdAndStatusAndStartsAtBetween(UUID studentId,
                                                             BookingStatus status,
                                                             Instant from,
                                                             Instant to);

    /**
     * Un estudiante no puede tener dos clases confirmadas que se pisen, ni siquiera con
     * profesores distintos. Misma semántica semiabierta [inicio, fin) que el resto del dominio:
     * existente.inicio < nueva.fin AND nueva.inicio < existente.fin.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.studentId = :studentId
              and b.status = co.orion.scheduling.domain.BookingStatus.CONFIRMED
              and b.startsAt < :endsAt
              and :startsAt < b.endsAt
            """)
    boolean studentHasOverlappingBooking(@Param("studentId") UUID studentId,
                                         @Param("startsAt") Instant startsAt,
                                         @Param("endsAt") Instant endsAt);
}
