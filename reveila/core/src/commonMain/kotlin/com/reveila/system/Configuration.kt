package com.reveila.system

import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil

open class Configuration {

    private val metaObjects: MutableList<MetaObject>

    constructor(jsonContent: String) {
        this.metaObjects = parse(jsonContent)
    }

    constructor(metaObjects: MutableList<MetaObject>) {
        this.metaObjects = metaObjects
    }

    open fun getMetaObjects(): List<MetaObject> = metaObjects

    @Throws(JsonException::class)
    @Suppress("UNCHECKED_CAST")
    private fun parse(jsonContent: String): MutableList<MetaObject> {
        var list: MutableList<Map<String, Any>>? = null
        try {
            val parsedList = JsonUtil.parseJsonStringToList(jsonContent)
            list = parsedList.toMutableList()
        } catch (e: Exception) {
            try {
                val single = JsonUtil.parseJsonStringToMap(jsonContent)
                list = mutableListOf(single)
            } catch (e2: Exception) {
                throw JsonException("Failed to parse configuration.", e2)
            }
        }

        if (list.isEmpty()) {
            throw JsonException("Malformed configuration.")
        }

        val mObjList = mutableListOf<MetaObject>()

        for (wrapper in list) {
            for (value in wrapper.values) {
                if (value is Map<*, *>) {
                    mObjList.add(MetaObject(value as Map<String, Any>))
                }
            }
        }

        return mObjList
    }

    open fun add(metaObject: MetaObject) {
        this.metaObjects.add(metaObject)
    }

    open fun remove(metaObject: MetaObject?) {
        if (metaObject != null) {
            this.metaObjects.remove(metaObject)
        }
    }

    open fun toJsonString(): String {
        val listToSave = this.metaObjects.map { it.dataMap }
        return JsonUtil.toPrettyJsonString(listToSave)
    }
}
