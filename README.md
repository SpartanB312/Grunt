# Grunt Reborn

Grunt Reborn is a continuation of Grunt project witch is a free and open source lightweight obfuscator.

## Features

Notice: Many features are not stable. If you encounter any compatibility issues, please raise an Issue.

The reborn project is under development. Here is the feature TODO list.

### Renamer:

* [X] ClassRename
* [X] FieldRename
* [X] MethodRename
* [X] LocalVarRename

### Minecraft:

* [ ] MixinClassRename
* [ ] MixinFieldRename
* [ ] MixinMethodRename
* [ ] CustomClassLoader

### Encrypt:

* [ ] ArithmeticEncrypt
* [ ] FloatingPointEncrypt
* [ ] NumberEncrypt
* [ ] StringEncrypt

### Redirect:

* [ ] MethodCallRedirect
* [ ] InitializerRedirect
* [ ] FieldCallRedirect (Known as Scramble)
* [ ] StringEqualsRedirect
* [ ] InvokeDynamic

### Optimization:

* [X] SourceDebugRemove
* [X] KotlinOptimize
* [X] Shrinking

### Miscellaneous:

* [X] Watermark
* [ ] NativeCandidate
* [X] ShuffleMembers
* [ ] TrashClass
* [X] ClonedClass
* [X] SyntheticBridge
* [ ] PostProcess

### ControlFlow:

* [ ] BlockSplit
* [ ] LookupSwitch
* [ ] ReplaceGoto
* [ ] TableSwitch

## License: Apache License 2.0

This is a free and open source software under Apache License 2.0

The previous Grunt versions under MIT license (1.5.8 and before)