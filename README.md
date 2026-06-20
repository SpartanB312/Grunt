# Grunteon

Grunteon is the third generation of Grunt. A high concurrency JVM bytecode obfuscator framework written in kotlin.

This project is under development starting from November 2025. 

Feel free to join our Discord server for suggestions: https://discord.gg/ysB2fMfeYW

QQ-chat group: 554702632

## Features

Working in progress. The following is a list of features that have been completed or are currently being developed in
the near future

### Framework

* [X] Parallel pipeline
* [X] Filter system
* [X] Compose fluent UI
* [X] SSA-IR / Flow-IR
* [X] Native obfuscation

### Native obfuscation

* [X] SSA-IR direct to cpp
* [X] JVM bytecode to cpp
* [X] Native candidate scanner
* [X] Native method validator
* [X] Native runtime bridges
* [X] Native handle caches
* [X] Native intrinsics
* [X] Integrated workflow

### Controlflow flattening

* [X] Verifier
* [X] Dispatcher protect
* [X] Bogus jump/loop
* [X] Shuffle blocks
* [X] Dispatcher trailing block
* [X] Anti-static simulation
* [X] Shared terminator
* [X] Junk code

### Controlflow jump

* [X] Verifier
* [X] Bogus jump
* [X] Mangled jump
* [X] Exception bridge
* [X] Dispatcher landing block
* [X] Anti-static simulation
* [X] Runtime dynamic predicate
* [X] Shared terminator
* [X] Junk code

### Encrypt

* [X] Number encryption
* [X] String encryption
* [X] Arithmetic substitution
* [ ] ConstPool extractor

### Miscellaneous

* [X] Declared fields extractor
* [X] Parameter obfuscation
* [X] Trash class generator
* [ ] HardwareID authenticator

### Anti debug

* [X] Runtime material
* [X] AntiLLM

### Optimize

* [X] Class shrinking
* [X] Dead code remove
* [X] Enum optimize
* [X] Kotlin class shrinking
* [X] Method inliner
* [X] Source debug info hide
* [X] String equals optimize

### Other

* [X] Decompiler crasher
* [X] Fake synthetic bridge
* [X] Reference obfuscate
* [X] Reflection support
* [X] Shuffle members
* [X] Watermark

### Redirect

* [X] Field access proxy
* [X] Invoke proxy
* [X] Invoke dispatcher

### Rename

* [X] Class renamer
* [X] Field renamer
* [X] Method renamer
* [X] LocalVar renamer
* [X] Mixin renamer


## License

Grunteon is a free and open source obfuscator framework licensed under Apache License 2.0

Yapyap is a grunt extension pack licensed under PolyForm Strict License 1.0.0

Grunteon nativecode licensed under PolyForm Strict License 1.0.0

The license of each Grunt version：

| Generation     | Versions    | Aim of obfuscation            | License  | Commercial Use |
|----------------|-------------|-------------------------------|----------|----------------|
| Grunt          | 1.0.0-1.5.x | Lightweight and stability     | MIT      | Allowed        |
| Gruntpocalypse | 2.0.0-2.5.x | Diversity and intensity       | LGPL3    | Restricted     |
| Grunteon       | 3.0.0-      | Industrial-aimed and strength | Apache2* | Allowed        |

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)

![Alt](https://repobeats.axiom.co/api/embed/ea2d273dbb7a9cb21f070102f01e31234afc2627.svg "Repobeats analytics image")
