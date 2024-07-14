# Gruntpocalypse

Gruntpocalypse (Grunt Reborn) is a continuation of Grunt project.

This is a free and open source lightweight obfuscator aiming for stability and versatility.

Some ideas are from other obfuscator, and be presented with refined version.

Discord server link: https://discord.gg/ysB2fMfeYW

## Features

If you encounter any compatibility issues, please raise an issue on GitHub.

By the way, I appended the stability level on each feature. If you encountered unstable situation. You can turn off the
low stability-level features.

The reborn project is under development. Here is the feature TODO list.

Stability level: [1]=Unstable [5]=Stable

## Notice

The dependencies of your project is highly required to ensure stability and intensity. You should add all of them to your config. (Especially method and field renamer, which'll search all source methods and invoke dynamic insn for safe renaming. If the one class on the hierarchy tree missing dependencies, the whole hierarchy tree will be excluded).

If most of the project dependencies are unavailable for some reasons. I suggest you to enable useComputeMaxs in Global setting to ensure stability as much as possible. (If you encountered VerifyError, You should first try to complete the dependencies. It is recommended to only enable computeMaxs when dependencies cannot be completed)

### Renamer:

* [X] [5]ClassRename
* [X] [5]FieldRename
* [X] [5]MethodRename
* [X] [5]LocalVarRename

  The method renamer support MultiSource, InvokeDynamic, and FunctionalInterface check. 
 
  MultiSource: A class extends/implements more than 2 class/interfaces with same method name and descriptor. (Example: A implements B and C, B and C both are independent interface and have method invoke(I)J.)

  That's why Grunt is one of the few obfuscators that can stably support interface method renaming

### Minecraft:

* [X] [4]MixinClassRename
* [X] [4]MixinFieldRename

### Encrypt:

* [X] [4]ArithmeticEncrypt
* [X] [5]NumberEncrypt
* [X] [5]FloatingPointEncrypt
* [X] [5]StringEncrypt

### Redirect:

* [X] [4]MethodScramble
* [X] [5]FieldScramble
* [X] [5]StringEqualsRedirect
* [X] [5]InvokeDynamic

### Optimization:

* [X] [5]SourceDebugRemove
* [X] [5]EnumOptimization
* [X] [5]DeadCodeRemove
* [X] [5]KotlinOptimize
* [X] [4]Shrinking

### Miscellaneous:

* [X] [5]Watermark
* [X] [5]NativeCandidate
* [X] [5]ShuffleMembers
* [X] [5]TrashClass
* [X] [5]ClonedClass
* [X] [5]SyntheticBridge
* [X] [5]PostProcess

### ControlFlow:

* [X] [4]ImplicitJump

## License: GNU General Public License 3.0

This is a free and open source software under GPLv3

The previous Grunt versions under MIT license (1.5.8 and before)
