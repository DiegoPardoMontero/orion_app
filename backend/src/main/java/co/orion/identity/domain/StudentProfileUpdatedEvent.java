package co.orion.identity.domain;

import java.util.UUID;

/**
 * El estudiante tocó su ficha. Lo publica `identity` sin saber quién escucha: `engagement` lo usa
 * para reevaluar «Perfil listo» y «Objetivo declarado», y aquí no se sabe qué es un punto.
 */
public record StudentProfileUpdatedEvent(UUID studentId) {
}
