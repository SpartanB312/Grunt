# Gruntpocalypse

[![CodeFactor](https://www.codefactor.io/repository/github/spartanb312/grunt/badge)](https://www.codefactor.io/repository/github/spartanb312/grunt)

Gruntpocalypse is a jvm bytecode obfuscator written in kotlin with 30+ features.

This project aims for stability and versatility. Some ideas are from other obfuscators.

Join our Discord server and provide your suggestions: https://discord.gg/ysB2fMfeYW

#### Grunt 2.5 will be the last version of Grunt 2, which coming in 2025 Apr
#### Grunt 3 project will be launched later in 2025

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

* [X] [5] ClassRename
* [X] [5] FieldRename
* [X] [5] MethodRename
* [X] [5] LocalVarRename
* [X] [3] ReflectionSupport 

  The method renamer supports InterfaceOverlap, InvokeDynamic, and FunctionalInterface check.

  InterfaceOverlap: A class extends/implements more than 2 class/interfaces with same method name and descriptor.
  (Example: A implements B and C, B and C both are independent interface and have method invoke(I)J.)

### Minecraft

* [X] [4] MixinClassRename
* [X] [4] MixinFieldRename
* [ ] [4] MixinMethodRename

### Encrypt

* [X] [4] ArithmeticEncrypt
* [X] [5] ConstPoolEncrypt
* [X] [5] NumberEncrypt
* [X] [5] StringEncrypt

### Redirect

* [X] [4] MethodScramble
* [X] [5] FieldScramble
* [X] [5] StringEqualsRedirect
* [X] [5] InvokeDynamic

### Optimization

* [X] [5] SourceDebugRemove
* [X] [5] EnumOptimization
* [X] [5] DeadCodeRemove
* [X] [5] KotlinOptimize
* [X] [4] Shrinking

### Miscellaneous

* [ ] [5] AntiDebug
* [X] [5] Crasher
* [X] [5] Watermark
* [X] [5] NativeCandidate
* [X] [5] ShuffleMembers
* [X] [5] TrashClass
* [X] [5] ClonedClass
* [X] [5] SyntheticBridge
* [X] [5] HWIDAuthentication
* [X] [5] PostProcess
* [X] [5] DeclareFields

### Plugins

* [ ] [3] NoverifyHackery (Misc) Force noverify and hackery bytecodes [(reference)](https://github.com/char/noverify-hackery)
* [X] [4] RemoteLoader (Misc) Remote authentication and constant class loading services
* [X] [2] VersionPatcher (Misc) Downgrade the class version from java6 to java5 for Windows 98

### ControlFlow

#### General

* [X] [5] RandomArithmeticExpr (Seed) Hide the seed operations to anti-simulation
* [X] [4] BogusConditionJump (If, Goto) Generate fake jumps with random junk codes
* [X] [4] MangledCompareJump (If, Goto) Generate conditional jump with junk codes to replace direct jump
* [X] [5] ReversedIfJump (If, Goto) Random mutation to the jump condition
* [ ] [4] ParallelBranch (If, Goto) Generate junk parallel branch to prevent inference from returns
* [X] [4] TableSwitchJump (Switch) Generate TableSwitch to replace direct jumps
* [X] [4] TrappedSwitchCase (Switch) Random trapped jumps or weird loops between switch cases
* [X] [4] JunkCodeGenerator (JunkCode) Generate junk calls
* [X] [5] SwitchExtractor (Switch) Replace switches with blocks
* [X] [5] MutateJumps (Switch) Generate TableSwitch to replace compare statements

#### Miscellaneous

* [X] [4] ConstantBuilder (If, Switch) Using controlflow to build constants
* [X] [4] SwitchProtector (Switch) Hide the real keys of switch

## License: GNU General Public License 3.0

This is a free and open source software, licensed under GPLv3.

The previous Grunt versions are licensed under the MIT license (1.5.8 and before).

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)
