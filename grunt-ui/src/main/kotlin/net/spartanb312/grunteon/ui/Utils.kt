package net.spartanb312.grunteon.ui


fun camelCaseToWords(input: String): String {
    return buildString {
        input.forEachIndexed { index, c ->
            if (c == '_' && index != 0) {
                append(' ')
                return@forEachIndexed
            }

            if (c.isUpperCase() && index != 0) {
                append(' ')
            }
            if (index == 0) {
                append(c.uppercase())
            } else {
                append(c.lowercase())
            }
        }
    }
}