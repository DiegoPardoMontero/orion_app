Implementa la pantalla **Invitación personal al profesor** de Orión según este paquete.

1. Lee `README.md` completo. Ahí están los tokens, el layout, el copy final, los estados y la checklist.
2. Usa como referencia visual `capturas/invitacion-390.png` e `capturas/invitacion-1280.png`. El diseño vivo está en `disenos/Profesor-Invitacion.dc.html`: es solo referencia, no lo copies como código.
3. Antes de crear componentes, revisa el código existente. Reutiliza lo que ya exista: el botón en píldora, el logo, los tokens de Tailwind v4 y el flujo de registro de profesor. Si falta algún token, agrégalo en `@theme`.
4. Crea la ruta pública `/invitacion/[token]`. Resuelve el token del lado del servidor y maneja los estados `vigente`, `vencida` y `usada`. Un token inexistente se trata como `vencida`.
5. Importa Rigel desde `assets/rigel-saluda.svg`. No lo redibujes. Recuerda que Rigel y Meissa nunca van en la misma pantalla.
6. «Aceptar la invitación» lleva al registro con el correo prellenado y bloqueado. El token se consume al crear la cuenta.
7. El copy va exactamente como está en el README, en español de Colombia.
8. Al terminar, compara con las capturas a 390 y a 1280. No debe quedar ningún texto partido. Después marca la checklist.

Si algo del README choca con el código existente, pregúntame antes de decidir.
