package co.orion.teaching.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import co.orion.teaching.domain.LessonNote;

/**
 * El borrador sin IA, para local y tests (brief, paso A2: ninguna prueba depende de la red).
 * Determinista: las notas van enteras a «lo que trabajaron» y cada término entre comillas se vuelve
 * una palabra nueva. Se enciende con el mismo interruptor que la voz del diagnóstico: sin IA real,
 * tampoco hay IA en las actas.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class DeterministicLessonNoteDrafter implements LessonNoteDrafter {

    private static final Pattern ENTRE_COMILLAS = Pattern.compile("[\"'“‘«]([^\"'”’»]{1,60})[\"'”’»]");

    @Override
    public Optional<Borrador> redactar(Contexto contexto) {
        String notas = contexto.notas().trim();
        List<Palabra> palabras = new ArrayList<>();
        Matcher m = ENTRE_COMILLAS.matcher(notas);
        while (m.find() && palabras.size() < 12) {
            palabras.add(new Palabra(m.group(1).trim(), null));
        }
        String trabajado = notas.length() > LessonNote.MAX_SECCION
                ? notas.substring(0, LessonNote.MAX_SECCION) : notas;
        return Optional.of(new Borrador(trabajado, "", "", palabras, "determinista-v1"));
    }
}
