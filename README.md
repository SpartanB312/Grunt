# Gruntpocalypse

[![CodeFactor](https://www.codefactor.io/repository/github/spartanb312/grunt/badge)](https://www.codefactor.io/repository/github/spartanb312/grunt)

Gruntpocalypse is a jvm bytecode obfuscator written in kotlin with 30+ transformers.

This project aims for stability and versatility. Some ideas are from other obfuscators.

Join our Discord server and provide your suggestions: https://discord.gg/ysB2fMfeYW

## Compatibility

100% passed [JVM Obfuscation Test](https://github.com/sim0n/jvm-obfuscation-tester)

If you encounter any compatibility issues, please raise an issue on GitHub to help us improve Grunt.

By the way, I appended the stability level on each feature. If you encountered unstable situation. You can turn off the
low stability-level features.

Stability level: [1]=Unstable [5]=Stable

## Notice

Controlflow requires ComputeMaxs disabled. Please ensure your included classes with full dependencies.

The dependencies of your project is highly required to ensure stability and intensity. If most of the project
dependencies are unavailable for some reasons. I suggest you to enable useComputeMaxs in Global setting to ensure
stability as much as possible. (If you encountered VerifyError, You should first try to complete the dependencies. It is
recommended to only enable computeMaxs and disable controlflow obfuscation when most dependencies cannot be completed)

## Features

### Renamer

* [X] [5]ClassRename
* [X] [5]FieldRename
* [X] [5]MethodRename
* [X] [5]LocalVarRename

  The method renamer support InterfaceOverlap, InvokeDynamic, and FunctionalInterface check.

  InterfaceOverlap: A class extends/implements more than 2 class/interfaces with same method name and descriptor. (
  Example: A implements B and C, B and C both are independent interface and have method invoke(I)J.)

### Minecraft

* [X] [4]MixinClassRename
* [X] [4]MixinFieldRename

### Encrypt

* [X] [4]ArithmeticEncrypt
* [X] [5]ConstPoolEncrypt
* [X] [5]NumberEncrypt
* [X] [5]FloatingPointEncrypt
* [X] [5]StringEncrypt
* [X] [1]StringSwitch

### Redirect

* [X] [4]MethodScramble
* [X] [5]FieldScramble
* [X] [5]StringEqualsRedirect
* [X] [5]InvokeDynamic

### Optimization

* [X] [5]SourceDebugRemove
* [X] [5]EnumOptimization
* [X] [5]DeadCodeRemove
* [X] [5]KotlinOptimize
* [X] [4]Shrinking

### Miscellaneous

* [X] [5]Crasher
* [X] [5]Watermark
* [X] [5]NativeCandidate
* [X] [5]ShuffleMembers
* [X] [5]TrashClass
* [X] [5]ClonedClass
* [X] [5]SyntheticBridge
* [X] [5]HWIDAuthentication
* [X] [5]PostProcess

### ControlFlow

* [X] [4]BogusConditionJump (If, Goto) Generate fake jumps with random junk codes
* [X] [4]MangledCompareJump (If, Goto) Generate random conditional jump with junk codes for direct jump
* [X] [4]ReversedIfJump (If, Goto) Random mutation to the jump condition
* [X] [4]TableSwitchJump (Switch) Generate TableSwitch to replace direct jumps
* [X] [4]UnconditionalLoop (Switch) Random weird loops between switch labels
* [X] [4]TrappedSwitchCase (Switch) Random trapped switch junk cases
* [X] [4]JunkCodeGenerator (JunkCode) Generate junk calls
* [ ] [0] (Provide ur suggestion in Discord)

## License: GNU General Public License 3.0

This is a free and open source software under GPLv3

The previous Grunt versions under MIT license (1.5.8 and before)
