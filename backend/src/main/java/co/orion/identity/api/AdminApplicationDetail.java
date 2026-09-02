package co.orion.identity.api;

import java.util.List;

/** Ficha completa de una postulación para el admin: datos, previsualización del perfil, docs e historial. */
public record AdminApplicationDetail(
        AdminApplicationSummary application,
        ProfileResponse profile,
        List<DocumentView> documents,
        List<ApplicationEventView> history) {
}
