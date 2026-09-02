-- Decisión Q1 (Pardo, 01/09/2026): comisión para todos, no solo los nuevos. Los profesores
-- actuales pasan a COMMISSION. Como "un profesor sin tarifa no puede publicarse bajo COMMISSION",
-- quedan OCULTOS del buscador (regla en el servicio/consulta) hasta que Sofía fije su precio con
-- cada uno — nunca se muestran sin precio. No se toca is_published: reaparecen al fijar la tarifa.
UPDATE professor_profiles
SET compensation_model = 'COMMISSION'
WHERE compensation_model = 'FIXED_FEE';

-- Todo profesor existente enseña inglés (el negocio nació como academia de inglés): se les siembra
-- una fila de idioma EN para que tengan taxonomía desde el primer día. Sin niveles ni objetivos:
-- eso lo completa cada profesor al editar su perfil.
INSERT INTO professor_languages (professor_id, language_code, is_native)
SELECT user_id, 'EN', false
FROM professor_profiles
ON CONFLICT (professor_id, language_code) DO NOTHING;
