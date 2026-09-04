package co.orion.scheduling.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;

/**
 * JpaSpecificationExecutor da el listado con filtros del panel de admin. Se usa Specification y
 * no un @Query con "(:from is null or ...)": Postgres no puede inferir el tipo de un parámetro
 * nulo y responde "could not determine data type of parameter $1". La Specification construye
 * la consulta con los filtros que de verdad llegaron, así que ese parámetro nunca se envía.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID>,
        JpaSpecificationExecutor<Booking> {

    /**
     * Las reservas que ocupan cupos del profesor en un rango: la entrada del SlotCalculator.
     * Recibe una colección de estados porque desde el Bloque 4 ocupan cupo dos: CONFIRMED y
     * PENDING_PAYMENT. Es el mismo par que filtra el índice único parcial de la tabla.
     */
    List<Booking> findByProfessorIdAndStatusInAndStartsAtBetween(UUID professorId,
                                                                 Collection<BookingStatus> statuses,
                                                                 Instant from,
                                                                 Instant to);

    List<Booking> findByStudentIdAndStatusAndStartsAtBetween(UUID studentId,
                                                             BookingStatus status,
                                                             Instant from,
                                                             Instant to);

    /**
     * ¿Han coincidido alguna vez en una clase? Es lo que habilita al profesor a iniciar una
     * conversación: sin esa relación previa podría escribirle a cualquiera del directorio.
     *
     * Cuenta cualquier estado, incluidas las canceladas: si una clase se canceló, hablarlo es
     * justamente lo que hay que poder hacer.
     */
    boolean existsByProfessorIdAndStudentId(UUID professorId, UUID studentId);

    boolean existsByStudentIdAndStatus(UUID studentId, BookingStatus status);

    /** Próximas: activas y que aún no empiezan, de la más cercana a la más lejana. */
    List<Booking> findByStudentIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(
            UUID studentId, Collection<BookingStatus> statuses, Instant now);

    List<Booking> findByProfessorIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(
            UUID professorId, Collection<BookingStatus> statuses, Instant now);

    /** Las reservas cuyo plazo para pagar ya se cumplió: la entrada del job de expiración. */
    List<Booking> findByStatusAndExpiresAtLessThanEqual(BookingStatus status, Instant deadline);

    /**
     * Clases terminadas hace rato y todavía sin cerrar: la entrada del autocompletado. El filtro
     * por completed_at nulo es lo que lo hace idempotente incluso antes de mirar cada reserva.
     */
    List<Booking> findByStatusAndEndsAtLessThanAndCompletedAtIsNull(
            BookingStatus status, Instant deadline);

    /** Pasadas: todo lo demás — ya ocurrieron o están en un estado terminal. */
    @Query("""
            select b from Booking b
            where b.studentId = :userId
              and (b.status not in :activeStatuses or b.startsAt <= :now)
            order by b.startsAt desc
            """)
    List<Booking> findPastOfStudent(@Param("userId") UUID studentId,
                                    @Param("activeStatuses") Collection<BookingStatus> activeStatuses,
                                    @Param("now") Instant now);

    @Query("""
            select b from Booking b
            where b.professorId = :userId
              and (b.status not in :activeStatuses or b.startsAt <= :now)
            order by b.startsAt desc
            """)
    List<Booking> findPastOfProfessor(@Param("userId") UUID professorId,
                                      @Param("activeStatuses") Collection<BookingStatus> activeStatuses,
                                      @Param("now") Instant now);

    /* --- Insumos de las métricas de desempeño (ventana móvil, por fecha de la CLASE) --- */

    long countByProfessorIdAndStatusInAndStartsAtAfter(
            UUID professorId, Collection<BookingStatus> statuses, Instant since);

    long countByProfessorIdAndStartsAtAfter(UUID professorId, Instant since);

    /** Estudiantes DISTINTOS con clase cerrada en la ventana: la retención real, no el volumen. */
    @Query("""
            select count(distinct b.studentId) from Booking b
            where b.professorId = :professorId
              and b.startsAt > :since
              and b.status in (co.orion.scheduling.domain.BookingStatus.COMPLETED,
                               co.orion.scheduling.domain.BookingStatus.NO_SHOW_STUDENT)
            """)
    long countDistinctStudentsOfProfessorSince(@Param("professorId") UUID professorId,
                                               @Param("since") Instant since);

    long countByStatus(BookingStatus status);

    /** Reservas creadas en los últimos 7 días (por fecha de creación, no de la clase). */
    long countByCreatedAtGreaterThanEqual(Instant since);

    /**
     * El porcentaje histórico de reservas de autoservicio: las que creó el propio estudiante,
     * sin que nadie de la academia tuviera que intervenir. Es la métrica estrella del MVP.
     */
    @Query("""
            select coalesce(
                     sum(case when b.createdBy = b.studentId then 1.0 else 0.0 end) * 100.0
                     / nullif(count(b), 0),
                   0.0)
            from Booking b
            """)
    double selfServicePercentage();

    /**
     * Un estudiante no puede tener dos clases que se pisen, ni siquiera con profesores distintos.
     * Cuenta también la que está pagando: si no, abre dos checkouts a la misma hora y paga los dos. Misma semántica semiabierta [inicio, fin) que el resto del dominio:
     * existente.inicio < nueva.fin AND nueva.inicio < existente.fin.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.studentId = :studentId
              and b.status in (co.orion.scheduling.domain.BookingStatus.CONFIRMED,
                               co.orion.scheduling.domain.BookingStatus.PENDING_PAYMENT)
              and b.startsAt < :endsAt
              and :startsAt < b.endsAt
            """)
    boolean studentHasOverlappingBooking(@Param("studentId") UUID studentId,
                                         @Param("startsAt") Instant startsAt,
                                         @Param("endsAt") Instant endsAt);
}
