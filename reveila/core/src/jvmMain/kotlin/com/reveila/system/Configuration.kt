package com.reveila.system

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Objects
import java.util.stream.Collectors
import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil

open class Configuration {

    private var metaObjects: MutableList<MetaObject>

    @Throws(IOException::class, JsonException::class)
    constructor(inputStream: InputStream) {
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            val jsonContent = reader.lines().collect(Collectors.joining(System.lineSeparator()))
            this.metaObjects = parse(jsonContent)
        }
    }

    constructor(metaObjects: MutableList<MetaObject>) {
        this.metaObjects = metaObjects
    }

    open fun getMetaObjects(): List<MetaObject> = metaObjects

    @Synchronized
    @Throws(JsonException::class)
    @Suppress("UNCHECKED_CAST")
    private fun parse(jsonContent: String): MutableList<MetaObject> {
        var list: MutableList<Map<String, Any>>? = null
        try {
            val parsedList = JsonUtil.parseJsonStringToList(jsonContent)
            list = parsedList as? MutableList<Map<String, Any>>
        } catch (e: Exception) {
            try {
                val single = JsonUtil.parseJsonStringToMap(jsonContent)
                list = ArrayList()
                list.add(single as Map<String, Any>)
            } catch (e2: Exception) {
                throw JsonException("Failed to parse configuration.", e2)
            }
        }

        if (list == null || list.isEmpty()) {
            throw JsonException("Mulformed configuration.")
        }

        val mObjList = ArrayList<MetaObject>()

        for (wrapper in list) {
            val values = wrapper.values
            for (value in values) {
                if (value is Map<*, *>) {
                    mObjList.add(MetaObject(value as Map<String, Any>))
                }
            }
        }

        return mObjList
    }

    @Synchronized
    open fun add(metaObject: MetaObject) {
        Objects.requireNonNull(metaObject, "MetaObject to add must not be null")
        this.metaObjects.add(metaObject)
    }

    @Synchronized
    open fun remove(metaObject: MetaObject?) {
        if (metaObject == null) {
            return
        }
        this.metaObjects.remove(metaObject)
    }

    @Synchronized
    @Throws(IOException::class, JsonException::class)
    open fun writeToStream(outputStream: OutputStream) {
        val listToSave = this.metaObjects.map { it.dataMap }
        JsonUtil.writeToStream(listToSave, outputStream)
    }
}
