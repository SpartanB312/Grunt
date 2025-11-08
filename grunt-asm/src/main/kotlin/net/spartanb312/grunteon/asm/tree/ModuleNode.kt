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

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes

/**
 * A node that represents a module declaration.
 *
 * @author Remi Forax
 */
class ModuleNode : ModuleVisitor {
    /** The fully qualified name (using dots) of this module.  */
    var name: String?

    /**
     * The module's access flags, among `ACC_OPEN`, `ACC_SYNTHETIC` and `ACC_MANDATED`.
     */
    var access: Int

    /** The version of this module. May be null.  */
    var version: String?

    /**
     * The internal name of the main class of this module (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var mainClass: String? = null

    /**
     * The internal name of the packages declared by this module (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var packages: MutableList<String?>? = null

    /** The dependencies of this module. May be null.  */
    var requires: MutableList<ModuleRequireNode?>? = null

    /** The packages exported by this module. May be null.  */
    var exports: MutableList<ModuleExportNode?>? = null

    /** The packages opened by this module. May be null.  */
    var opens: MutableList<ModuleOpenNode?>? = null

    /**
     * The internal names of the services used by this module (see [ ][org.objectweb.asm.Type.getInternalName]). May be null.
     */
    var uses: MutableList<String?>? = null

    /** The services provided by this module. May be null.  */
    var provides: MutableList<ModuleProvideNode?>? = null

    /**
     * Constructs a [ModuleNode]. *Subclasses must not use this constructor*. Instead, they
     * must use the [.ModuleNode] version.
     *
     * @param name the fully qualified name (using dots) of the module.
     * @param access the module access flags, among `ACC_OPEN`, `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param version the module version, or null.
     * @throws IllegalStateException If a subclass calls this constructor.
     */
    constructor(name: String?, access: Int, version: String?) : super( /* latest api = */Opcodes.ASM9) {
        check(javaClass == ModuleNode::class.java)
        this.name = name
        this.access = access
        this.version = version
    }

    // TODO(forax): why is there no 'mainClass' and 'packages' parameters in this constructor?
    /**
     * Constructs a [ModuleNode].
     *
     * @param api the ASM API version implemented by this visitor. Must be one of [     ][Opcodes.ASM6], [Opcodes.ASM7], [Opcodes.ASM8] or [Opcodes.ASM9].
     * @param name the fully qualified name (using dots) of the module.
     * @param access the module access flags, among `ACC_OPEN`, `ACC_SYNTHETIC` and `ACC_MANDATED`.
     * @param version the module version, or null.
     * @param requires The dependencies of this module. May be null.
     * @param exports The packages exported by this module. May be null.
     * @param opens The packages opened by this module. May be null.
     * @param uses The internal names of the services used by this module (see [     ][org.objectweb.asm.Type.getInternalName]). May be null.
     * @param provides The services provided by this module. May be null.
     */
    constructor(
        api: Int,
        name: String?,
        access: Int,
        version: String?,
        requires: MutableList<ModuleRequireNode?>?,
        exports: MutableList<ModuleExportNode?>?,
        opens: MutableList<ModuleOpenNode?>?,
        uses: MutableList<String?>?,
        provides: MutableList<ModuleProvideNode?>?
    ) : super(api) {
        this.name = name
        this.access = access
        this.version = version
        this.requires = requires
        this.exports = exports
        this.opens = opens
        this.uses = uses
        this.provides = provides
    }

    override fun visitMainClass(mainClass: String?) {
        this.mainClass = mainClass
    }

    override fun visitPackage(packaze: String?) {
        if (packages == null) {
            packages = ArrayList<String?>(5)
        }
        packages!!.add(packaze)
    }

    override fun visitRequire(module: String?, access: Int, version: String?) {
        if (requires == null) {
            requires = ArrayList<ModuleRequireNode?>(5)
        }
        requires!!.add(ModuleRequireNode(module, access, version))
    }

    override fun visitExport(packaze: String?, access: Int, vararg modules: String?) {
        if (exports == null) {
            exports = ArrayList<ModuleExportNode?>(5)
        }
        exports!!.add(ModuleExportNode(packaze, access, Util.asArrayList<String?>(modules)))
    }

    override fun visitOpen(packaze: String?, access: Int, vararg modules: String?) {
        if (opens == null) {
            opens = ArrayList<ModuleOpenNode?>(5)
        }
        opens!!.add(ModuleOpenNode(packaze, access, Util.asArrayList<String?>(modules)))
    }

    override fun visitUse(service: String?) {
        if (uses == null) {
            uses = ArrayList<String?>(5)
        }
        uses!!.add(service)
    }

    override fun visitProvide(service: String?, vararg providers: String?) {
        if (provides == null) {
            provides = ArrayList<ModuleProvideNode?>(5)
        }
        provides!!.add(ModuleProvideNode(service, Util.asArrayList<String?>(providers)))
    }

    override fun visitEnd() {
        // Nothing to do.
    }

    /**
     * Makes the given class visitor visit this module.
     *
     * @param classVisitor a class visitor.
     */
    fun accept(classVisitor: ClassVisitor) {
        val moduleVisitor = classVisitor.visitModule(name, access, version)
        if (moduleVisitor == null) {
            return
        }
        if (mainClass != null) {
            moduleVisitor.visitMainClass(mainClass)
        }
        if (packages != null) {
            var i = 0
            val n = packages!!.size
            while (i < n) {
                moduleVisitor.visitPackage(packages!!.get(i))
                i++
            }
        }
        if (requires != null) {
            var i = 0
            val n = requires!!.size
            while (i < n) {
                requires!!.get(i)!!.accept(moduleVisitor)
                i++
            }
        }
        if (exports != null) {
            var i = 0
            val n = exports!!.size
            while (i < n) {
                exports!!.get(i)!!.accept(moduleVisitor)
                i++
            }
        }
        if (opens != null) {
            var i = 0
            val n = opens!!.size
            while (i < n) {
                opens!!.get(i)!!.accept(moduleVisitor)
                i++
            }
        }
        if (uses != null) {
            var i = 0
            val n = uses!!.size
            while (i < n) {
                moduleVisitor.visitUse(uses!!.get(i))
                i++
            }
        }
        if (provides != null) {
            var i = 0
            val n = provides!!.size
            while (i < n) {
                provides!!.get(i)!!.accept(moduleVisitor)
                i++
            }
        }
    }
}
