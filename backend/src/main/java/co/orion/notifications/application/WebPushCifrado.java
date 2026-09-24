package co.orion.notifications.application;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * El cifrado de un aviso push (RFC 8291, «aes128gcm»), con la criptografía del JDK y nada más.
 *
 * <p>El navegador entrega dos cosas al suscribirse: su clave pública P-256 ({@code p256dh}) y un
 * secreto de 16 bytes ({@code auth}). Con ellas y una clave efímera nuestra se deriva la clave y el
 * nonce del mensaje; el servicio de push (Google, Mozilla, Apple) solo ve bytes cifrados.
 *
 * <p>Clase pura, sin Spring: la prueba que importa —que un receptor real puede descifrar lo que
 * sale de aquí— corre sin levantar nada.
 */
public final class WebPushCifrado {

    private static final int REGISTRO = 4096;
    private static final SecureRandom AZAR = new SecureRandom();

    private WebPushCifrado() {
    }

    /** Los parámetros de la curva P-256, la única que usa Web Push. */
    static ECParameterSpec p256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 no disponible", e);
        }
    }

    /**
     * Cifra el cuerpo para una suscripción.
     *
     * @param p256dh la clave pública del navegador, 65 bytes sin comprimir
     * @param auth   el secreto de autenticación del navegador, 16 bytes
     */
    public static byte[] cifrar(byte[] p256dh, byte[] auth, byte[] mensaje) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair efimera = gen.generateKeyPair();
            byte[] sal = new byte[16];
            AZAR.nextBytes(sal);
            return cifrar(p256dh, auth, mensaje, efimera, sal);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el aviso", e);
        }
    }

    /** Con la clave efímera y la sal dadas: lo que permite probarlo contra un vector fijo. */
    static byte[] cifrar(byte[] p256dh, byte[] auth, byte[] mensaje, KeyPair efimera, byte[] sal)
            throws GeneralSecurityException {
        byte[] nuestra = sinComprimir((ECPublicKey) efimera.getPublic());
        byte[] secreto = ecdh(efimera.getPrivate(), clavePublica(p256dh));

        byte[] infoClave = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), p256dh, nuestra);
        byte[] ikm = hkdf(auth, secreto, infoClave, 32);
        byte[] cek = hkdf(sal, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(sal, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // Un solo registro: el mensaje, el delimitador del último registro (0x02) y nada de relleno.
        byte[] claro = Arrays.copyOf(mensaje, mensaje.length + 1);
        claro[mensaje.length] = 0x02;
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] cifrado = aes.doFinal(claro);

        ByteBuffer cuerpo = ByteBuffer.allocate(16 + 4 + 1 + nuestra.length + cifrado.length);
        cuerpo.put(sal).putInt(REGISTRO).put((byte) nuestra.length).put(nuestra).put(cifrado);
        return cuerpo.array();
    }

    static byte[] ecdh(PrivateKey propia, PublicKey ajena) throws GeneralSecurityException {
        KeyAgreement acuerdo = KeyAgreement.getInstance("ECDH");
        acuerdo.init(propia);
        acuerdo.doPhase(ajena, true);
        return acuerdo.generateSecret();
    }

    /** HKDF-SHA-256 (RFC 5869) para salidas de hasta 32 bytes: un solo bloque de expansión. */
    static byte[] hkdf(byte[] sal, byte[] ikm, byte[] info, int largo) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sal, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 0x01);
        return Arrays.copyOf(mac.doFinal(), largo);
    }

    /** Una clave pública desde sus 65 bytes sin comprimir (0x04 ‖ x ‖ y). */
    static PublicKey clavePublica(byte[] sinComprimir) throws GeneralSecurityException {
        if (sinComprimir.length != 65 || sinComprimir[0] != 0x04) {
            throw new GeneralSecurityException("Clave pública P-256 inválida");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(sinComprimir, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(sinComprimir, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    /** Una clave privada desde sus 32 bytes (el escalar d). */
    static PrivateKey clavePrivada(byte[] d) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), p256()));
    }

    static byte[] sinComprimir(ECPublicKey clave) {
        byte[] x = fijo(clave.getW().getAffineX());
        byte[] y = fijo(clave.getW().getAffineY());
        return concat(new byte[] {0x04}, x, y);
    }

    /** Un entero de la curva en exactamente 32 bytes: BigInteger a veces pone un cero de signo o se queda corto. */
    private static byte[] fijo(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length == 32) {
            return b;
        }
        byte[] r = new byte[32];
        if (b.length > 32) {
            System.arraycopy(b, b.length - 32, r, 0, 32);
        } else {
            System.arraycopy(b, 0, r, 32 - b.length, b.length);
        }
        return r;
    }

    static byte[] concat(byte[]... partes) {
        int total = 0;
        for (byte[] p : partes) {
            total += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(total);
        for (byte[] p : partes) {
            b.put(p);
        }
        return b.array();
    }
}
