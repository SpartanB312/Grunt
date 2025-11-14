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

import net.spartanb312.grunteon.asm.ClassVisitor
import net.spartanb312.grunteon.asm.ModuleVisitor

/** A node that represents a module declaration. */
interface ModuleNode : Node {
    val name: String
    val access: Int
    val version: String?
    val mainClass: String?
    val packages: List<String>
    val requires: List<ModuleRequireNode>
    val exports: List<ModuleExportNode>
    val opens: List<ModuleOpenNode>
    val uses: List<String>
    val provides: List<ModuleProvideNode>

    fun accept(classVisitor: ClassVisitor) {
        val moduleVisitor = classVisitor.visitModule(name, access, version) ?: return

        mainClass?.let { moduleVisitor.visitMainClass(it) }
        packages.forEach { packaze ->
            moduleVisitor.visitPackage(packaze)
        }
        requires.forEach { require ->
            moduleVisitor.visitRequire(require.module, require.access, require.version)
        }
        exports.forEach { export ->
            moduleVisitor.visitExport(export.packaze, export.access, export.modules.toTypedArray())
        }
        opens.forEach { open ->
            moduleVisitor.visitOpen(open.packaze, open.access, open.modules.toTypedArray())
        }
        uses.forEach { service ->
            moduleVisitor.visitUse(service)
        }
        provides.forEach { provide ->
            moduleVisitor.visitProvide(provide.service, provide.providers.toTypedArray())
        }
    }
}

interface MutableModuleNode : ModuleNode, ModuleVisitor {
    override var name: String
    override var access: Int
    override var version: String?
    override var mainClass: String?
    override val packages: MutableList<String>
    override val requires: MutableList<ModuleRequireNode>
    override val exports: MutableList<ModuleExportNode>
    override val opens: MutableList<ModuleOpenNode>
    override val uses: MutableList<String>
    override val provides: MutableList<ModuleProvideNode>

    override fun visitMainClass(mainClass: String) {
        this.mainClass = mainClass
    }

    override fun visitPackage(packaze: String) {
        packages.add(packaze)
    }

    override fun visitRequire(module: String, access: Int, version: String?) {
        requires.add(nodeFactory.ModuleRequire(module, access, version))
    }

    override fun visitExport(packaze: String, access: Int, modules: Array<String>?) {
        exports.add(nodeFactory.ModuleExport(packaze, access, modules?.toMutableList() ?: mutableListOf()))
    }


    override fun visitOpen(packaze: String, access: Int, modules: Array<String>?) {
        opens.add(nodeFactory.ModuleOpen(packaze, access, modules?.toMutableList() ?: mutableListOf()))
    }

    override fun visitUse(service: String) {
        uses.add(service)
    }

    override fun visitProvide(service: String, providers: Array<String>) {
        provides.add(nodeFactory.ModuleProvide(service, providers.toMutableList()))
    }

    override fun visitEnd() {
        // Nothing to do.
    }
}
