/* SPDX-License-Identifier: PolyForm-Strict-1.0.0 */
package net.spartanb312.grunt.yapyap.runtime;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.jpbc.PairingParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeAPairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.a1.TypeA1Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.d.TypeDPairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.e.TypeEPairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.f.TypeFPairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.g.TypeGPairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.parameters.PropertiesParameters;
import net.spartanb312.grunt.yapyap.annotation.DisableNumberABE;
import net.spartanb312.grunt.yapyap.annotation.DisableStringABE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInput;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Classloader-local, bounded caches of PUBLIC immutable data only. Never keys, pools, elements or pairings. */
@DisableNumberABE
@DisableStringABE
final class AbeRuntimeSupport {
    static final int PARAMETER_CACHE_SIZE = 16;
    static final int MAX_ENCODED_PARAMETER_CHARS = 32768;
    static final int MAX_CACHED_LEAVES = 64;
    private static final Map<String, PairingParameters> PARAMETERS = new LinkedHashMap<>();
    private static final Map<Integer, List<BigInteger>> COEFFICIENTS = new LinkedHashMap<>();

    private AbeRuntimeSupport() { }

    static PairingParameters freezeParameters(PairingParameters parameters) {
        return new FrozenParameters(parameters.toString().getBytes(StandardCharsets.UTF_8));
    }

    static PairingParameters parameters(String encoded) {
        // Oversized input is still accepted, but cannot turn a bounded-entry cache into unbounded retention.
        if (encoded.length() > MAX_ENCODED_PARAMETER_CHARS) {
            return new FrozenParameters(Base64.getDecoder().decode(encoded));
        }
        synchronized (PARAMETERS) {
            PairingParameters existing = PARAMETERS.get(encoded);
            if (existing != null) return existing;
            PairingParameters parsed = new FrozenParameters(Base64.getDecoder().decode(encoded));
            if (PARAMETERS.size() == PARAMETER_CACHE_SIZE) {
                PARAMETERS.remove(PARAMETERS.keySet().iterator().next());
            }
            PARAMETERS.put(encoded, parsed);
            return parsed;
        }
    }

    static Pairing freshPairing(PairingParameters parameters, boolean useNative) {
        // PairingFactory.getPairing caches mutable Pairings by default, and its backend flag is global.
        // Construct independently instead, without changing any process-global JPBC configuration.
        if (useNative) {
            try {
                Class<?> factory = Class.forName("it.unisa.dia.gas.plaf.jpbc.pbc.PBCPairingFactory");
                Pairing nativePairing = (Pairing) factory.getMethod("getPairing", PairingParameters.class)
                    .invoke(null, parameters);
                if (nativePairing != null) return nativePairing;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                // Same optional native -> Java fallback; no parameter/curve downgrade.
            }
        }
        SecureRandom random = new SecureRandom();
        String type = parameters.getString("type");
        switch (type.toLowerCase(Locale.ROOT)) {
            case "a": return new TypeAPairing(random, parameters);
            case "a1": return new TypeA1Pairing(random, parameters);
            case "d": return new TypeDPairing(random, parameters);
            case "e": return new TypeEPairing(random, parameters);
            case "f": return new TypeFPairing(random, parameters);
            case "g": return new TypeGPairing(random, parameters);
            case "ctl13":
                try {
                    return (Pairing) Class.forName("it.unisa.dia.gas.plaf.jpbc.mm.clt13.pairing.CTL13PairingFactory")
                        .getMethod("getPairing", SecureRandom.class, PairingParameters.class)
                        .invoke(null, random, parameters);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException("Cannot create pairing instance. Type = " + type, e);
                }
            default: throw new IllegalArgumentException("Type not supported. Type = " + type);
        }
    }

    static Element lagrangeCoefficient(Pairing pairing, int i, int n) {
        // At x=0 and evaluation points 1..n: lambda_i = (-1)^(i-1) * binomial(n, i).
        // The cached integers are field-independent; every returned Element belongs to THIS pairing.
        if (n > 0 && n <= MAX_CACHED_LEAVES && BigInteger.valueOf(n).compareTo(pairing.getZr().getOrder()) < 0) {
            return pairing.getZr().newElement(coefficients(n).get(i - 1)).getImmutable();
        }
        // Retain the original field division/failure behaviour outside the bounded fast path.
        Element numerator = pairing.getZr().newOneElement();
        Element denominator = pairing.getZr().newOneElement();
        for (int j = 1; j <= n; j++) {
            if (j == i) continue;
            numerator.mul(pairing.getZr().newElement(-j));
            denominator.mul(pairing.getZr().newElement(i - j));
        }
        return numerator.div(denominator).getImmutable();
    }

    static List<BigInteger> coefficients(int n) {
        if (n < 1 || n > MAX_CACHED_LEAVES) throw new IllegalArgumentException("Uncacheable leaf count");
        synchronized (COEFFICIENTS) {
            List<BigInteger> existing = COEFFICIENTS.get(n);
            if (existing != null) return existing;
            List<BigInteger> values = new ArrayList<>(n);
            BigInteger choose = BigInteger.ONE;
            for (int i = 1; i <= n; i++) {
                choose = choose.multiply(BigInteger.valueOf(n - i + 1)).divide(BigInteger.valueOf(i));
                values.add((i & 1) == 1 ? choose : choose.negate());
            }
            List<BigInteger> result = Collections.unmodifiableList(values);
            COEFFICIENTS.put(n, result); // at most 64 lists / 2080 small immutable integers
            return result;
        }
    }

    /** JPBC's properties implementation is mutable; publish a sealed snapshot with decoded numeric values. */
    private static final class FrozenParameters extends PropertiesParameters {
        private final Map<String, BigInteger> integers;

        FrozenParameters(byte[] bytes) {
            super.load(new ByteArrayInputStream(bytes));
            Map<String, BigInteger> decoded = new HashMap<>();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                try {
                    decoded.put(entry.getKey(), new BigInteger(entry.getValue()));
                } catch (NumberFormatException notAnInteger) {
                    // For instance "type a"; retain the base implementation's error on numeric access.
                }
            }
            integers = Collections.unmodifiableMap(decoded);
        }

        // PairingParameters is Serializable. Export a detached ordinary snapshot rather than
        // asking Externalizable to instantiate this private immutable implementation.
        private Object writeReplace() {
            return new PropertiesParameters().load(new ByteArrayInputStream(toString().getBytes(StandardCharsets.UTF_8)));
        }

        @Override public BigInteger getBigInteger(String key) {
            BigInteger value = integers.get(key);
            return value != null ? value : super.getBigInteger(key);
        }
        @Override public BigInteger getBigInteger(String key, BigInteger defaultValue) {
            return containsKey(key) ? getBigInteger(key) : defaultValue;
        }
        @Override public void put(String key, String value) { throw new UnsupportedOperationException("Immutable parameters"); }
        @Override public void putBytes(String key, byte[] value) { throw new UnsupportedOperationException("Immutable parameters"); }
        @Override public String remove(String key) { throw new UnsupportedOperationException("Immutable parameters"); }
        @Override public PropertiesParameters load(InputStream input) { throw new UnsupportedOperationException("Immutable parameters"); }
        @Override public PropertiesParameters load(String path) { throw new UnsupportedOperationException("Immutable parameters"); }
        @Override public void readExternal(ObjectInput input) { throw new UnsupportedOperationException("Immutable parameters"); }
    }
}
