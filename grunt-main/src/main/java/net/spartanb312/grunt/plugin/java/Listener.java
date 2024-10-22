package net.spartanb312.grunt.plugin.java;

import kotlin.Unit;
import net.spartanb312.grunt.event.Event;
import net.spartanb312.grunt.event.IListenerOwner;
import net.spartanb312.grunt.event.ListenerKt;

public class Listener {

    public static <E extends Event> void listener(
            IListenerOwner owner,
            Class<E> eventClass,
            ListenerTask<E> task
    ) {
        listener(owner, eventClass, 0, false, task);
    }

    public static <E extends Event> void listener(
            IListenerOwner owner,
            Class<E> eventClass,
            int priority,
            ListenerTask<E> task
    ) {
        listener(owner, eventClass, priority, false, task);
    }

    public static <E extends Event> void listener(
            IListenerOwner owner,
            Class<E> eventClass,
            int priority,
            boolean alwaysListening,
            ListenerTask<E> task
    ) {
        ListenerKt.listener(owner, eventClass, priority, alwaysListening, it -> {
            task.invoke(it);
            return Unit.INSTANCE;
        });
    }

}
