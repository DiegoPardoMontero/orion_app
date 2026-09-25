-- El profe fundador (brief del profe fundador, con las decisiones de Pardo del 25/09/2026).
--
-- La comisión de Orión vuelve a ser 20 %, y los profes fundadores pagan 15 % durante sus primeros
-- 3 meses de clases, contados desde la primera clase pagada. Es un beneficio con fecha de fin que el
-- profe conoce desde el primer día: al pasar a 20 % lo vive como el final de un beneficio, no como
-- una subida.

-- La promesa se copia al otorgarla (porcentaje y meses) y el conteo lo arranca la primera clase
-- pagada, con un UPDATE condicional: la base decide, sin locks.
ALTER TABLE professor_profiles
    ADD COLUMN founder_rate_bps           INTEGER CHECK (founder_rate_bps BETWEEN 0 AND 10000),
    ADD COLUMN founder_period_months      SMALLINT CHECK (founder_period_months > 0),
    ADD COLUMN founder_granted_at         TIMESTAMPTZ,
    ADD COLUMN founder_started_at         TIMESTAMPTZ,
    ADD COLUMN founder_until              TIMESTAMPTZ,
    ADD COLUMN founder_expiry_notified_at TIMESTAMPTZ;

ALTER TABLE professor_profiles
    ADD CONSTRAINT professor_profiles_founder_promesa_ck CHECK (
        (founder_rate_bps IS NULL AND founder_period_months IS NULL AND founder_granted_at IS NULL)
        OR (founder_rate_bps IS NOT NULL AND founder_period_months IS NOT NULL AND founder_granted_at IS NOT NULL)),
    ADD CONSTRAINT professor_profiles_founder_conteo_ck CHECK (
        (founder_started_at IS NULL) = (founder_until IS NULL)),
    ADD CONSTRAINT professor_profiles_founder_empieza_ck CHECK (
        founder_started_at IS NULL OR founder_rate_bps IS NOT NULL);

-- Los ajustes del beneficio: solo se leen al otorgarlo.
INSERT INTO platform_settings (key, value) VALUES
    ('founder_commission_rate_bps', '1500'),
    ('founder_period_months',       '3')
ON CONFLICT (key) DO NOTHING;

-- La base vuelve a 20 % (la V40 la había bajado a 15 % para todos). Solo cambia lo que se reserve de
-- aquí en adelante: cada pago congeló su comisión en payments.commission_rate_bps.
UPDATE platform_settings
   SET value = '2000'
 WHERE key = 'commission_rate_bps'
   AND value = '1500';

-- Todos los profes que ya están en Orión son fundadores (decisión de Pardo): nadie pasa de 15 % a
-- 20 % de golpe. Sus 3 meses cuentan desde su primera clase pagada, si ya la tuvieron.
UPDATE professor_profiles p
   SET founder_rate_bps = 1500,
       founder_period_months = 3,
       founder_granted_at = now()
  FROM users u
 WHERE u.id = p.user_id
   AND u.role = 'PROFESSOR';

-- El mismo cálculo que CommissionPolicy.founderUntil: la fecha de inicio en Bogotá más tres meses
-- calendario, a las 00:00 de Bogotá.
UPDATE professor_profiles p
   SET founder_started_at = primera.pagada,
       founder_until = (((primera.pagada AT TIME ZONE 'America/Bogota')::date + INTERVAL '3 months')::timestamp)
                       AT TIME ZONE 'America/Bogota'
  FROM (SELECT professor_id, min(paid_at) AS pagada
          FROM payments
         WHERE paid_at IS NOT NULL
         GROUP BY professor_id) primera
 WHERE primera.professor_id = p.user_id
   AND p.founder_rate_bps IS NOT NULL;
