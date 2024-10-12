package net.spartanb312.genesis.java;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static <T> List<T> add(List<T> list, T element) {
        List<T> newList = list == null ? new ArrayList<>(1) : list;
        newList.add(element);
        return newList;
    }

    public static LabelNode asNode(Label label) {
        if (!(label.info instanceof LabelNode)) {
            label.info = new LabelNode();
        }
        return (LabelNode) label.info;
    }

    public static AbstractInsnNode toInsnNode(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(value + 0x3);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, value);
        else return new LdcInsnNode(value);
    }

    public static AbstractInsnNode toInsnNode(long value) {
        if (value == 0L) return new InsnNode(Opcodes.LCONST_0);
        else if (value == 1L) return new InsnNode(Opcodes.LCONST_1);
        else return new LdcInsnNode(value);
    }

    public static AbstractInsnNode toInsnNode(float value) {
        if (value == 0f) return new InsnNode(Opcodes.FCONST_0);
        else if (value == 1f) return new InsnNode(Opcodes.FCONST_1);
        else if (value == 2f) return new InsnNode(Opcodes.FCONST_2);
        else return new LdcInsnNode(value);
    }

    public static AbstractInsnNode toInsnNode(double value) {
        if (value == 0.0) return new InsnNode(Opcodes.DCONST_0);
        else if (value == 1.0) return new InsnNode(Opcodes.DCONST_1);
        else return new LdcInsnNode(value);
    }

}
