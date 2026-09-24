-- La práctica, más variada (24/09/2026, pedido de Pardo): cinco ejercicios por set, cada uno de un
-- tipo distinto, de cinco categorías —palabras, frases, conversación, escucha y tu turno—. Llegan
-- cinco tipos nuevos: cazar el error, armar la frase, responder en el chat, escuchar y elegir, y
-- escuchar y escribir.
ALTER TABLE practice_items DROP CONSTRAINT practice_items_item_type_check;
ALTER TABLE practice_items ADD CONSTRAINT practice_items_item_type_check
    CHECK (item_type IN ('FILL_BLANK', 'FIX_SENTENCE', 'MATCH_MEANING', 'ORDER_DIALOGUE', 'WRITE_SENTENCE',
                         'SPOT_ERROR', 'BUILD_SENTENCE', 'CHOOSE_REPLY', 'LISTEN_CHOOSE', 'DICTATION'));

-- Los de escucha suenan con la voz del dispositivo, y hay dispositivos sin voz en inglés. Ahí el
-- ejercicio se salta sin contar como error: saltado es cerrado, pero ni acierto ni fallo.
ALTER TABLE practice_items ADD COLUMN skipped_at TIMESTAMPTZ;

-- Cinco ejercicios, uno por categoría. Solo si el ajuste seguía en el valor de la V49: si alguien
-- ya lo había movido desde Ajustes, esa decisión manda.
UPDATE platform_settings SET value = '5' WHERE key = 'practice_items_per_set' AND value = '4';
