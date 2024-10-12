package net.spartanb312.genesis.java;

import net.spartanb312.genesis.java.builder.AnnotationBuilder;
import net.spartanb312.genesis.java.builder.ClassBuilder;
import net.spartanb312.genesis.java.builder.MethodBuilder;
import org.objectweb.asm.tree.ClassNode;

import static net.spartanb312.genesis.java.Access.PUBLIC;
import static net.spartanb312.genesis.java.Access.STATIC;

public class Test {

    public static ClassNode test() {
        return Builders.clazz(new ClassBuilder(
                PUBLIC,
                "Main"
        ) {
            @Override
            public void assemble() {
                addAnnotation(new AnnotationBuilder("LMark;") {
                    @Override
                    public void assemble() {
                        addProperty("key", 114514);
                        addProperty("value", 69420);
                    }
                });
                addMethod(new MethodBuilder(
                        PUBLIC + STATIC,
                        "main",
                        "([Ljava/lang/String;)V"
                ) {
                    @Override
                    protected void instructions() {
                        addAnnotation(new AnnotationBuilder("LMethodMark;") {
                            @Override
                            public void assemble() {
                                addProperty("type", 23333);
                                addProperty("version", "1.0.0");
                            }
                        });
                        ICONST_1();
                        TABLESWITCH(1, 2, "labelA", "labelB", "labelC");
                        LABEL("labelA");
                        GOTO("labelD");
                        LABEL("labelB");
                        GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;");
                        LDC("Hello 1");
                        INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
                        GOTO("labelD");
                        LABEL("labelC");
                        GOTO("labelD");
                        LABEL("labelD");
                        GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;");
                        LDC("End");
                        INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
                        RETURN();
                    }
                });
            }
        });
    }

}
