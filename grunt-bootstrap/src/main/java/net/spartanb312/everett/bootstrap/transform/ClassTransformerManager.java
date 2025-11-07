package net.spartanb312.everett.bootstrap.transform;

import java.util.ArrayList;
import java.util.List;

public class ClassTransformerManager {

    public List<IClassTransformer> transformers = new ArrayList<>();

    public byte[] transform(String name, byte[] bytes) {
        byte[] bytes1 = bytes;
        for (IClassTransformer transformer : transformers) {
            bytes1 = transformer.transform(name, bytes1);
        }
        return bytes1;
    }

}
