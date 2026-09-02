package co.orion.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.billing.domain.StudentCredit;
import jakarta.persistence.LockModeType;

public interface StudentCreditRepository extends JpaRepository<StudentCredit, UUID> {

    /**
     * Los créditos gastables de un estudiante, en orden FIFO por vencimiento (los que vencen antes
     * se gastan antes; los sin vencimiento, al final).
     *
     * PESSIMISTIC_WRITE — el único bloqueo pesimista del proyecto, y con motivo: sin él dos
     * pestañas leen el mismo saldo y las dos lo gastan. Aquí no hay una constraint que pueda
     * arbitrar la carrera como la hay en los cupos (el saldo es un número que baja, no una fila
     * única que colisiona), así que el árbitro tiene que ser el bloqueo. Son pocas filas por
     * estudiante y el bloqueo dura lo que dura la reserva.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from StudentCredit c
            where c.studentId = :studentId
              and c.remainingCop > 0
              and (c.expiresAt is null or c.expiresAt > :now)
            order by case when c.expiresAt is null then 1 else 0 end, c.expiresAt asc, c.createdAt asc
            """)
    List<StudentCredit> findUsableForUpdate(@Param("studentId") UUID studentId,
                                            @Param("now") Instant now);

    /** Saldo y detalle para la pantalla del estudiante: sin bloqueo, es solo lectura. */
    @Query("""
            select c from StudentCredit c
            where c.studentId = :studentId
              and c.remainingCop > 0
              and (c.expiresAt is null or c.expiresAt > :now)
            order by case when c.expiresAt is null then 1 else 0 end, c.expiresAt asc, c.createdAt asc
            """)
    List<StudentCredit> findUsable(@Param("studentId") UUID studentId, @Param("now") Instant now);
}
