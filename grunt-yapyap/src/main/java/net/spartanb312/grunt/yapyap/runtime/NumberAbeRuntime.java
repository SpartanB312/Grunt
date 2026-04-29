package net.spartanb312.grunt.yapyap.runtime;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.jpbc.PairingParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator;
import it.unisa.dia.gas.plaf.jpbc.pairing.parameters.PropertiesParameters;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NumberAbeRuntime {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 16;

    private NumberAbeRuntime() {
    }

    public static String[] buildPool(String[] policyAttributes, byte[] plainBlob, int rBits, int qBits) {
        try {
            TypeACurveGenerator generator = new TypeACurveGenerator(rBits, qBits);
            PairingParameters parameters = generator.generate();
            Pairing pairing = PairingFactory.getPairing(parameters);

            Element g = pairing.getG1().newRandomElement().getImmutable();
            Element alpha = pairing.getZr().newRandomElement().getImmutable();
            Element beta = pairing.getZr().newRandomElement().getImmutable();
            Element gAlpha = g.duplicate().powZn(alpha).getImmutable();
            Element h = g.duplicate().powZn(beta).getImmutable();
            Element eggAlpha = pairing.pairing(g, g).powZn(alpha).getImmutable();

            SecretKey secretKey = keyGen(pairing, g, gAlpha, beta, policyAttributes);
            CipherText cipherText = encrypt(pairing, g, h, eggAlpha, policyAttributes);
            byte[] aesKey = dataKey(cipherText.message);
            byte[] encryptedBlob = encryptBlob(aesKey, plainBlob);

            return new String[]{
                    b64(parameters.toString().getBytes(StandardCharsets.UTF_8)),
                    b64(writeSecretKey(secretKey)),
                    b64(writeCipherText(cipherText)),
                    b64(encryptedBlob)
            };
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build CP-ABE number pool", e);
        }
    }

    /*public static Object[] init(
            Class<?> owner,
            String shapeSalt,
            String parameters,
            String secretKey,
            String cipherText,
            String encryptedBlob
    ) {
        try {
            Pairing pairing = readPairing(parameters);
            String runtimeShape = shapeAttribute(owner, shapeSalt);
            Element dataKey = decrypt(
                    pairing,
                    readSecretKey(pairing, decodeBase64(secretKey)),
                    readCipherText(pairing, decodeBase64(cipherText)),
                    runtimeShape
            );
            byte[] plainBlob = decryptBlob(dataKey(dataKey), decodeBase64(encryptedBlob));
            return readNumberBlob(plainBlob);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }*/

    public static byte[] readResource(Class<?> anchor, String name) throws Exception {
        String normalized = name.startsWith("/") ? name.substring(1) : name;
        ClassLoader loader = anchor.getClassLoader();
        InputStream stream = loader == null
                ? ClassLoader.getSystemResourceAsStream(normalized)
                : loader.getResourceAsStream(normalized);
        if (stream == null) {
            throw new IllegalStateException("Missing CP-ABE number payload resource: " + name);
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    public static String[] readPayload(byte[] payload) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
        int size = data.readInt();
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            byte[] bytes = readBytes(data);
            result[i] = new String(bytes, StandardCharsets.UTF_8);
        }
        return result;
    }

    public static String shapeAttribute(Class<?> owner, String salt) {
        Class<?> superClass = owner.getSuperclass();
        int ancestors = superClass == null ? 0 : 1;
        Class<?>[] interfaces = owner.getInterfaces();
        ancestors += interfaces.length;
        return shapeAttribute(ancestors, owner.getModifiers(), owner.isAnnotation(), owner.isEnum(), salt);
    }

    public static String shapeAttribute(
            int ancestors,
            int access,
            boolean annotation,
            boolean enumClass,
            String salt
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            digest.update((byte) 0);
            digest.update((byte) ancestors);
            digest.update((byte) 0);
            digest.update(Integer.toString(stableAccess(access, annotation, enumClass)).getBytes(StandardCharsets.UTF_8));
            return "shape:" + hex(digest.digest(), 16);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build CP-ABE shape attribute", e);
        }
    }

    private static int stableAccess(int access, boolean annotation, boolean enumClass) {
        int stable = access & (Modifier.ABSTRACT | Modifier.INTERFACE);
        if (annotation) stable |= 0x2000;
        if (enumClass) stable |= 0x4000;
        return stable;
    }

    public static Pairing readPairing(String encodedParameters) throws Exception {
        String text = new String(decodeBase64(encodedParameters), StandardCharsets.UTF_8);
        PropertiesParameters parameters = new PropertiesParameters();
        parameters.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return PairingFactory.getPairing(parameters);
    }

    private static SecretKey keyGen(Pairing pairing, Element g, Element gAlpha, Element beta, String[] attributes) {
        Element r = pairing.getZr().newRandomElement().getImmutable();
        Element betaInverse = beta.duplicate().invert().getImmutable();
        Element d = gAlpha.duplicate().mul(g.duplicate().powZn(r)).powZn(betaInverse).getImmutable();
        Map<String, Element[]> components = new HashMap<>();
        for (String attr : attributes) {
            Element rj = pairing.getZr().newRandomElement().getImmutable();
            Element dj = g.duplicate().powZn(r).mul(hashToG1(pairing, attr).powZn(rj)).getImmutable();
            Element djp = g.duplicate().powZn(rj).getImmutable();
            components.put(attr, new Element[]{dj, djp});
        }
        return new SecretKey(d, components);
    }

    private static CipherText encrypt(Pairing pairing, Element g, Element h, Element eggAlpha, String[] attributes) {
        Element s = pairing.getZr().newRandomElement().getImmutable();
        Element message = pairing.getGT().newRandomElement().getImmutable();
        Element cTilde = message.duplicate().mul(eggAlpha.duplicate().powZn(s)).getImmutable();
        Element c = h.duplicate().powZn(s).getImmutable();
        Element[] coefficients = new Element[attributes.length];
        coefficients[0] = s;
        for (int i = 1; i < coefficients.length; i++) {
            coefficients[i] = pairing.getZr().newRandomElement().getImmutable();
        }
        List<CipherLeaf> leaves = new ArrayList<>();
        for (int i = 0; i < attributes.length; i++) {
            Element share = evalPolynomial(pairing, coefficients, i + 1);
            Element cy = g.duplicate().powZn(share).getImmutable();
            Element cyp = hashToG1(pairing, attributes[i]).powZn(share).getImmutable();
            leaves.add(new CipherLeaf(attributes[i], cy, cyp));
        }
        return new CipherText(message, cTilde, c, leaves);
    }

    public static Element decrypt(Pairing pairing, SecretKey secretKey, CipherText cipherText, String runtimeShape) {
        Element accumulated = pairing.getGT().newOneElement().getImmutable();
        int n = cipherText.leaves.size();
        for (int index = 0; index < n; index++) {
            CipherLeaf leaf = cipherText.leaves.get(index);
            if (leaf.attribute.startsWith("shape:") && !leaf.attribute.equals(runtimeShape)) {
                throw new IllegalStateException("Runtime class shape does not satisfy CP-ABE policy");
            }
            Element[] component = secretKey.components.get(leaf.attribute);
            if (component == null) {
                throw new IllegalStateException("Missing CP-ABE private key attribute: " + leaf.attribute);
            }
            Element numerator = pairing.pairing(component[0], leaf.cy);
            Element denominator = pairing.pairing(component[1], leaf.cyp);
            Element share = numerator.div(denominator);
            Element lambda = lagrangeCoefficient(pairing, index + 1, n);
            accumulated = accumulated.duplicate().mul(share.powZn(lambda)).getImmutable();
        }
        Element ecd = pairing.pairing(cipherText.c, secretKey.d);
        Element mask = ecd.div(accumulated);
        return cipherText.cTilde.duplicate().div(mask).getImmutable();
    }

    private static Element evalPolynomial(Pairing pairing, Element[] coefficients, int xValue) {
        Element x = pairing.getZr().newElement(xValue).getImmutable();
        Element result = coefficients[coefficients.length - 1].duplicate();
        for (int i = coefficients.length - 2; i >= 0; i--) {
            result.mul(x).add(coefficients[i]);
        }
        return result.getImmutable();
    }

    private static Element lagrangeCoefficient(Pairing pairing, int i, int n) {
        Element numerator = pairing.getZr().newOneElement();
        Element denominator = pairing.getZr().newOneElement();
        for (int j = 1; j <= n; j++) {
            if (j == i) continue;
            numerator.mul(pairing.getZr().newElement(-j));
            denominator.mul(pairing.getZr().newElement(i - j));
        }
        return numerator.div(denominator).getImmutable();
    }

    private static Element hashToG1(Pairing pairing, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return pairing.getG1().newElement().setFromHash(bytes, 0, bytes.length).getImmutable();
    }

    private static byte[] writeSecretKey(SecretKey secretKey) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        writeElement(data, secretKey.d);
        data.writeInt(secretKey.components.size());
        for (Map.Entry<String, Element[]> entry : secretKey.components.entrySet()) {
            data.writeUTF(entry.getKey());
            writeElement(data, entry.getValue()[0]);
            writeElement(data, entry.getValue()[1]);
        }
        return output.toByteArray();
    }

    public static SecretKey readSecretKey(Pairing pairing, byte[] bytes) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        Element d = readG1(data, pairing);
        int size = data.readInt();
        Map<String, Element[]> components = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String attr = data.readUTF();
            components.put(attr, new Element[]{readG1(data, pairing), readG1(data, pairing)});
        }
        return new SecretKey(d, components);
    }

    private static byte[] writeCipherText(CipherText cipherText) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        writeElement(data, cipherText.cTilde);
        writeElement(data, cipherText.c);
        data.writeInt(cipherText.leaves.size());
        for (CipherLeaf leaf : cipherText.leaves) {
            data.writeUTF(leaf.attribute);
            writeElement(data, leaf.cy);
            writeElement(data, leaf.cyp);
        }
        return output.toByteArray();
    }

    public static CipherText readCipherText(Pairing pairing, byte[] bytes) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        Element cTilde = readGT(data, pairing);
        Element c = readG1(data, pairing);
        int size = data.readInt();
        List<CipherLeaf> leaves = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            leaves.add(new CipherLeaf(data.readUTF(), readG1(data, pairing), readG1(data, pairing)));
        }
        return new CipherText(null, cTilde, c, leaves);
    }

    private static void writeElement(DataOutputStream data, Element element) throws Exception {
        byte[] bytes = element.toBytes();
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static Element readG1(DataInputStream data, Pairing pairing) throws Exception {
        byte[] bytes = readBytes(data);
        return pairing.getG1().newElementFromBytes(bytes).getImmutable();
    }

    private static Element readGT(DataInputStream data, Pairing pairing) throws Exception {
        byte[] bytes = readBytes(data);
        return pairing.getGT().newElementFromBytes(bytes).getImmutable();
    }

    private static byte[] readBytes(DataInputStream data) throws Exception {
        int length = data.readInt();
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return bytes;
    }

    private static byte[] encryptBlob(byte[] key, byte[] plain) throws Exception {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        SECURE_RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(plain);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(nonce.length);
        data.write(nonce);
        data.writeInt(encrypted.length);
        data.write(encrypted);
        return output.toByteArray();
    }

    public static byte[] decryptBlob(byte[] key, byte[] encryptedBlob) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(encryptedBlob));
        byte[] nonce = readBytes(data);
        byte[] encrypted = readBytes(data);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(encrypted);
    }

    public static byte[] dataKey(Element element) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(element.toBytes());
        return Arrays.copyOf(digest, AES_KEY_BYTES);
    }

    public static Object[] readNumberBlob(byte[] plainBlob) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(plainBlob));
        int[] ints = new int[data.readInt()];
        for (int i = 0; i < ints.length; i++) ints[i] = data.readInt();
        long[] longs = new long[data.readInt()];
        for (int i = 0; i < longs.length; i++) longs[i] = data.readLong();
        float[] floats = new float[data.readInt()];
        for (int i = 0; i < floats.length; i++) floats[i] = Float.intBitsToFloat(data.readInt());
        double[] doubles = new double[data.readInt()];
        for (int i = 0; i < doubles.length; i++) doubles[i] = Double.longBitsToDouble(data.readLong());
        return new Object[]{ints, longs, floats, doubles};
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] b64(String value) {
        return Base64.getDecoder().decode(value);
    }

    public static byte[] decodeBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String hex(byte[] bytes, int limit) {
        StringBuilder builder = new StringBuilder(limit * 2);
        for (int i = 0; i < bytes.length && i < limit; i++) {
            int value = bytes[i] & 0xFF;
            if (value < 16) builder.append('0');
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    public static final class SecretKey {
        private final Element d;
        private final Map<String, Element[]> components;

        private SecretKey(Element d, Map<String, Element[]> components) {
            this.d = d;
            this.components = components;
        }
    }

    public static final class CipherText {
        private final Element message;
        private final Element cTilde;
        private final Element c;
        private final List<CipherLeaf> leaves;

        private CipherText(Element message, Element cTilde, Element c, List<CipherLeaf> leaves) {
            this.message = message;
            this.cTilde = cTilde;
            this.c = c;
            this.leaves = leaves;
        }
    }

    public static final class CipherLeaf {
        private final String attribute;
        private final Element cy;
        private final Element cyp;

        private CipherLeaf(String attribute, Element cy, Element cyp) {
            this.attribute = attribute;
            this.cy = cy;
            this.cyp = cyp;
        }
    }
}
