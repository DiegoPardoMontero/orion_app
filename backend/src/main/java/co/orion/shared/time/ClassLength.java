package co.orion.shared.time;

import java.time.Duration;

/**
 * Cuánto dura una clase de Orión.
 *
 * <p>Vive en {@code shared} y no en {@code scheduling} porque no solo lo usa quien calcula cupos:
 * lo dicen también los Términos, la página pública y las ayudas de los formularios. Que el número
 * tenga un solo sitio es la regla de Pardo llevada al código — y {@code catalog} no puede importar
 * {@code scheduling} para preguntárselo, porque {@code scheduling} ya lee ajustes de
 * {@code catalog} y eso sería un ciclo.
 *
 * <p><strong>Cincuenta y cinco minutos y no sesenta.</strong> Los cinco que sobran de cada hora son
 * el respiro del profesor entre una clase y la siguiente, y salen solos de que los cupos sigan
 * empezando en punto. Sin ese margen, dos clases seguidas se pisan por diseño.
 *
 * <p>No es un ajuste de {@code platform_settings} a propósito: cambiarlo no es cambiar una promesa
 * comercial sino la aritmética de los cupos ya publicados, y eso se hace con una migración y con la
 * cabeza fría, no desde una pantalla.
 */
public final class ClassLength {

    public static final Duration DURATION = Duration.ofMinutes(55);

    public static final int MINUTES = (int) DURATION.toMinutes();

    private ClassLength() {
    }
}
