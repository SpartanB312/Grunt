// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package net.spartanb312.grunteon.asm.tree

/**
 * Utility methods to convert an array of primitive or object values to a mutable ArrayList, not
 * baked by the array (unlike [java.util.Arrays.asList]).
 *
 * @author Eric Bruneton
 */
internal object Util {
    fun <T> add(list: MutableList<T?>?, element: T?): MutableList<T?> {
        val newList = if (list == null) ArrayList<T?>(1) else list
        newList.add(element)
        return newList
    }

    fun <T> asArrayList(length: Int): MutableList<T?> {
        val list: MutableList<T?> = ArrayList<T?>(length)
        for (i in 0..<length) {
            list.add(null)
        }
        return list
    }

    fun <T> asArrayList(array: Array<T?>?): MutableList<T?> {
        if (array == null) {
            return ArrayList<T?>()
        }
        val list = ArrayList<T?>(array.size)
        for (t in array) {
            list.add(t) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return list
    }

    fun asArrayList(byteArray: ByteArray?): MutableList<Byte?> {
        if (byteArray == null) {
            return ArrayList<Byte?>()
        }
        val byteList = ArrayList<Byte?>(byteArray.size)
        for (b in byteArray) {
            byteList.add(b) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return byteList
    }

    fun asArrayList(booleanArray: BooleanArray?): MutableList<Boolean?> {
        if (booleanArray == null) {
            return ArrayList<Boolean?>()
        }
        val booleanList = ArrayList<Boolean?>(booleanArray.size)
        for (b in booleanArray) {
            booleanList.add(b) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return booleanList
    }

    fun asArrayList(shortArray: ShortArray?): MutableList<Short?> {
        if (shortArray == null) {
            return ArrayList<Short?>()
        }
        val shortList = ArrayList<Short?>(shortArray.size)
        for (s in shortArray) {
            shortList.add(s) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return shortList
    }

    fun asArrayList(charArray: CharArray?): MutableList<Char?> {
        if (charArray == null) {
            return ArrayList<Char?>()
        }
        val charList = ArrayList<Char?>(charArray.size)
        for (c in charArray) {
            charList.add(c) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return charList
    }

    fun asArrayList(intArray: IntArray?): MutableList<Int?> {
        if (intArray == null) {
            return ArrayList<Int?>()
        }
        val intList = ArrayList<Int?>(intArray.size)
        for (i in intArray) {
            intList.add(i) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return intList
    }

    fun asArrayList(floatArray: FloatArray?): MutableList<Float?> {
        if (floatArray == null) {
            return ArrayList<Float?>()
        }
        val floatList = ArrayList<Float?>(floatArray.size)
        for (f in floatArray) {
            floatList.add(f) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return floatList
    }

    fun asArrayList(longArray: LongArray?): MutableList<Long?> {
        if (longArray == null) {
            return ArrayList<Long?>()
        }
        val longList = ArrayList<Long?>(longArray.size)
        for (l in longArray) {
            longList.add(l) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return longList
    }

    fun asArrayList(doubleArray: DoubleArray?): MutableList<Double?> {
        if (doubleArray == null) {
            return ArrayList<Double?>()
        }
        val doubleList = ArrayList<Double?>(doubleArray.size)
        for (d in doubleArray) {
            doubleList.add(d) // NOPMD(UseArraysAsList): we want a modifiable list.
        }
        return doubleList
    }

    fun <T> asArrayList(length: Int, array: Array<T?>): MutableList<T?> {
        val list: MutableList<T?> = ArrayList<T?>(length)
        for (i in 0..<length) {
            list.add(array[i]) // NOPMD(UseArraysAsList): we convert a part of the array.
        }
        return list
    }
}
