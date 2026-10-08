package net.spartanb312.grunteon.testcase.recordrename;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

public class RecordRoundTrip {
    public record Int2IntMapping(@SerializedName("name") String name,
                                 @SerializedName("ids") Map<Integer, Integer> ids) {
        public static String marker = "marker";

        public String describe() {
            return name + ids.size() + marker;
        }
    }

    // The accessor overrides both an input interface and a library interface through a bridge.
    public interface Named extends Supplier<String> {
        String name();
    }

    public record NamedMapping(@SerializedName("name") String name,
                               @SerializedName("get") String get) implements Named {
    }

    public record Ordered(@SerializedName("first") String first,
                          @SerializedName("second") String second,
                          @SerializedName("wide") long wide,
                          @SerializedName("fraction") double fraction) {
    }

    public interface Accessor {
        String a();

        default int size() {
            return a().length();
        }
    }

    // Retained component names must not collide with the renamers' first dictionary entry.
    public record Collision(@SerializedName("a") String a) implements Accessor {
        public static String marker = "marker";

        public String describe() {
            return a + marker;
        }

        public int length() {
            return a.length();
        }
    }

    public static void main(String[] args) throws Exception {
        Int2IntMapping mapping = new Int2IntMapping("mapping", Map.of(1, 2));
        roundTrip(mapping, "{\"name\":\"mapping\",\"ids\":{\"1\":2}}");
        check(mapping.describe().equals("mapping1marker"), "ordinary members");
        NamedMapping named = new NamedMapping("named", "supplied");
        roundTrip(named, "{\"name\":\"named\",\"get\":\"supplied\"}");
        Named asInput = named;
        Supplier<String> asLibrary = named;
        check(asInput.name().equals("named"), "input interface dispatch");
        check(asLibrary.get().equals("supplied"), "library interface bridge dispatch");
        roundTrip(new Ordered("left", "right", 1234567890123L, 2.5),
                "{\"first\":\"left\",\"second\":\"right\",\"wide\":1234567890123,\"fraction\":2.5}");
        Collision collision = new Collision("value");
        roundTrip(collision, "{\"a\":\"value\"}");
        check(collision.describe().equals("valuemarker") && collision.length() == 5, "collision members");
        Accessor accessor = collision;
        check(accessor.a().equals("value") && accessor.size() == 5, "retained interface group");
        System.out.println("record-round-trip-ok");
    }

    private static void roundTrip(Object value, String json) throws Exception {
        Class<?> type = value.getClass();
        check(type.isRecord(), "Record attribute");
        Gson gson = new Gson();
        // Run Gson first: the original issue fails here with NoSuchMethodException.
        Object decoded = gson.fromJson(json, type);
        check(value.equals(decoded), "Gson canonical constructor order / equals");
        check(value.hashCode() == decoded.hashCode(), "ObjectMethods hashCode");
        check(gson.toJson(value).equals(json), "SerializedName / Gson serialization");
        RecordComponent[] components = type.getRecordComponents();
        var constructor = type.getDeclaredConstructor(Arrays.stream(components)
                .map(RecordComponent::getType).toArray(Class<?>[]::new));
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            var component = components[i];
            var field = type.getDeclaredField(component.getName());
            field.setAccessible(true);
            check(field.getType() == component.getType(), "backing field type");
            check(component.getAccessor() != null, "record accessor");
            check(component.getAccessor().getName().equals(field.getName()), "accessor name");
            check(component.getAccessor().getAnnotation(SerializedName.class).value()
                    .equals(field.getAnnotation(SerializedName.class).value()), "SerializedName annotations");
            values[i] = field.get(value);
            check(values[i].equals(component.getAccessor().invoke(value)), "accessor value");
            check(constructor.getParameters()[i].getName().equals(component.getName()), "parameter order / name");
            check(value.toString().contains(component.getName() + "=" + values[i]), "ObjectMethods names");
        }
        check(value.equals(constructor.newInstance(values)), "reflective canonical constructor");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
