package net.spartanb312.grunteon.index.io

fun compareVersion(version1: IntArray, version2: IntArray): Int {
    require(version1.size == 3)
    require(version2.size == 3)
    for (i in 0..2) {
        val v1 = version1[i]
        val v2 = version2[i]
        when {
            v1 > v2 -> return 1
            v1 < v2 -> return -1
        }
    }
    return 0
}

fun IntArray.isGreaterThan(other: IntArray): Boolean = compareVersion(this, other) == 1
fun IntArray.isLessThan(other: IntArray): Boolean = compareVersion(this, other) == -1
fun IntArray.isEqual(other: IntArray): Boolean = compareVersion(this, other) == 0