package net.spartanb312.genesis.java.builder;

import net.spartanb312.genesis.java.Utils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Collectors;

import static net.spartanb312.genesis.java.Utils.asNode;
import static net.spartanb312.genesis.java.Utils.toInsnNode;

public abstract class InsnListBuilder {

    private final InsnList insnList = new InsnList();
    private final Map<Object, Label> labelRegistry = new HashMap<>();

    protected abstract void instructions();

    public InsnList toInsnList() {
        labelRegistry.clear();
        insnList.clear();
        instructions();
        return insnList;
    }

    public Label L(Object key) {
        Label existed = labelRegistry.getOrDefault(key, null);
        if (existed != null) return existed;
        else {
            Label created = new Label();
            labelRegistry.put(key, created);
            return created;
        }
    }

    public void INSN(int opcode) {
        insnList.add(new InsnNode(opcode));
    }

    /**
     * Arithmetic
     */
    public void IADD() {
        INSN(Opcodes.IADD);
    }

    public void ISUB() {
        INSN(Opcodes.ISUB);
    }

    public void IMUL() {
        INSN(Opcodes.IMUL);
    }

    public void IDIV() {
        INSN(Opcodes.IDIV);
    }

    public void IREM() {
        INSN(Opcodes.IREM);
    }

    public void INEG() {
        INSN(Opcodes.INEG);
    }

    public void ISHL() {
        INSN(Opcodes.ISHL);
    }

    public void ISHR() {
        INSN(Opcodes.ISHR);
    }

    public void IUSHR() {
        INSN(Opcodes.IUSHR);
    }

    public void IAND() {
        INSN(Opcodes.IAND);
    }

    public void IOR() {
        INSN(Opcodes.IOR);
    }

    public void IXOR() {
        INSN(Opcodes.IXOR);
    }

    public void LADD() {
        INSN(Opcodes.LADD);
    }

    public void LSUB() {
        INSN(Opcodes.LSUB);
    }

    public void LMUL() {
        INSN(Opcodes.LMUL);
    }

    public void LDIV() {
        INSN(Opcodes.LDIV);
    }

    public void LREM() {
        INSN(Opcodes.LREM);
    }

    public void LNEG() {
        INSN(Opcodes.LNEG);
    }

    public void LSHL() {
        INSN(Opcodes.LSHL);
    }

    public void LSHR() {
        INSN(Opcodes.LSHR);
    }

    public void LUSHR() {
        INSN(Opcodes.LUSHR);
    }

    public void LAND() {
        INSN(Opcodes.LAND);
    }

    public void LOR() {
        INSN(Opcodes.LOR);
    }

    public void LXOR() {
        INSN(Opcodes.LXOR);
    }

    public void FADD() {
        INSN(Opcodes.FADD);
    }

    public void FSUB() {
        INSN(Opcodes.FSUB);
    }

    public void FMUL() {
        INSN(Opcodes.FMUL);
    }

    public void FDIV() {
        INSN(Opcodes.FDIV);
    }

    public void FREM() {
        INSN(Opcodes.FREM);
    }

    public void FNEG() {
        INSN(Opcodes.FNEG);
    }

    public void DADD() {
        INSN(Opcodes.DADD);
    }

    public void DSUB() {
        INSN(Opcodes.DSUB);
    }

    public void DMUL() {
        INSN(Opcodes.DMUL);
    }

    public void DDIV() {
        INSN(Opcodes.DDIV);
    }

    public void DREM() {
        INSN(Opcodes.DREM);
    }

    public void DNEG() {
        INSN(Opcodes.DNEG);
    }

    public void IINC(int slot) {
        insnList.add(new IincInsnNode(slot, 1));
    }

    public void IINC(int slot, int amount) {
        insnList.add(new IincInsnNode(slot, amount));
    }

    public void LCMP() {
        INSN(Opcodes.LCMP);
    }

    public void FCMPL() {
        INSN(Opcodes.FCMPL);
    }

