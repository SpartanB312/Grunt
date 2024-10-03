package net.spartanb312.grunt.utils.thread

import kotlinx.coroutines.CoroutineScope

object MainScope : CoroutineScope by newCoroutineScope(
    Runtime.getRuntime().availableProcessors(),
    "Coroutines"
)