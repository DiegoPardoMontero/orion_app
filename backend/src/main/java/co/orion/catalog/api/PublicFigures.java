package co.orion.catalog.api;

/**
 * Los números de negocio que aparecen escritos en pantallas y documentos.
 *
 * <p>Existe por una regla de Pardo: <strong>si un número se puede cambiar desde Ajustes, tiene que
 * cambiar en todas partes</strong> — textos, documentos legales, placeholders, ayudas de formulario.
 * Un número escrito a mano en una pantalla es una segunda verdad que nadie recuerda actualizar, y
 * cuando las dos verdades se separan, la que ve el usuario es la falsa.
 *
 * <p>Va bajo {@code /api/v1/catalog}, que es público: son cifras que ya se anuncian en la página
 * de inicio y en los Términos. Nada de aquí es sensible.
 *
 * <p>Se entregan como números, no como frases hechas: cada pantalla arma su oración. Las frases
 * cambian de idioma y de tono; el número no.
 */
public record PublicFigures(
        /** Comisión de Orión, en porcentaje entero. Sale de `commission_rate_bps`. */
        int commissionPercent,
        /** Duración de una clase, en minutos. Constante de dominio, no ajuste. */
        int classMinutes,
        /** Minutos que se aparta el cupo esperando el pago. */
        int paymentHoldMinutes,
        /** Horas de antelación para que el estudiante cancele sin perder el dinero. */
        int studentCancelHours,
        /** Horas de antelación para que el profesor cancele sin sanción. */
        int professorCancelHours,
        /** Con menos de estas horas por delante, el cupo ya no se ofrece. */
        int bookingMinLeadHours,
        /** Minutos desde el inicio antes de poder reportar que el profesor no llegó. */
        int noShowReportMinutes,
        /** Horas para abrir un reclamo después de que la clase debía terminar. */
        int disputeReportWindowHours,
        /** Horas tras las que la clase se cierra sola si nadie registra asistencia. */
        int autoCompleteHours,
        /** Días hábiles prometidos para revisar una postulación de profesor. */
        int applicationReviewBusinessDays,
        /** Minutos que dura la conversación del diagnóstico de confianza. */
        int assessmentMinutes,
        /** Días que se guarda un diagnóstico hecho sin cuenta, si nadie lo reclama. */
        int assessmentLeadRetentionDays,
        /** La comisión de profe fundador, en porcentaje entero (`founder_commission_rate_bps`). */
        int founderCommissionPercent,
        /** Cuántos meses dura el beneficio de fundador, desde la primera clase pagada. */
        int founderPeriodMonths) {
}
