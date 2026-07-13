package co.orion.scheduling.persistence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.scheduling.domain.AvailabilityRule;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule> findByProfessorIdAndActiveTrue(UUID professorId);

    List<AvailabilityRule> findByProfessorIdOrderByWeekdayAscStartTimeAsc(UUID professorId);

    /**
     * Semántica semiabierta [inicio, fin): dos franjas se solapan si y solo si
     * existente.inicio < nueva.fin AND nueva.inicio < existente.fin.
     * Por eso 18:00–21:00 y 21:00–22:00 NO se solapan (se tocan en el borde),
     * mientras que 18:00–21:00 y 20:00–22:00 sí.
     */
    @Query("""
            select count(r) > 0 from AvailabilityRule r
            where r.professorId = :professorId
              and r.weekday = :weekday
              and r.active = true
              and r.startTime < :endTime
              and :startTime < r.endTime
            """)
    boolean overlapsActiveRule(@Param("professorId") UUID professorId,
                               @Param("weekday") DayOfWeek weekday,
                               @Param("startTime") LocalTime startTime,
                               @Param("endTime") LocalTime endTime);
}
