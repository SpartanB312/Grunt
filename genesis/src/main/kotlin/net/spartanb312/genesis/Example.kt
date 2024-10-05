package net.spartanb312.genesis

import net.spartanb312.genesis.extensions.*
import net.spartanb312.genesis.extensions.insn.*
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.random.Random

fun main() {
    val myClass = clazz(
        PUBLIC + FINAL,
        "Main",
    ) {
        +clinit {
            INSTRUCTIONS {
                GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;")
                LDC("Hello world!")
                INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
                RETURN
            }


            +instructions {

            }

            +instructions {

            }

            MAXS(2, 0)
        }

        CLINIT(2, 0) {
            GETSTATIC("java/lang/System", "out", "Ljava/io/PrintStream;")
            LDC("Hello world!")
            INVOKEVIRTUAL("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
            RETURN
        }
    }

    val existed = ClassNode()
    existed.modify {
        val created = method(
            PRIVATE + STATIC,
            "check",
            "(Ljava/lang/String;)Z"
        ) {
            INSTRUCTIONS {
                val label1 = Label()
                INT(114514)
                INT(1919810)
                IXOR
                INT(69420)
                IF_ICMPEQ(label1)
                ICONST_0
                IRETURN
                LABEL(label1)
                ICONST_1
                IRETURN

                val def = Label()
                val pair = arrayOf(
                    123456 to Label(),
                    786786 to Label(),
                    987893 to Label()
                ).sortedBy { it.first }.toTypedArray()

                LOOKUPSWITCH(
                    def,
                    Random.nextInt() to Label(),
                    Random.nextInt() to Label(),
                    Random.nextInt() to Label(),
                    Random.nextInt() to Label()
                )
            }
        }
        +created
        instructions {
            INVOKESTATIC(existed, created)
        }
    }

}



