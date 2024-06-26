# Grunt Reborn

Grunt Reborn is a continuation of Grunt project witch is a free and open source lightweight obfuscator.

Some ideas are from ESkid and other obfuscator, and be presented with refined version.

## Features

Notice: Many features from my private obfuscator Guardian (Made in 2022) are experimental which are unstable.

14 features from Grunt, 11 features from Guardian, 7 new features in coming.

If you encounter any compatibility issues, please raise an issue on GitHub.

The reborn project is under development. Here is the feature TODO list.

### Renamer:

* [X] ClassRename
* [X] FieldRename
* [X] MethodRename
* [X] LocalVarRename

### Minecraft:

* [ ] MixinClassRename [Guardian]
* [ ] MixinFieldRename [Guardian]
* [ ] MixinMethodRename [Guardian]

### Encrypt:

* [ ] ArithmeticEncrypt [Guardian]
* [X] NumberEncrypt
* [X] StringEncrypt

### Redirect:

* [ ] MethodCallRedirect [Guardian]
* [X] InitializerRedirect [Guardian]
* [X] FieldCallRedirect (Known as Scramble)
* [X] StringEqualsRedirect [Guardian]
* [ ] InvokeDynamic [New]
* [ ] MergeUtils [Guardian] (Will generate many methods)

### Optimization:

* [X] SourceDebugRemove
* [X] EnumOptimization [Guardian]
* [X] DeadCodeRemove [Guardian]
* [X] KotlinOptimize
* [X] Shrinking

### Miscellaneous:

* [ ] CustomClassLoader [New]
* [X] Watermark
* [X] NativeCandidate
* [X] ShuffleMembers
* [X] TrashClass [Guardian]
* [X] ClonedClass [New]
* [X] SyntheticBridge
* [X] PostProcess

### ControlFlow:

* [ ] BlockSplit [New]
* [ ] LookupSwitch [New]
* [ ] ReplaceGoto [New]
* [ ] TableSwitch [New]

## License: GNU General Public License 3.0

This is a free and open source software under GPLv3

The previous Grunt versions under MIT license (1.5.8 and before)