    public void FCMPG() {
        INSN(Opcodes.FCMPG);
    }

    public void DCMPL() {
        INSN(Opcodes.DCMPL);
    }

    public void DCMPG() {
        INSN(Opcodes.DCMPG);
    }

    public void I2S() {
        INSN(Opcodes.I2S);
    }

    public void I2C() {
        INSN(Opcodes.I2C);
    }

    public void I2B() {
        INSN(Opcodes.I2B);
    }

    public void I2L() {
        INSN(Opcodes.I2L);
    }

    public void I2F() {
        INSN(Opcodes.I2F);
    }

    public void I2D() {
        INSN(Opcodes.I2D);
    }

    public void L2I() {
        INSN(Opcodes.L2I);
    }

    public void L2F() {
        INSN(Opcodes.L2F);
    }

    public void L2D() {
        INSN(Opcodes.L2D);
    }

    public void F2I() {
        INSN(Opcodes.F2I);
    }

    public void F2L() {
        INSN(Opcodes.F2L);
    }

    public void F2D() {
        INSN(Opcodes.F2D);
    }

    public void D2I() {
        INSN(Opcodes.D2I);
    }

    public void D2L() {
        INSN(Opcodes.D2L);
    }

    public void D2F() {
        INSN(Opcodes.D2F);
    }

    /**
     * Array
     */
    public void IALOAD() {
        INSN(Opcodes.IALOAD);
    }

    public void SALOAD() {
        INSN(Opcodes.SALOAD);
    }

    public void BALOAD() {
        INSN(Opcodes.BALOAD);
    }

    public void CALOAD() {
        INSN(Opcodes.CALOAD);
    }

    public void LALOAD() {
        INSN(Opcodes.LALOAD);
    }

    public void FALOAD() {
        INSN(Opcodes.FALOAD);
    }

    public void DALOAD() {
        INSN(Opcodes.DALOAD);
    }

    public void AALOAD() {
        INSN(Opcodes.AALOAD);
    }

    public void IASTORE() {
        INSN(Opcodes.IASTORE);
    }

    public void SASTORE() {
        INSN(Opcodes.SASTORE);
    }

    public void BASTORE() {
        INSN(Opcodes.BASTORE);
    }

    public void CASTORE() {
        INSN(Opcodes.CASTORE);
    }

    public void LASTORE() {
        INSN(Opcodes.LASTORE);
    }

    public void FASTORE() {
        INSN(Opcodes.FASTORE);
    }

    public void DASTORE() {
        INSN(Opcodes.DASTORE);
    }

    public void AASTORE() {
        INSN(Opcodes.AASTORE);
    }

    public void ARRAYLENGTH() {
        INSN(Opcodes.ARRAYLENGTH);
    }

    public void NEWARRAY(int type) {
        assert type >= Opcodes.T_CHAR && type <= Opcodes.T_LONG;
        insnList.add(new IntInsnNode(Opcodes.NEWARRAY, type));
    }

    public void ANEWARRAY(String desc) {
        insnList.add(new TypeInsnNode(Opcodes.ANEWARRAY, desc));
    }

    public void ANEWARRAY(String desc, int dimensions) {
        insnList.add(new MultiANewArrayInsnNode(desc, dimensions));
    }

    /**
     * Constant
     */
    public void ACONST_NULL() {
        INSN(Opcodes.ACONST_NULL);
    }

    public void ICONST_M1() {
        INSN(Opcodes.ICONST_M1);
    }

    public void ICONST_0() {
        INSN(Opcodes.ICONST_0);
    }

    public void ICONST_1() {
        INSN(Opcodes.ICONST_1);
    }

    public void ICONST_2() {
        INSN(Opcodes.ICONST_2);
    }

    public void ICONST_3() {
        INSN(Opcodes.ICONST_3);
    }

    public void ICONST_4() {
        INSN(Opcodes.ICONST_4);
    }

    public void ICONST_5() {
        INSN(Opcodes.ICONST_5);
    }

