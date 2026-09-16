-- Retira la versión 1.1 de los Términos, que nunca debió existir.
--
-- Se publicó el 14/09/2026 para bajar la comisión del 20 % al 15 % en el texto legal. Fue la
-- decisión equivocada: el porcentaje no es una cláusula, es un dato que se cambia desde Ajustes, y
-- congelarlo por versión obligaba a publicar Términos nuevos cada vez que Pardo moviera un número.
--
-- Ahora la cláusula dice «{{comision}}» y el marcador se rellena en cada lectura desde
-- `platform_settings`, igual que ya se hacía con el domicilio del responsable. Lo que se congela
-- por versión son las cláusulas; los números que citan, no. Lo mismo se aplicó a la duración de la
-- clase, la retención del pago, las ventanas de cancelación y los plazos de reclamo — y de paso
-- salió que el texto decía «60 minutos» cuando las clases duran 55 desde hace dos bloques.
--
-- Se puede borrar sin más porque no hay usuarios reales: ninguna aceptación apunta a la 1.1. La
-- constraint de `agreement_acceptances` protegería la fila si la hubiera, y entonces esto fallaría
-- en vez de dejar una aceptación huérfana, que es exactamente lo que debe pasar.
DELETE FROM legal_documents
 WHERE code = 'TERMS'
   AND version = '1.1';
