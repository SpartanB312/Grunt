# Gruntpocalypse

[![Version](https://img.shields.io/github/v/release/SpartanB312/Grunt)](https://github.com/SpartanB312/Grunt/releases)
[![CodeFactor](https://www.codefactor.io/repository/github/spartanb312/grunt/badge)](https://www.codefactor.io/repository/github/spartanb312/grunt)

Gruntpocalypse is a JVM bytecode obfuscator written in kotlin with 40+ features.

This project aims for stability and versatility. Some ideas are taken from other obfuscators.

Feel free to join our Discord server for suggestions: https://discord.gg/ysB2fMfeYW

#### Grunt 2.5 will be the last version of Grunt 2, which comes in April 2025.
#### Grunt 3 project will be launched later in 2025.

## Compatibility

100% passed [JVM Obfuscation Test](https://github.com/sim0n/jvm-obfuscation-tester)

If you encounter any compatibility issues, please open a GitHub Issue or join the Discord to help us improve Grunt.

## Important

Controlflow requires ComputeMaxs disabled.
Ensure all project dependencies are included for stability and intensity.
If dependencies are unavailable, enable <code>useComputeMaxs</code> in Global settings as a last resort.
Address <code>VerifyError</code> by completing dependencies first.
Only enable <code>computeMaxs</code> and disable controlflow obfuscation when dependencies are mostly unresolvable.

## Features

Stability level: 1 = Unstable; 5 = Stable

| Category                        | Feature              | Level | Description                                                                                |
|---------------------------------|----------------------|-------|--------------------------------------------------------------------------------------------|
| **Renamer**                     | ClassRename          | 5     | Class renaming                                                                             |
|                                 | FieldRename          | 5     | Field renaming                                                                             |
|                                 | MethodRename         | 5     | Method renaming. Support InterfaceOverlap, InvokeDynamic, FunctionalInterface              |
|                                 | LocalVarRename       | 5     | Local variable renaming                                                                    |
|                                 | ReflectionSupport    | 3     | Reflection remapping after renaming                                                        |
| **Minecraft**                   | MixinClassRename     | 4     | Class renaming for Mixin class                                                             |
|                                 | MixinFieldRename     | 4     | Field renaming for Mixin class                                                             |
|                                 | *MixinMethodRename   | -     | Method renaming for Mixin class                                                            |
| **Encrypt**                     | ArithmeticEncrypt    | 4     | Replace arithmetic operation to its substitution                                           |
|                                 | ConstPoolEncrypt     | 5     | Encrypt constants in class constant pool                                                   |
|                                 | NumberEncrypt        | 5     | Encrypt numbers in different ways                                                          |
|                                 | StringEncrypt        | 5     | Encrypt strings in different ways                                                          |
| **Redirect**                    | MethodScramble       | 4     | Generate method call proxy                                                                 |
|                                 | FieldScramble        | 5     | Generate field call proxy                                                                  |
|                                 | StringEqualsRedirect | 5     | Replace string eqauls                                                                      |
|                                 | InvokeDynamic        | 5     | Encrypt method call via invoke dynamic                                                     |
| **Optimization**                | SourceDebugRemove    | 5     | Remove source debug information                                                            |
|                                 | EnumOptimization     | 5     | Optimize enum classes                                                                      |
|                                 | DeadCodeRemove       | 5     | Remove redundant dead codes                                                                |
|                                 | KotlinOptimize       | 5     | Kotlin intrinsincs/metadata optimizer                                                      |
|                                 | Shrinking            | 4     | Code shrinking                                                                             |
| **Miscellaneous**               | *AntiDebug           | -     | Insert anti debug checker                                                                  |
|                                 | Crasher              | 5     | Crash the decompiler                                                                       |
|                                 | Watermark            | 5     | Insert watermark                                                                           |
|                                 | NativeCandidate      | 5     | Automatically append annotation for candidates                                             |
|                                 | ShuffleMembers       | 5     | Shuffle members in class                                                                   |
|                                 | TrashClass           | 5     | Generate trash classes                                                                     |
|                                 | ClonedClass          | 5     | Clone trash classes                                                                        |
|                                 | SyntheticBridge      | 5     | Insert synthetic and bridge flag to hide code                                              |
|                                 | HWIDAuthentication   | 5     | Insert HardwareID authenticator                                                            |
|                                 | PostProcess          | 5     | Remapping related files in jar                                                             |
|                                 | DeclareFields        | 5     | Move field declarations into the init/clinit of classes.                                   |
| **Plugins**                     | *NoverifyHackery     | -     | Force noverify and hackery bytecodes. [Reference](https://github.com/char/noverify-hackery) |
|                                 | RemoteLoader         | 4     | Remote authentication and constant class loading services.                                 |
|                                 | VersionPatcher       | 2     | Downgrade class version (Java 6 to Java 5) for Windows 98.                                 |
| **ControlFlow - General**       | RandomArithmeticExpr | 5     | (Seed) Hide seed operations to anti-simulation.                                            |
|                                 | BogusConditionJump   | 4     | (If, Goto) Generate fake jumps with random junk codes.                                     |
|                                 | MangledCompareJump   | 4     | (If, Goto) Generate conditional jumps with junk codes.                                     |
|                                 | ReversedIfJump       | 5     | (If, Goto) Random mutation to jump conditions.                                             |
|                                 | *ParallelBranch      | -     | (If, Goto) Generate junk parallel branches.                                                |
|                                 | TableSwitchJump      | 4     | (Switch) Generate TableSwitch to replace direct jumps.                                     |
|                                 | TrappedSwitchCase    | 4     | (Switch) Random trapped jumps or weird loops between switch cases.                         |
|                                 | JunkCodeGenerator    | 4     | (JunkCode) Generate junk calls.                                                            |
|                                 | SwitchExtractor      | 5     | (Switch) Replace switches with blocks.                                                     |
|                                 | MutateJumps          | 5     | (Switch) Generate TableSwitch for compare statements.                                      |
|                                 | ChaosSwitchJump      | 4     | (Switch) Generate illegal loop jumps to replace direct jumps                               |
| **ControlFlow - Miscellaneous** | ConstantBuilder      | 4     | (If, Switch) Use controlflow to build constants.                                           |
|                                 | SwitchProtector      | 4     | (Switch) Hide real keys of switches.                                                       |

#### * Not implemented yet.

## License

This is a free and open source software, licensed under LGPL 3.0

The previous Grunt versions are licensed under the MIT license (1.5.8 and before).

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)
