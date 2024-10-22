package net.spartanb312.grunt.plugin.java;

import net.spartanb312.grunt.event.Event;

public interface ListenerTask<T extends Event>  {
    void invoke(T event);
}
