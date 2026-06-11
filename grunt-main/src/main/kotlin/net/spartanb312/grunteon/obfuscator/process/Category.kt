package net.spartanb312.grunteon.obfuscator.process

enum class Category(val desc: String) {
    Encryption("encryption"),
    Controlflow("controlflow"),
    AntiDebug("anti_debug"),
    Authentication("authentication"),
    Exploit("exploit"),
    Miscellaneous("miscellaneous"),
    Optimization("optimization"),
    Redirect("redirect"),
    Renaming("renaming"),
    Other("other"),
    PostProcess("postprocess"),
}
