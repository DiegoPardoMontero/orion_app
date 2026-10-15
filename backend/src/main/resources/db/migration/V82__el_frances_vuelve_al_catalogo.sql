-- El francés vuelve al catálogo.
--
-- Decisión de Pardo y Sofía (14/10/2026): el primer lanzamiento es con inglés y francés. La V42 lo
-- apagó sin desmontarlo, así que reabrirlo es exactamente lo que esa migración anticipó: volver a
-- poner la bandera. La oferta de francés que la V42 borró no se restaura (los profes la vuelven a
-- marcar en Mi perfil); el histórico de clases nunca se tocó. El español sigue apagado.
UPDATE languages
   SET is_active = true
 WHERE code = 'FR';