    public void LCONST_0() {
        INSN(Opcodes.LCONST_0);
    }

    public void LCONST_1() {
        INSN(Opcodes.LCONST_1);
    }

    public void FCONST_0() {
        INSN(Opcodes.FCONST_0);
    }

    public void FCONST_1() {
        INSN(Opcodes.FCONST_1);
    }

    public void FCONST_2() {
        INSN(Opcodes.FCONST_2);
    }

    public void DCONST_0() {
        INSN(Opcodes.DCONST_0);
    }

    public void DCONST_1() {
        INSN(Opcodes.DCONST_1);
    }

    public void BIPUSH(int value) {
        assert value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
        insnList.add(new IntInsnNode(Opcodes.BIPUSH, value));
    }

    public void SIPUSH(int value) {
        assert value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
        insnList.add(new IntInsnNode(Opcodes.SIPUSH, value));
    }

    public void LDC(Number number) {
        insnList.add(new LdcInsnNode(number));
    }

    public void LDC(String string) {
        insnList.add(new LdcInsnNode(string));
    }

    public void LDC(Type type) {
        insnList.add(new LdcInsnNode(type));
    }

    public void LDC_TYPE(String typeDesc) {
        insnList.add(new LdcInsnNode(Type.getType(typeDesc)));
    }

    public void LDC_TYPE(String typeDesc, boolean isArray) {
        insnList.add(new LdcInsnNode(Type.getType(isArray ? "[" + typeDesc : typeDesc)));
    }

