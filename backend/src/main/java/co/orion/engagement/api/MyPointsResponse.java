package co.orion.engagement.api;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import co.orion.engagement.application.EngagementQueryService;
import co.orion.engagement.domain.PointSource;
import co.orion.shared.time.BusinessZone;

/**
 * Los puntos del estudiante: el total, lo último que le dio puntos y las maneras de hacer más.
 * {@code ways} sale del mismo enum que concede los puntos, así que la pantalla nunca promete una
 * cifra distinta de la que se da.
 */
public record MyPointsResponse(long total, List<Movement> recent, List<Way> ways) {

    public record Movement(String source, int points, ZonedDateTime occurredAt, String detail) {
    }

    /** Una manera de hacer puntos. Los logros no van: cada uno da lo suyo y ya se ven en «Mi cielo». */
    public record Way(String source, int points, boolean once) {
    }

    public static MyPointsResponse from(EngagementQueryService.MisPuntos p) {
        return new MyPointsResponse(
                p.total(),
                p.recientes().stream()
                        .map(m -> new Movement(m.source(), m.points(), m.occurredAt().atZone(BusinessZone.BOGOTA),
                                m.detalle()))
                        .toList(),
                Arrays.stream(PointSource.values())
                        .filter(f -> f != PointSource.ACHIEVEMENT)
                        .map(f -> new Way(f.name(), f.points(), f.unaVez()))
                        .toList());
    }
}
