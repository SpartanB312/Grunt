package net.spartanb312.grunteon.ui

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.process.DecimalRangeVal
import net.spartanb312.grunteon.obfuscator.util.Decimal

@Serializable
data class AppConfig(
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    val uiScale: Decimal = Decimal.ONE,
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    val fontScale: Decimal = Decimal.ONE,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val uiLogLevel: UiLogLevel = UiLogLevel.Info,
)