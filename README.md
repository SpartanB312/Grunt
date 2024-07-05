# Grunt Reborn

Grunt Reborn is a continuation of Grunt project witch is a free and open source lightweight obfuscator.

Some ideas are from ESkid and other obfuscator, and be presented with refined version.

Discord server link: https://discord.gg/ysB2fMfeYW

## Features

Notice: Many features from my private obfuscator Guardian (Made in 2022) are experimental which are unstable.

If you encounter any compatibility issues, please raise an issue on GitHub. By the way, I appended the stability level on each feature. If you encountered unstable situation. You can turn off the low stability-level features.

The reborn project is under development. Here is the feature TODO list.

Stability level: [1]=Unstable [5]=Stable

### Renamer:

* [X] [5]ClassRename
* [X] [5]FieldRename
* [X] [4]MethodRename
* [X] [5]LocalVarRename

### Minecraft:

* [X] [4]MixinClassRename
* [X] [4]MixinFieldRename

### Encrypt:

* [ ] [4]ArithmeticEncrypt
* [X] [5]NumberEncrypt
* [X] [5]FloatingPointEncrypt
* [X] [5]StringEncrypt

### Redirect:

* [X] [4]MethodCallRedirect
* [X] [4]FieldCallRedirect (Known as Scramble)
* [X] [5]StringEqualsRedirect
* [ ] [0]InvokeDynamic
* [ ] [2]ShuffleUtils

### Optimization:

* [X] [5]SourceDebugRemove
* [X] [5]EnumOptimization
* [X] [5]DeadCodeRemove
* [X] [5]KotlinOptimize
* [X] [4]Shrinking

### Miscellaneous:

* [ ] [0]ClassLoader
* [X] [5]Watermark
* [X] [5]NativeCandidate
* [X] [5]ShuffleMembers
* [X] [5]TrashClass
* [X] [5]ClonedClass
* [X] [5]SyntheticBridge
* [X] [5]PostProcess

### ControlFlow:

* [ ] [0]BlockSplit
* [ ] [0]LookupSwitch
* [ ] [0]ReplaceGoto
* [ ] [0]TableSwitch

## License: GNU General Public License 3.0

This is a free and open source software under GPLv3

The previous Grunt versions under MIT license (1.5.8 and before)
