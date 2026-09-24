package co.orion.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * Lo único que importa del cifrado: que un navegador real lo descifre. Aquí el «navegador» es el
 * lado receptor del RFC 8291 escrito aparte, con su propia clave privada, y la firma VAPID se
 * verifica con la clave pública que viaja en la cabecera.
 */
class WebPushCifradoTest {

    private static KeyPair par() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    @Test
    void unNavegadorDescifraLoQueSale() throws Exception {
        KeyPair navegador = par();
        byte[] p256dh = WebPushCifrado.sinComprimir((ECPublicKey) navegador.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] mensaje = "{\"title\":\"En una hora: tu clase con María ✦\"}".getBytes(StandardCharsets.UTF_8);

        byte[] cuerpo = WebPushCifrado.cifrar(p256dh, auth, mensaje);

        // El lado del navegador: cabecera, acuerdo con su privada, las mismas derivaciones, AES-GCM.
        ByteBuffer b = ByteBuffer.wrap(cuerpo);
        byte[] sal = new byte[16];
        b.get(sal);
        assertThat(b.getInt()).isEqualTo(4096);
        byte[] suya = new byte[b.get()];
        b.get(suya);
        byte[] cifrado = new byte[b.remaining()];
        b.get(cifrado);

        byte[] secreto = WebPushCifrado.ecdh(navegador.getPrivate(), WebPushCifrado.clavePublica(suya));
        byte[] ikm = WebPushCifrado.hkdf(auth, secreto,
                WebPushCifrado.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), p256dh, suya), 32);
        byte[] cek = WebPushCifrado.hkdf(sal, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCifrado.hkdf(sal, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] claro = aes.doFinal(cifrado);

        assertThat(claro[claro.length - 1]).isEqualTo((byte) 0x02);
        assertThat(Arrays.copyOf(claro, claro.length - 1)).isEqualTo(mensaje);
    }

    @Test
    void laFirmaVapidSeVerificaConLaClaveDeLaCabecera() throws Exception {
        KeyPair orion = par();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String publica = b64.encodeToString(WebPushCifrado.sinComprimir((ECPublicKey) orion.getPublic()));
        byte[] d = ((ECPrivateKey) orion.getPrivate()).getS().toByteArray();
        byte[] d32 = new byte[32];
        System.arraycopy(d, Math.max(0, d.length - 32), d32, Math.max(0, 32 - d.length), Math.min(32, d.length));
        Vapid vapid = new Vapid(publica, b64.encodeToString(d32), "mailto:pardo@orion.test",
                Clock.fixed(Instant.parse("2026-09-24T20:00:00Z"), ZoneOffset.UTC));

        String cabecera = vapid.autorizacion("https://fcm.googleapis.com");
        assertThat(cabecera).startsWith("vapid t=").endsWith(", k=" + publica);

        String jwt = cabecera.substring("vapid t=".length(), cabecera.indexOf(", k="));
        String[] partes = jwt.split("\\.");
        String reclamos = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        assertThat(reclamos).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"sub\":\"mailto:pardo@orion.test\"");

        Signature verificar = Signature.getInstance("SHA256withECDSAinP1363Format");
        verificar.initVerify(WebPushCifrado.clavePublica(Base64.getUrlDecoder().decode(publica)));
        verificar.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verificar.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
    }

    @Test
    void sinClavesNoHayAvisos() {
        assertThat(new Vapid("", "", "", Clock.systemUTC()).disponible()).isFalse();
        assertThat(new Vapid("abc", "no-es-una-clave!!", "", Clock.systemUTC()).disponible()).isFalse();
    }
}
