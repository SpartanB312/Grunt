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


## Field renamer exclusions (Grunteon 3)

Use JVM internal class names (`net/example/Example`), not dotted package names. For example:

```json
{
    "globalConfig": {
        "exclusions": ["net/example/api/**", "net/example/PublicFields"]
    },
    "transformers": [{
        "enabled": true,
        "config": {
            "type": "net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer.Config",
            "classFilter": {
                "includeStrategy": ["**"],
                "excludeStrategy": ["net/example/LocalFields"]
            },
            "fieldExclusions": ["net/example/Example.value", "net/example/Example.keep**"],
            "excludedNames": ["INSTANCE", "Companion"]
        }
    }]
}
```

- `globalConfig.exclusions` and `classFilter.excludeStrategy` preserve declared fields in matching classes.
  An empty `includeStrategy` selects no classes. Exact rules match only that class; a trailing `**` matches a prefix.
- `fieldExclusions` is optional and defaults to `[]`. Rules match `declaring/Owner.fieldName` exactly,
  or by prefix with a trailing `**`, for every descriptor with that name. Use the declaring owner for inherited fields.
  `Example.value` does not match `Example.valueExtra`; use `Example.value**` for both.
- `excludedNames` keeps exact bare field names in all classes; its defaults remain `INSTANCE` and `Companion`.
- These rules are not regex or general glob patterns. A package prefix needs the trailing `**`.
  Old Grunt JSON layouts and a transformer-level `exclusion` key are not aliases for these settings:
  unknown JSON keys are ignored, not migrated.
- References to renamed inherited fields must still be remapped, even when the referencing class is excluded.
  If a source field's mapping would also rename an excluded hiding declaration, the source field is conservatively
  kept too. Retained names are reserved to prevent generated names from hiding those fields.

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
