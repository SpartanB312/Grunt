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
package net.spartanb312.grunteon.asm.tree.insn

import org.objectweb.asm.MethodVisitor

/**
 * A doubly linked list of [AbstractInsnNode] objects. *This implementation is not thread
 * safe*.
 */
class InsnList : Iterable<AbstractInsnNode?> {
    /** The number of instructions in this list.  */
    private var size = 0

    /** The first instruction in this list. May be null.  */
    private var firstInsn: AbstractInsnNode? = null

    /**
     * Returns the last instruction in this list.
     *
     * @return the last instruction in this list, or null if the list is empty.
     */
    /** The last instruction in this list. May be null.  */
    var last: AbstractInsnNode? = null
        private set

    /**
     * A cache of the instructions of this list. This cache is used to improve the performance of the
     * [.get] method.
     */
    var cache: Array<AbstractInsnNode?>?

    /**
     * Returns the number of instructions in this list.
     *
     * @return the number of instructions in this list.
     */
    fun size(): Int {
        return size
    }

    val first: AbstractInsnNode
        /**
         * Returns the first instruction in this list.
         *
         * @return the first instruction in this list, or null if the list is empty.
         */
        get() = firstInsn!!

    /**
     * Returns the instruction whose index is given. This method builds a cache of the instructions in
     * this list to avoid scanning the whole list each time it is called. Once the cache is built,
     * this method runs in constant time. This cache is invalidated by all the methods that modify the
     * list.
     *
     * @param index the index of the instruction that must be returned.
     * @return the instruction whose index is given.
     * @throws IndexOutOfBoundsException if (index &lt; 0 || index &gt;= size()).
     */
    fun get(index: Int): AbstractInsnNode? {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException()
        }
        if (cache == null) {
            cache = toArray()
        }
        return cache!![index]
    }

    /**
     * Returns true if the given instruction belongs to this list. This method always scans
     * the instructions of this list until it finds the given instruction or reaches the end of the
     * list.
     *
     * @param insnNode an instruction.
     * @return true if the given instruction belongs to this list.
     */
    fun contains(insnNode: AbstractInsnNode?): Boolean {
        var currentInsn = firstInsn
        while (currentInsn != null && currentInsn !== insnNode) {
            currentInsn = currentInsn.nextInsn
        }
        return currentInsn != null
    }

    /**
     * Returns the index of the given instruction in this list. This method builds a cache of the
     * instruction indexes to avoid scanning the whole list each time it is called. Once the cache is
     * built, this method run in constant time. The cache is invalidated by all the methods that
     * modify the list.
     *
     * @param insnNode an instruction *of this list*.
     * @return the index of the given instruction in this list. *The result of this method is
     * undefined if the given instruction does not belong to this list*. Use [.contains]
     * to test if an instruction belongs to an instruction list or not.
     */
    fun indexOf(insnNode: AbstractInsnNode): Int {
        if (cache == null) {
            cache = toArray()
        }
        return insnNode.index
    }

    /**
     * Makes the given visitor visit all the instructions in this list.
     *
     * @param methodVisitor the method visitor that must visit the instructions.
     */
    fun accept(methodVisitor: MethodVisitor?) {
        var currentInsn = firstInsn
        while (currentInsn != null) {
            currentInsn.accept(methodVisitor)
            currentInsn = currentInsn.nextInsn
        }
    }

    /**
     * Returns an iterator over the instructions in this list.
     *
     * @return an iterator over the instructions in this list.
     */
    override fun iterator(): MutableListIterator<AbstractInsnNode?> {
        return iterator(0)
    }

    /**
     * Returns an iterator over the instructions in this list.
     *
     * @param index index of instruction for the iterator to start at.
     * @return an iterator over the instructions in this list.
     */
    fun iterator(index: Int): MutableListIterator<AbstractInsnNode?> {
        return InsnList.InsnListIterator(index)
    }

    /**
     * Returns an array containing all the instructions in this list.
     *
     * @return an array containing all the instructions in this list.
     */
    fun toArray(): Array<AbstractInsnNode?> {
        var currentInsnIndex = 0
        var currentInsn = firstInsn
        val insnNodeArray = arrayOfNulls<AbstractInsnNode>(size)
        while (currentInsn != null) {
            insnNodeArray[currentInsnIndex] = currentInsn
            currentInsn.index = currentInsnIndex++
            currentInsn = currentInsn.nextInsn
        }
        return insnNodeArray
    }

    /**
     * Replaces an instruction of this list with another instruction.
     *
     * @param oldInsnNode an instruction *of this list*.
     * @param newInsnNode another instruction, *which must not belong to any [InsnList]*.
     */
    fun set(oldInsnNode: AbstractInsnNode, newInsnNode: AbstractInsnNode) {
        val nextInsn = oldInsnNode.nextInsn
        newInsnNode.nextInsn = nextInsn
        if (nextInsn != null) {
            nextInsn.previousInsn = newInsnNode
        } else {
            this.last = newInsnNode
        }
        val previousInsn = oldInsnNode.previousInsn
        newInsnNode.previousInsn = previousInsn
        if (previousInsn != null) {
            previousInsn.nextInsn = newInsnNode
        } else {
            firstInsn = newInsnNode
        }
        if (cache != null) {
            val index = oldInsnNode.index
            cache!![index] = newInsnNode
            newInsnNode.index = index
        } else {
            newInsnNode.index = 0 // newInsnNode now belongs to an InsnList.
        }
        oldInsnNode.index = -1 // oldInsnNode no longer belongs to an InsnList.
        oldInsnNode.previousInsn = null
        oldInsnNode.nextInsn = null
    }

    /**
     * Adds the given instruction to the end of this list.
     *
     * @param insnNode an instruction, *which must not belong to any [InsnList]*.
     */
    fun add(insnNode: AbstractInsnNode) {
        ++size
        if (this.last == null) {
            firstInsn = insnNode
            this.last = insnNode
        } else {
            last!!.nextInsn = insnNode
            insnNode.previousInsn = this.last
        }
        this.last = insnNode
        cache = null
        insnNode.index = 0 // insnNode now belongs to an InsnList.
    }

    /**
     * Adds the given instructions to the end of this list.
     *
     * @param insnList an instruction list, which is cleared during the process. This list must be
     * different from 'this'.
     */
    fun add(insnList: InsnList) {
        if (insnList.size == 0) {
            return
        }
        size += insnList.size
        if (this.last == null) {
            firstInsn = insnList.firstInsn
            this.last = insnList.last
        } else {
            val firstInsnListElement = insnList.firstInsn
            last!!.nextInsn = firstInsnListElement
            firstInsnListElement!!.previousInsn = this.last
            this.last = insnList.last
        }
        cache = null
        insnList.removeAll(false)
    }

    /**
     * Inserts the given instruction at the beginning of this list.
     *
     * @param insnNode an instruction, *which must not belong to any [InsnList]*.
     */
    fun insert(insnNode: AbstractInsnNode) {
        ++size
        if (firstInsn == null) {
            firstInsn = insnNode
            this.last = insnNode
        } else {
            firstInsn!!.previousInsn = insnNode
            insnNode.nextInsn = firstInsn
        }
        firstInsn = insnNode
        cache = null
        insnNode.index = 0 // insnNode now belongs to an InsnList.
    }

    /**
     * Inserts the given instructions at the beginning of this list.
     *
     * @param insnList an instruction list, which is cleared during the process. This list must be
     * different from 'this'.
     */
    fun insert(insnList: InsnList) {
        if (insnList.size == 0) {
            return
        }
        size += insnList.size
        if (firstInsn == null) {
            firstInsn = insnList.firstInsn
            this.last = insnList.last
        } else {
            val lastInsnListElement = insnList.last
            firstInsn!!.previousInsn = lastInsnListElement
            lastInsnListElement!!.nextInsn = firstInsn
            firstInsn = insnList.firstInsn
        }
        cache = null
        insnList.removeAll(false)
    }

    /**
     * Inserts the given instruction after the specified instruction.
     *
     * @param previousInsn an instruction *of this list* after which insnNode must be inserted.
     * @param insnNode the instruction to be inserted, *which must not belong to any [     ]*.
     */
    fun insert(previousInsn: AbstractInsnNode, insnNode: AbstractInsnNode) {
        ++size
        val nextInsn = previousInsn.nextInsn
        if (nextInsn == null) {
            this.last = insnNode
        } else {
            nextInsn.previousInsn = insnNode
        }
        previousInsn.nextInsn = insnNode
        insnNode.nextInsn = nextInsn
        insnNode.previousInsn = previousInsn
        cache = null
        insnNode.index = 0 // insnNode now belongs to an InsnList.
    }

    /**
     * Inserts the given instructions after the specified instruction.
     *
     * @param previousInsn an instruction *of this list* after which the instructions must be
     * inserted.
     * @param insnList the instruction list to be inserted, which is cleared during the process. This
     * list must be different from 'this'.
     */
    fun insert(previousInsn: AbstractInsnNode, insnList: InsnList) {
        if (insnList.size == 0) {
            return
        }
        size += insnList.size
        val firstInsnListElement = insnList.firstInsn
        val lastInsnListElement = insnList.last
        val nextInsn = previousInsn.nextInsn
        if (nextInsn == null) {
            this.last = lastInsnListElement
        } else {
            nextInsn.previousInsn = lastInsnListElement
        }
        previousInsn.nextInsn = firstInsnListElement
        lastInsnListElement!!.nextInsn = nextInsn
        firstInsnListElement!!.previousInsn = previousInsn
        cache = null
        insnList.removeAll(false)
    }

    /**
     * Inserts the given instruction before the specified instruction.
     *
     * @param nextInsn an instruction *of this list* before which insnNode must be inserted.
     * @param insnNode the instruction to be inserted, *which must not belong to any [     ]*.
     */
    fun insertBefore(nextInsn: AbstractInsnNode, insnNode: AbstractInsnNode) {
        ++size
        val previousInsn = nextInsn.previousInsn
        if (previousInsn == null) {
            firstInsn = insnNode
        } else {
            previousInsn.nextInsn = insnNode
        }
        nextInsn.previousInsn = insnNode
        insnNode.nextInsn = nextInsn
        insnNode.previousInsn = previousInsn
        cache = null
        insnNode.index = 0 // insnNode now belongs to an InsnList.
    }

    /**
     * Inserts the given instructions before the specified instruction.
     *
     * @param nextInsn an instruction *of this list* before which the instructions must be
     * inserted.
     * @param insnList the instruction list to be inserted, which is cleared during the process. This
     * list must be different from 'this'.
     */
    fun insertBefore(nextInsn: AbstractInsnNode, insnList: InsnList) {
        if (insnList.size == 0) {
            return
        }
        size += insnList.size
        val firstInsnListElement = insnList.firstInsn
        val lastInsnListElement = insnList.last
        val previousInsn = nextInsn.previousInsn
        if (previousInsn == null) {
            firstInsn = firstInsnListElement
        } else {
            previousInsn.nextInsn = firstInsnListElement
        }
        nextInsn.previousInsn = lastInsnListElement
        lastInsnListElement!!.nextInsn = nextInsn
        firstInsnListElement!!.previousInsn = previousInsn
        cache = null
        insnList.removeAll(false)
    }

    /**
     * Removes the given instruction from this list.
     *
     * @param insnNode the instruction *of this list* that must be removed.
     */
    fun remove(insnNode: AbstractInsnNode) {
        --size
        val nextInsn = insnNode.nextInsn
        val previousInsn = insnNode.previousInsn
        if (nextInsn == null) {
            if (previousInsn == null) {
                firstInsn = null
                this.last = null
            } else {
                previousInsn.nextInsn = null
                this.last = previousInsn
            }
        } else {
            if (previousInsn == null) {
                firstInsn = nextInsn
                nextInsn.previousInsn = null
            } else {
                previousInsn.nextInsn = nextInsn
                nextInsn.previousInsn = previousInsn
            }
        }
        cache = null
        insnNode.index = -1 // insnNode no longer belongs to an InsnList.
        insnNode.previousInsn = null
        insnNode.nextInsn = null
    }

    /**
     * Removes all the instructions of this list.
     *
     * @param mark if the instructions must be marked as no longer belonging to any [InsnList].
     */
    fun removeAll(mark: Boolean) {
        if (mark) {
            var currentInsn = firstInsn
            while (currentInsn != null) {
                val next = currentInsn.nextInsn
                currentInsn.index = -1 // currentInsn no longer belongs to an InsnList.
                currentInsn.previousInsn = null
                currentInsn.nextInsn = null
                currentInsn = next
            }
        }
        size = 0
        firstInsn = null
        this.last = null
        cache = null
    }

    /** Removes all the instructions of this list.  */
    fun clear() {
        removeAll(false)
    }

    /**
     * Resets all the labels in the instruction list. This method should be called before reusing an
     * instruction list between several `ClassWriter`s.
     */
    fun resetLabels() {
        var currentInsn = firstInsn
        while (currentInsn != null) {
            if (currentInsn is LabelNode) {
                currentInsn.resetLabel()
            }
            currentInsn = currentInsn.nextInsn
        }
    }

    // Note: this class is not generified because it would create bridges.
    private inner class InsnListIterator(index: Int) : MutableListIterator<Any?> {
        var nextInsn: AbstractInsnNode? = null

        var previousInsn: AbstractInsnNode? = null

        var remove: AbstractInsnNode? = null

        init {
            if (index < 0 || index > size()) {
                throw IndexOutOfBoundsException()
            } else if (index == size()) {
                nextInsn = null
                previousInsn = this.last
            } else {
                var currentInsn: AbstractInsnNode = this.first
                for (i in 0..<index) {
                    currentInsn = currentInsn.nextInsn
                }

                nextInsn = currentInsn
                previousInsn = currentInsn.previousInsn
            }
        }

        override fun hasNext(): Boolean {
            return nextInsn != null
        }

        override fun next(): Any {
            if (nextInsn == null) {
                throw NoSuchElementException()
            }
            val result = nextInsn
            previousInsn = result
            nextInsn = result!!.nextInsn
            remove = result
            return result
        }

        override fun remove() {
            if (remove != null) {
                if (remove === nextInsn) {
                    nextInsn = nextInsn!!.nextInsn
                } else {
                    previousInsn = previousInsn!!.previousInsn
                }
                this@InsnList.remove(remove!!)
                remove = null
            } else {
                throw IllegalStateException()
            }
        }

        override fun hasPrevious(): Boolean {
            return previousInsn != null
        }

        override fun previous(): Any {
            if (previousInsn == null) {
                throw NoSuchElementException()
            }
            val result = previousInsn
            nextInsn = result
            previousInsn = result!!.previousInsn
            remove = result
            return result
        }

        override fun nextIndex(): Int {
            if (nextInsn == null) {
                return size()
            }
            if (cache == null) {
                cache = toArray()
            }
            return nextInsn!!.index
        }

        override fun previousIndex(): Int {
            if (previousInsn == null) {
                return -1
            }
            if (cache == null) {
                cache = toArray()
            }
            return previousInsn!!.index
        }

        override fun add(o: Any?) {
            if (nextInsn != null) {
                this@InsnList.insertBefore(nextInsn!!, (o as AbstractInsnNode?)!!)
            } else if (previousInsn != null) {
                this@InsnList.insert(previousInsn!!, (o as AbstractInsnNode?)!!)
            } else {
                this@InsnList.add((o as AbstractInsnNode?)!!)
            }
            previousInsn = o
            remove = null
        }

        override fun set(o: Any?) {
            if (remove != null) {
                this@InsnList.set(remove!!, (o as AbstractInsnNode?)!!)
                if (remove === previousInsn) {
                    previousInsn = o as AbstractInsnNode
                } else {
                    nextInsn = o as AbstractInsnNode
                }
            } else {
                throw IllegalStateException()
            }
        }
    }
}
