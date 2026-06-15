package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.trashgen

const val DEFAULT_TRASH_CLASS_PROVIDER = "genesis"

object TrashClassProviderRegistry {
    private val providers = linkedMapOf<String, TrashClassProvider>()

    @Synchronized
    fun register(provider: TrashClassProvider) {
        val id = provider.id.trim()
        require(id.isNotEmpty()) { "Trash class provider id must not be empty" }
        providers[id] = provider
    }

    @Synchronized
    fun find(id: String): TrashClassProvider? {
        return providers[id.trim()]
    }

    @Synchronized
    fun ids(): List<String> {
        return providers.keys.toList()
    }
}
