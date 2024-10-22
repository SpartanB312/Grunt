package net.spartanb312.grunt.plugin.java;

import kotlin.Unit;
import net.spartanb312.grunt.event.Event;
import net.spartanb312.grunt.event.ListenerKt;
import net.spartanb312.grunt.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

abstract public class JavaPlugin extends Plugin {

    public JavaPlugin(@NotNull String name, @NotNull String version, @NotNull String author, @NotNull String description, @NotNull String minVersion) {
        super(name, version, author, description, minVersion);
    }

    public <E extends Event> void listener(
            Class<E> eventClass,
            ListenerTask<E> task
    ) {
        listener(eventClass, 0, false, task);
    }

    public <E extends Event> void listener(
            Class<E> eventClass,
            int priority,
            ListenerTask<E> task
    ) {
        listener(eventClass, priority, false, task);
    }

    public <E extends Event> void listener(
            Class<E> eventClass,
            int priority,
            boolean alwaysListening,
            ListenerTask<E> task
    ) {
        ListenerKt.listener(this, eventClass, priority, alwaysListening, it -> {
            task.invoke(it);
            return Unit.INSTANCE;
        });
    }

}
