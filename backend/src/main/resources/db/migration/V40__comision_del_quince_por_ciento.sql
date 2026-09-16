-- La comisión de Orión baja del 20 % al 15 %.
--
-- Decisión de Pardo (14/09/2026), al detectar que el documento de Sofía sobre el portal del
-- profesor daba por hecho un 15 % que nunca existió en el producto. Entre corregir el documento y
-- corregir el producto, eligió el producto.
--
-- Lo que esto NO toca: las reservas ya hechas. `payments.commission_rate_bps` se congela en cada
-- reserva y es `updatable = false`, así que toda clase ya pagada se liquida con el 20 % que regía
-- cuando se reservó. Solo cambia lo que se cobre de aquí en adelante, que es exactamente lo que
-- debe pasar — cambiar la comisión de una clase ya vendida sería cambiar el trato a posteriori.
--
-- Va acompañada de la versión 1.1 de los Términos: el texto legal publicado decía «20 %», y dejar
-- el dato y el contrato en desacuerdo era publicar una cláusula falsa.
UPDATE platform_settings
   SET value = '1500'
 WHERE key = 'commission_rate_bps'
   AND value = '2000';
