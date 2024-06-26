package net.spartanb312.grunt.process.hierarchy

interface Hierarchy {
    fun build()
    fun isSubType(child: String, father: String): Boolean
    val size: Int
}