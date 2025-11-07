package net.spartanb312.everett.bootstrap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Module {
    String name();

    String version();

    String description() default "";

    String author() default "";
}
