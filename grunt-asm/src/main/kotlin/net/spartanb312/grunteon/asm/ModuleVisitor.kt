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
package net.spartanb312.grunteon.asm

/**
 * A visitor to visit a Java module. The methods of this class must be called in the following
 * order: ( `visitMainClass` | ( `visitPackage` | `visitRequire` | `visitExport` | `visitOpen` | `visitUse` | `visitProvide` )* ) `visitEnd`.
 *
 * @author Remi Forax
 * @author Eric Bruneton
 * @author Luna
 */
interface ModuleVisitor {

    /**
     * Visits the main class of the current module.
     *
     * @param mainClass the internal name of the main class of the current module (see [     ][Type.getInternalName]).
     */
    fun visitMainClass(mainClass: String) {}

    /**
     * Visits a package of the current module.
     *
     * @param packaze the internal name of a package (see [Type.getInternalName]).
     */
    fun visitPackage(packaze: String) {}

    /**
     * Visits a dependence of the current module.
     *
     * @param module the fully qualified name (using dots) of the dependence.
     * @param access the access flag of the dependence among `ACC_TRANSITIVE`, `ACC_STATIC_PHASE`, `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param version the module version at compile time, or null.
     */
    fun visitRequire(module: String, access: Int, version: String?) {}

    /**
     * Visits an exported package of the current module.
     *
     * @param packaze the internal name of the exported package (see [Type.getInternalName]).
     * @param access the access flag of the exported package, valid values are among `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param modules the fully qualified names (using dots) of the modules that can access the public
     * classes of the exported package, or null.
     */
    fun visitExport(packaze: String, access: Int, modules: Array<String>?) {}

    /**
     * Visits an open package of the current module.
     *
     * @param packaze the internal name of the opened package (see [Type.getInternalName]).
     * @param access the access flag of the opened package, valid values are among `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param modules the fully qualified names (using dots) of the modules that can use deep
     * reflection to the classes of the open package, or null.
     */
    fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {}

    /**
     * Visits a service used by the current module. The name must be the internal name of an interface
     * or a class.
     *
     * @param service the internal name of the service (see [Type.getInternalName]).
     */
    fun visitUse(service: String) {}

    /**
     * Visits an implementation of a service.
     *
     * @param service the internal name of the service (see [Type.getInternalName]).
     * @param providers the internal names (see [Type.getInternalName]) of the implementations
     * of the service (there is at least one provider).
     */
    fun visitProvide(service: String, providers: Array<String>) {}

    /**
     * Visits the end of the module. This method, which is the last one to be called, is used to
     * inform the visitor that everything have been visited.
     */
    fun visitEnd() {}

    class FromOw2(val ow2: org.objectweb.asm.ModuleVisitor) : ModuleVisitor {
        override fun visitMainClass(mainClass: String) {
            ow2.visitMainClass(mainClass)
        }

        override fun visitPackage(packaze: String) {
            ow2.visitPackage(packaze)
        }

        override fun visitRequire(module: String, access: Int, version: String?) {
            ow2.visitRequire(module, access, version)
        }

        override fun visitExport(packaze: String, access: Int, modules: Array<String>?) {
            ow2.visitExport(packaze, access, *(modules ?: emptyArray()))
        }

        override fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {
            ow2.visitOpen(packaze, access, *(modules ?: emptyArray()))
        }

        override fun visitUse(service: String) {
            ow2.visitUse(service)
        }

        override fun visitProvide(service: String, providers: Array<String>) {
            ow2.visitProvide(service, *providers)
        }

        override fun visitEnd() {
            ow2.visitEnd()
        }
    }

    class ToOw2(val toOw2: ModuleVisitor) :
        org.objectweb.asm.ModuleVisitor(org.objectweb.asm.Opcodes.ASM9) {
        override fun visitMainClass(mainClass: String) {
            toOw2.visitMainClass(mainClass)
        }

        override fun visitPackage(packaze: String) {
            toOw2.visitPackage(packaze)
        }

        override fun visitRequire(module: String, access: Int, version: String?) {
            toOw2.visitRequire(module, access, version)
        }

        override fun visitExport(packaze: String, access: Int, modules: Array<String>?) {
            toOw2.visitExport(packaze, access, modules)
        }

        override fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {
            toOw2.visitOpen(packaze, access, modules)
        }

        override fun visitUse(service: String) {
            toOw2.visitUse(service)
        }

        override fun visitProvide(service: String, providers: Array<String>) {
            toOw2.visitProvide(service, providers)
        }

        override fun visitEnd() {
            toOw2.visitEnd()
        }
    }
}
