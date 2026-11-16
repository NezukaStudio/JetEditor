package ru.jet.editor.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Membuat keystore PKCS12 berisi kunci RSA 2048 dan sertifikat self-signed.
 *
 * Android tidak menyediakan API publik untuk membuat sertifikat, dan proyek ini
 * tidak memakai Bouncy Castle, jadi struktur DER X.509 disusun langsung di sini
 * (RFC 5280, sha256WithRSAEncryption). Sertifikat hasilnya dicek ulang lewat
 * CertificateFactory sebelum disimpan.
 *
 * Alias selalu huruf kecil: PKCS12 tidak membedakan huruf besar/kecil.
 */
public final class KeystoreMaker {

    private KeystoreMaker() {}

    /** Data identitas sertifikat. Field kosong tidak ditulis. */
    public static final class Subject {
        public String cn, ou, o, l, st, c;
    }

    /** Buat keystore di outFile. Mengembalikan file tersebut. Panggil dari thread latar belakang. */
    public static File create(File outFile, String alias, char[] password,
                              int validityYears, Subject subject) throws Exception {
        String a = alias.toLowerCase(Locale.ROOT);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = parse(buildCertificate(kp, subject, validityYears));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(a, kp.getPrivate(), password, new Certificate[]{cert});

        File parent = outFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Folder tidak bisa dibuat: " + parent);
        }
        FileOutputStream fos = new FileOutputStream(outFile);
        try {
            ks.store(fos, password);
            fos.flush();
        } finally {
            fos.close();
        }
        return outFile;
    }

    /** Sertifikat DER yang sudah ditandatangani dengan kunci privat pasangannya. */
    static byte[] buildCertificate(KeyPair kp, Subject s, int validityYears) throws Exception {
        byte[] sigAlg = seq(oid("1.2.840.113549.1.1.11"), NULL);   // sha256WithRSAEncryption

        byte[] version = tlv(0xA0, integer(BigInteger.valueOf(2)));  // v3
        byte[] serial = integer(new BigInteger(127, new SecureRandom()).add(BigInteger.ONE));

        byte[] name = name(s);

        Calendar nb = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        nb.add(Calendar.HOUR_OF_DAY, -1);
        Calendar na = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        na.add(Calendar.YEAR, validityYears);
        byte[] validity = seq(generalizedTime(nb.getTime()), generalizedTime(na.getTime()));

        byte[] spki = kp.getPublic().getEncoded();

        byte[] tbs = seq(version, serial, sigAlg, name, validity, name, spki);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] bits = new byte[signature.length + 1];           // 0 = jumlah bit tak terpakai
        System.arraycopy(signature, 0, bits, 1, signature.length);

        return seq(tbs, sigAlg, tlv(0x03, bits));
    }

    private static X509Certificate parse(byte[] der) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    // ---------- Name ----------

    private static byte[] name(Subject s) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (notEmpty(s.c))  out.write(rdn("2.5.4.6", printable(s.c)));    // C  (PrintableString)
        if (notEmpty(s.st)) out.write(rdn("2.5.4.8", utf8(s.st)));        // ST
        if (notEmpty(s.l))  out.write(rdn("2.5.4.7", utf8(s.l)));         // L
        if (notEmpty(s.o))  out.write(rdn("2.5.4.10", utf8(s.o)));        // O
        if (notEmpty(s.ou)) out.write(rdn("2.5.4.11", utf8(s.ou)));       // OU
        out.write(rdn("2.5.4.3", utf8(notEmpty(s.cn) ? s.cn : "Android")));  // CN
        return seq(out.toByteArray());
    }

    private static boolean notEmpty(String v) {
        return v != null && v.trim().length() > 0;
    }

    private static byte[] rdn(String type, byte[] value) throws Exception {
        return tlv(0x31, seq(oid(type), value));
    }

    // ---------- DER primitives ----------

    private static final byte[] NULL = {0x05, 0x00};

    static byte[] seq(byte[]... parts) { return tlv(0x30, parts); }

    static byte[] integer(BigInteger v) { return tlv(0x02, v.toByteArray()); }

    static byte[] utf8(String s) { return tlv(0x0C, s.getBytes(StandardCharsets.UTF_8)); }

    static byte[] printable(String s) { return tlv(0x13, s.getBytes(StandardCharsets.US_ASCII)); }

    static byte[] generalizedTime(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x18, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] oid(String dotted) {
        String[] p = dotted.split("\\.");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(40 * Integer.parseInt(p[0]) + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) base128(b, Long.parseLong(p[i]));
        return tlv(0x06, b.toByteArray());
    }

    private static void base128(ByteArrayOutputStream b, long v) {
        byte[] tmp = new byte[10];
        int n = 0;
        tmp[n++] = (byte) (v & 0x7F);
        v >>= 7;
        while (v > 0) {
            tmp[n++] = (byte) ((v & 0x7F) | 0x80);
            v >>= 7;
        }
        for (int i = n - 1; i >= 0; i--) b.write(tmp[i]);
    }

    static byte[] tlv(int tag, byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(len + 6);
        out.write(tag);
        writeLength(out, len);
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int len) {
        if (len < 0x80) {
            out.write(len);
            return;
        }
        int n = 0;
        for (int t = len; t > 0; t >>= 8) n++;
        out.write(0x80 | n);
        for (int i = n - 1; i >= 0; i--) out.write((len >> (8 * i)) & 0xFF);
    }
}
