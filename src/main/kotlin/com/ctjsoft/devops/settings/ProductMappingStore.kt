package com.ctjsoft.devops.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

data class ProductMapping(
    var key: String = "",
    var devProjectId: String = "",
    var devProjectName: String = "",
    var productId: String = "",
    var productName: String = "",
)

@Service(Service.Level.APP)
@State(name = "IssueLinkPushProductMappings", storages = [Storage("issueLinkPushMappings.xml")])
class ProductMappingStore : PersistentStateComponent<ProductMappingStore.Data> {
    data class Data(var mappings: MutableList<ProductMapping> = mutableListOf())

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) = XmlSerializerUtil.copyBean(state, data)

    @Synchronized
    fun get(key: String): ProductMapping? = data.mappings.firstOrNull { it.key == normalize(key) }

    @Synchronized
    fun put(key: String, devProjectId: String, devProjectName: String, productId: String, productName: String) {
        val normalized = normalize(key)
        data.mappings.removeAll { it.key == normalized }
        data.mappings.add(ProductMapping(normalized, devProjectId, devProjectName, productId, productName))
    }

    @Synchronized
    fun clear() = data.mappings.clear()

    private fun normalize(value: String): String = value.trim().replace('\\', '/').removeSuffix("/").removeSuffix(".git").lowercase()
}