    /**
     * Field
     */
    public void GETSTATIC(String owner, String name, String desc) {
        insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, name, desc));
    }

    public void PUTSTATIC(String owner, String name, String desc) {
        insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, name, desc));
    }

    public void GETSTATIC(String owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, field.name, field.desc));
    }

    public void PUTSTATIC(String owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field.name, field.desc));
    }

    public void GETSTATIC(ClassNode owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name, field.name, field.desc));
    }

    public void PUTSTATIC(ClassNode owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner.name, field.name, field.desc));
    }

    public void GETFIELD(String owner, String name, String desc) {
        insnList.add(new FieldInsnNode(Opcodes.GETFIELD, owner, name, desc));
    }

    public void PUTFIELD(String owner, String name, String desc) {
        insnList.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, name, desc));
    }

    public void GETFIELD(String owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.GETFIELD, owner, field.name, field.desc));
    }

    public void PUTFIELD(String owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, field.name, field.desc));
    }

    public void GETFIELD(ClassNode owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.GETFIELD, owner.name, field.name, field.desc));
    }

    public void PUTFIELD(ClassNode owner, FieldNode field) {
        insnList.add(new FieldInsnNode(Opcodes.PUTFIELD, owner.name, field.name, field.desc));
    }

    /**
     * InvokeDynamic
     */
    public void INVOKEDYNAMIC(String name, String desc, Handle bsmHandle, Object... bsmArgs) {
        insnList.add(new InvokeDynamicInsnNode(name, desc, bsmHandle, bsmArgs));
    }

    public static Handle H_GETFIELD(String owner, String name, String desc) {
        return new Handle(Opcodes.H_GETFIELD, owner, name, desc, false);
    }

    public static Handle H_GETSTATIC(String owner, String name, String desc) {
        return new Handle(Opcodes.H_GETSTATIC, owner, name, desc, false);
    }

    public static Handle H_PUTFIELD(String owner, String name, String desc) {
        return new Handle(Opcodes.H_PUTFIELD, owner, name, desc, false);
    }

    public static Handle H_PUTSTATIC(String owner, String name, String desc) {
        return new Handle(Opcodes.H_PUTSTATIC, owner, name, desc, false);
    }

    public static Handle H_GETFIELD(String owner, FieldNode field) {
        return new Handle(Opcodes.H_GETFIELD, owner, field.name, field.desc, false);
    }

    public static Handle H_GETSTATIC(String owner, FieldNode field) {
        return new Handle(Opcodes.H_GETSTATIC, owner, field.name, field.desc, false);
    }

    public static Handle H_PUTFIELD(String owner, FieldNode field) {
        return new Handle(Opcodes.H_PUTFIELD, owner, field.name, field.desc, false);
    }

    public static Handle H_PUTSTATIC(String owner, FieldNode field) {
        return new Handle(Opcodes.H_PUTSTATIC, owner, field.name, field.desc, false);
    }

    public static Handle H_GETFIELD(ClassNode owner, FieldNode field) {
        return new Handle(Opcodes.H_GETFIELD, owner.name, field.name, field.desc, false);
    }

    public static Handle H_GETSTATIC(ClassNode owner, FieldNode field) {
        return new Handle(Opcodes.H_GETSTATIC, owner.name, field.name, field.desc, false);
    }

    public static Handle H_PUTFIELD(ClassNode owner, FieldNode field) {
        return new Handle(Opcodes.H_PUTFIELD, owner.name, field.name, field.desc, false);
    }

    public static Handle H_PUTSTATIC(ClassNode owner, FieldNode field) {
        return new Handle(Opcodes.H_PUTSTATIC, owner.name, field.name, field.desc, false);
    }

    public static Handle H_INVOKEVIRTUAL(String owner, String name, String desc) {
        return new Handle(Opcodes.H_INVOKEVIRTUAL, owner, name, desc, false);
    }

    public static Handle H_INVOKESTATIC(String owner, String name, String desc) {
        return new Handle(Opcodes.H_INVOKESTATIC, owner, name, desc, false);
    }

    public static Handle H_INVOKESPECIAL(String owner, String name, String desc) {
        return new Handle(Opcodes.H_INVOKESPECIAL, owner, name, desc, false);
    }

    public static Handle H_NEWINVOKESPECIAL(String owner, String name, String desc) {
        return new Handle(Opcodes.H_NEWINVOKESPECIAL, owner, name, desc, false);
    }

    public static Handle H_INVOKEINTERFACE(String owner, String name, String desc) {
        return new Handle(Opcodes.H_INVOKEINTERFACE, owner, name, desc, true);
    }

    public static Handle H_INVOKEVIRTUAL(String owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKEVIRTUAL, owner, method.name, method.desc, false);
    }

    public static Handle H_INVOKESTATIC(String owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKESTATIC, owner, method.name, method.desc, false);
    }

    public static Handle H_INVOKESPECIAL(String owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKESPECIAL, owner, method.name, method.desc, false);
    }

    public static Handle H_NEWINVOKESPECIAL(String owner, MethodNode method) {
        return new Handle(Opcodes.H_NEWINVOKESPECIAL, owner, method.name, method.desc, false);
    }

    public static Handle H_INVOKEINTERFACE(String owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKEINTERFACE, owner, method.name, method.desc, true);
    }

    public static Handle H_INVOKEVIRTUAL(ClassNode owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKEVIRTUAL, owner.name, method.name, method.desc, false);
    }

    public static Handle H_INVOKESTATIC(ClassNode owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKESTATIC, owner.name, method.name, method.desc, false);
    }

    public static Handle H_INVOKESPECIAL(ClassNode owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKESPECIAL, owner.name, method.name, method.desc, false);
    }

    public static Handle H_NEWINVOKESPECIAL(ClassNode owner, MethodNode method) {
        return new Handle(Opcodes.H_NEWINVOKESPECIAL, owner.name, method.name, method.desc, false);
    }

    public static Handle H_INVOKEINTERFACE(ClassNode owner, MethodNode method) {
        return new Handle(Opcodes.H_INVOKEINTERFACE, owner.name, method.name, method.desc, true);
    }

    /**
     * Jump
     */
    public void GOTO(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.GOTO, target));
    }

    public void IFNULL(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNULL, target));
    }

    public void IFNONNULL(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNONNULL, target));
    }

    public void IFEQ(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFEQ, target));
    }

    public void IFNE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNE, target));
    }

    public void IFLT(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLT, target));
    }

    public void IFGE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGE, target));
    }

    public void IFGT(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGT, target));
    }

    public void IFLE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLE, target));
    }

    public void IF_ICMPEQ(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, target));
    }

    public void IF_ICMPNE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPNE, target));
    }

    public void IF_ICMPLT(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLT, target));
    }

    public void IF_ICMPGE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGE, target));
    }

    public void IF_ICMPGT(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGT, target));
    }

    public void IF_ICMPLE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLE, target));
    }

    public void IF_ACMPEQ(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, target));
    }

    public void IF_ACMPNE(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPNE, target));
    }

    public void JSR(LabelNode target) {
        insnList.add(new JumpInsnNode(Opcodes.JSR, target));
    }

    public void GOTO(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.GOTO, asNode(target)));
    }

    public void IFNULL(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNULL, asNode(target)));
    }

    public void IFNONNULL(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNONNULL, asNode(target)));
    }

    public void IFEQ(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFEQ, asNode(target)));
    }

    public void IFNE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNE, asNode(target)));
    }

    public void IFLT(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLT, asNode(target)));
    }

    public void IFGE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGE, asNode(target)));
    }

    public void IFGT(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGT, asNode(target)));
    }

    public void IFLE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLE, asNode(target)));
    }

    public void IF_ICMPEQ(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, asNode(target)));
    }

    public void IF_ICMPNE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPNE, asNode(target)));
    }

    public void IF_ICMPLT(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLT, asNode(target)));
    }

    public void IF_ICMPGE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGE, asNode(target)));
    }

    public void IF_ICMPGT(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGT, asNode(target)));
    }

    public void IF_ICMPLE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLE, asNode(target)));
    }

    public void IF_ACMPEQ(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, asNode(target)));
    }

    public void IF_ACMPNE(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPNE, asNode(target)));
    }

    public void JSR(Label target) {
        insnList.add(new JumpInsnNode(Opcodes.JSR, asNode(target)));
    }

    public void GOTO(String target) {
        insnList.add(new JumpInsnNode(Opcodes.GOTO, asNode(L(target))));
    }

    public void IFNULL(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNULL, asNode(L(target))));
    }

    public void IFNONNULL(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNONNULL, asNode(L(target))));
    }

    public void IFEQ(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFEQ, asNode(L(target))));
    }

    public void IFNE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFNE, asNode(L(target))));
    }

    public void IFLT(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLT, asNode(L(target))));
    }

    public void IFGE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGE, asNode(L(target))));
    }

    public void IFGT(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFGT, asNode(L(target))));
    }

    public void IFLE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IFLE, asNode(L(target))));
    }

    public void IF_ICMPEQ(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, asNode(L(target))));
    }

    public void IF_ICMPNE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPNE, asNode(L(target))));
    }

    public void IF_ICMPLT(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLT, asNode(L(target))));
    }

    public void IF_ICMPGE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGE, asNode(L(target))));
    }

    public void IF_ICMPGT(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGT, asNode(L(target))));
    }

    public void IF_ICMPLE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLE, asNode(L(target))));
    }

    public void IF_ACMPEQ(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, asNode(L(target))));
    }

    public void IF_ACMPNE(String target) {
        insnList.add(new JumpInsnNode(Opcodes.IF_ACMPNE, asNode(L(target))));
    }

    public void JSR(String target) {
        insnList.add(new JumpInsnNode(Opcodes.JSR, asNode(L(target))));
    }

    public void RET(int slot) {
        insnList.add(new VarInsnNode(Opcodes.RET, slot));
    }

    public void IRETURN() {
        INSN(Opcodes.IRETURN);
    }

    public void LRETURN() {
        INSN(Opcodes.LRETURN);
    }

    public void FRETURN() {
        INSN(Opcodes.FRETURN);
    }

    public void DRETURN() {
        INSN(Opcodes.DRETURN);
    }

    public void ARETURN() {
        INSN(Opcodes.ARETURN);
    }

    public void RETURN() {
        INSN(Opcodes.RETURN);
    }

    public void ATHROW() {
        INSN(Opcodes.ATHROW);
    }

    /**
     * Method
     */
    public void INVOKESTATIC(String owner, String name, String desc) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
    }

    public void INVOKEVIRTUAL(String owner, String name, String desc) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false));
    }

    public void INVOKESPECIAL(String owner, String name, String desc) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, name, desc, false));
    }

    public void INVOKEINTERFACE(String owner, String name, String desc) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, desc, true));
    }

    public void INVOKESTATIC(String owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, method.name, method.desc, false));
    }

    public void INVOKEVIRTUAL(String owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, method.name, method.desc, false));
    }

    public void INVOKESPECIAL(String owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, method.name, method.desc, false));
    }

    public void INVOKEINTERFACE(String owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, method.name, method.desc, true));
    }

    public void INVOKESTATIC(ClassNode owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, method.name, method.desc, false));
    }

    public void INVOKEVIRTUAL(ClassNode owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner.name, method.name, method.desc, false));
    }

    public void INVOKESPECIAL(ClassNode owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, method.name, method.desc, false));
    }

    public void INVOKEINTERFACE(ClassNode owner, MethodNode method) {
        insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, owner.name, method.name, method.desc, true));
    }

    /**
     * Miscellaneous
     */
    public void NOP() {
        INSN(Opcodes.NOP);
    }

    public void POP() {
        INSN(Opcodes.POP);
    }

    public void POP2() {
        INSN(Opcodes.POP2);
    }

    public void DUP() {
        INSN(Opcodes.DUP);
    }

    public void DUP_X1() {
        INSN(Opcodes.DUP_X1);
    }

    public void DUP_X2() {
        INSN(Opcodes.DUP_X2);
    }

    public void DUP2() {
        INSN(Opcodes.DUP2);
    }

    public void DUP2_X1() {
        INSN(Opcodes.DUP2_X1);
    }

    public void DUP2_X2() {
        INSN(Opcodes.DUP2_X2);
    }

    public void SWAP() {
        INSN(Opcodes.SWAP);
    }

    public void NEW(String type) {
        insnList.add(new TypeInsnNode(Opcodes.NEW, type));
    }

    public void CHECKCAST(String type) {
        insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, type));
    }

    public void INSTANCEOF(String type) {
        insnList.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
    }

    public void MONITORENTER() {
        INSN(Opcodes.MONITORENTER);
    }

    public void MONITOREXIT() {
        INSN(Opcodes.MONITOREXIT);
    }

    /**
     * Switch
     */
    public void TABLESWITCH(int min, int max, LabelNode def, LabelNode... labels) {
        insnList.add(new TableSwitchInsnNode(min, max, def, labels));
    }

    public void TABLESWITCH(int min, int max, Label def, Label... labels) {
        LabelNode[] labelNodes = Arrays.stream(labels).map(Utils::asNode).toArray(LabelNode[]::new);
        insnList.add(new TableSwitchInsnNode(min, max, asNode(def), labelNodes));
    }

    public void TABLESWITCH(int min, int max, String def, String... labels) {
        LabelNode[] labelNodes = Arrays.stream(labels).map(it -> asNode(L(it))).toArray(LabelNode[]::new);
        insnList.add(new TableSwitchInsnNode(min, max, asNode(L(def)), labelNodes));
    }

    public void LOOKUPSWITCH(LabelNode def, int[] keys, LabelNode[] labels) {
        assert keys.length == labels.length;
        Map<Integer, LabelNode> branches = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            branches.put(i, labels[i]);
        }
        List<Map.Entry<Integer, LabelNode>> sortedBranches = branches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        int[] sortedKeys = new int[keys.length];
        LabelNode[] sortedLabels = new LabelNode[labels.length];
        for (int i = 0; i < sortedBranches.size(); i++) {
            sortedKeys[i] = sortedBranches.get(i).getKey();
            sortedLabels[i] = sortedBranches.get(i).getValue();
        }
        insnList.add(new LookupSwitchInsnNode(def, sortedKeys, sortedLabels));
    }

    public void LOOKUPSWITCH(Label def, int[] keys, Label[] labels) {
        assert keys.length == labels.length;
        Map<Integer, Label> branches = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            branches.put(i, labels[i]);
        }
        List<Map.Entry<Integer, Label>> sortedBranches = branches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        int[] sortedKeys = new int[keys.length];
        LabelNode[] sortedLabels = new LabelNode[labels.length];
        for (int i = 0; i < sortedBranches.size(); i++) {
            sortedKeys[i] = sortedBranches.get(i).getKey();
            sortedLabels[i] = asNode(sortedBranches.get(i).getValue());
        }
        insnList.add(new LookupSwitchInsnNode(asNode(def), sortedKeys, sortedLabels));
    }

    public void LOOKUPSWITCH(String def, int[] keys, String[] labels) {
        assert keys.length == labels.length;
        Map<Integer, String> branches = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            branches.put(i, labels[i]);
        }
        List<Map.Entry<Integer, String>> sortedBranches = branches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
        int[] sortedKeys = new int[keys.length];
        LabelNode[] sortedLabels = new LabelNode[labels.length];
        for (int i = 0; i < sortedBranches.size(); i++) {
            sortedKeys[i] = sortedBranches.get(i).getKey();
            sortedLabels[i] = asNode(L(sortedBranches.get(i).getValue()));
        }
        insnList.add(new LookupSwitchInsnNode(asNode(L(def)), sortedKeys, sortedLabels));
    }

    /**
     * Variable
     */
    public void ILOAD(int slot) {
        insnList.add(new VarInsnNode(Opcodes.ILOAD, slot));
    }

    public void LLOAD(int slot) {
        insnList.add(new VarInsnNode(Opcodes.LLOAD, slot));
    }

    public void FLOAD(int slot) {
        insnList.add(new VarInsnNode(Opcodes.FLOAD, slot));
    }

    public void DLOAD(int slot) {
        insnList.add(new VarInsnNode(Opcodes.DLOAD, slot));
    }

    public void ALOAD(int slot) {
        insnList.add(new VarInsnNode(Opcodes.ALOAD, slot));
    }

    public void ISTORE(int slot) {
        insnList.add(new VarInsnNode(Opcodes.ISTORE, slot));
    }

    public void LSTORE(int slot) {
        insnList.add(new VarInsnNode(Opcodes.LSTORE, slot));
    }

    public void FSTORE(int slot) {
        insnList.add(new VarInsnNode(Opcodes.FSTORE, slot));
    }

    public void DSTORE(int slot) {
        insnList.add(new VarInsnNode(Opcodes.DSTORE, slot));
    }

    public void ASTORE(int slot) {
        insnList.add(new VarInsnNode(Opcodes.ASTORE, slot));
    }

    /**
     * Label
     */
    public void LABEL(String label) {
        insnList.add(asNode(L(label)));
    }

    public void LABEL(Label label) {
        insnList.add(asNode(label));
    }

    public void LABEL(LabelNode labelNode) {
        insnList.add(labelNode);
    }

    public void FRAME(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        insnList.add(new FrameNode(type, numLocal, local, numStack, stack));
    }

    public void LINE(int line, Label label) {
        insnList.add(new LineNumberNode(line, asNode(label)));
    }

    public void LINE(int line, LabelNode label) {
        insnList.add(new LineNumberNode(line, label));
    }

    /**
     * Sugar
     */
    public void INT(int value) {
        insnList.add(toInsnNode(value));
    }

    public void LONG(long value) {
        insnList.add(toInsnNode(value));
    }

    public void FLOAT(float value) {
        insnList.add(toInsnNode(value));
    }

    public void DOUBLE(double value) {
        insnList.add(toInsnNode(value));
    }

}
