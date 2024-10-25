package net.spartanb312.grunt.example;

import net.spartanb312.genesis.java.Builders;
import net.spartanb312.genesis.java.builder.AnnotationBuilder;
import net.spartanb312.genesis.java.builder.ClassBuilder;
import net.spartanb312.genesis.java.builder.MethodBuilder;
import net.spartanb312.grunt.event.events.TransformerEvent;
import net.spartanb312.grunt.plugin.java.JavaPlugin;
import net.spartanb312.grunt.process.Transformer;
import net.spartanb312.grunt.process.Transformers;
import net.spartanb312.grunt.process.resource.ResourceCache;
import net.spartanb312.grunt.process.transformers.flow.ControlflowTransformer;
import net.spartanb312.grunt.utils.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

import static net.spartanb312.genesis.java.Access.PUBLIC;
import static net.spartanb312.genesis.java.Access.STATIC;

public class JavaMain extends JavaPlugin {

    public static final String NAME = "JavaExample";
    public static final String VERSION = "1.0.0";
    public static final String AUTHOR = "SpartanB312";
    public static final String DESCRIPTION = "Example of java plugin";
    public static final String MIN_VERSION = "2.4.0";

    public JavaMain() {
        super(NAME, VERSION, AUTHOR, DESCRIPTION, MIN_VERSION);
        listener(TransformerEvent.Before.class, it -> {
            Logger.INSTANCE.info("Running: " + it.getTransformer().getName());
            if (it.getTransformer() == ControlflowTransformer.INSTANCE) {
                it.cancel();
                Logger.INSTANCE.info("Disabled control flow!");
            }
        });
        subscribe();
    }

    @Override
    public void onInit() {
        Logger.INSTANCE.info("Initializing my java plugin...");
        Transformers.INSTANCE.register(new TestTransformer(), 1000);
    }

    public static class TestTransformer extends Transformer {

        public TestTransformer() {
            super("Test", Category.Miscellaneous, false);
        }

        @Override
        public void transform(@NotNull ResourceCache resourceCache) {
            Logger.INSTANCE.info("Running test transformer...");
            resourceCache.addClass(testClass());
        }

    }

    public static ClassNode testClass() {
        return Builders.clazz(new ClassBuilder(
                PUBLIC,
                "TestClass"
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
