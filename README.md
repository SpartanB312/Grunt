# Gruntpocalypse

[![Version](https://img.shields.io/github/v/release/SpartanB312/Grunt)](https://github.com/SpartanB312/Grunt/releases)
[![CodeFactor](https://www.codefactor.io/repository/github/spartanb312/grunt/badge)](https://www.codefactor.io/repository/github/spartanb312/grunt)

Gruntpocalypse is a JVM bytecode obfuscator written in kotlin with 40+ features.

This project aims for stability and versatility. Some ideas are taken from other obfuscators.

Feel free to koin our Discord server for suggestions: https://discord.gg/ysB2fMfeYW

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
| **Renamer**                     | ClassRename          | 5     |                                                                                            |
|                                 | FieldRename          | 5     |                                                                                            |
|                                 | MethodRename         | 5     |                                                                                            |
|                                 | LocalVarRename       | 5     |                                                                                            |
|                                 | ReflectionSupport    | 3     | InterfaceOverlap, InvokeDynamic, FunctionalInterface                                       |
| **Minecraft**                   | MixinClassRename     | 4     |                                                                                            |
|                                 | MixinFieldRename     | 4     |                                                                                            |
|                                 | *MixinMethodRename   | -     |                                                                                            |
| **Encrypt**                     | ArithmeticEncrypt    | 4     |                                                                                            |
|                                 | ConstPoolEncrypt     | 5     |                                                                                            |
|                                 | NumberEncrypt        | 5     |                                                                                            |
|                                 | StringEncrypt        | 5     |                                                                                            |
| **Redirect**                    | MethodScramble       | 4     |                                                                                            |
|                                 | FieldScramble        | 5     |                                                                                            |
|                                 | StringEqualsRedirect | 5     |                                                                                            |
|                                 | InvokeDynamic        | 5     |                                                                                            |
| **Optimization**                | SourceDebugRemove    | 5     |                                                                                            |
|                                 | EnumOptimization     | 5     |                                                                                            |
|                                 | DeadCodeRemove       | 5     |                                                                                            |
|                                 | KotlinOptimize       | 5     |                                                                                            |
|                                 | Shrinking            | 4     |                                                                                            |
| **Miscellaneous**               | *AntiDebug           | -     |                                                                                            |
|                                 | Crasher              | 5     |                                                                                            |
|                                 | Watermark            | 5     |                                                                                            |
|                                 | NativeCandidate      | 5     |                                                                                            |
|                                 | ShuffleMembers       | 5     |                                                                                            |
|                                 | TrashClass           | 5     |                                                                                            |
|                                 | ClonedClass          | 5     |                                                                                            |
|                                 | SyntheticBridge      | 5     |                                                                                            |
|                                 | HWIDAuthentication   | 5     |                                                                                            |
|                                 | PostProcess          | 5     |                                                                                            |
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
| **ControlFlow - Miscellaneous** | ConstantBuilder      | 4     | (If, Switch) Use controlflow to build constants.                                           |
|                                 | SwitchProtector      | 4     | (Switch) Hide real keys of switches.                                                       |

#### * Not implemented yet.

## License

This is a free and open source software, licensed under GPLv3.

The previous Grunt versions are licensed under the MIT license (1.5.8 and before).

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)
