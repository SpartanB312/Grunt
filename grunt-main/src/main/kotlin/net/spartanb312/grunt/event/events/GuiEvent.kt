package net.spartanb312.grunt.event.events

import net.spartanb312.grunt.event.Event
import net.spartanb312.grunt.event.EventBus
import net.spartanb312.grunt.event.EventPosting

sealed class GuiEvent : Event {
    object BeforeInit : GuiEvent(), EventPosting by EventBus()
    object AfterInit : GuiEvent(), EventPosting by EventBus()
}