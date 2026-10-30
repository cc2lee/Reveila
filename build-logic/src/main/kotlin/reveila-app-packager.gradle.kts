// build-logic/src/main/kotlin/reveila-app-packager.gradle.kts

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Extension for configuring selective component, asset, and bytecode packaging
 * for individual target apps within Reveila-Suite.
 */
abstract class ReveilaAppExtension {
    /**
     * Root SystemComponent names required by this application from the Reveila warehouse.
     * Transitive component dependencies declared in standard.json are resolved automatically.
     */
    abstract val requiredComponents: ListProperty<String>

    /**
     * Optional Plugin names required by this application from configs/plugins/.
     */
    abstract val requiredPlugins: ListProperty<String>

    /**
     * Target Reveila runtime platforms to include in the sliced system-home (e.g., "java", "android").
     */
    abstract val platforms: ListProperty<String>

    /**
     * Whether to bundle the large native llama-server binary in mobile/desktop assets.
     * Defaults to false to keep release packages small for mobile and wearables.
     */
    abstract val includeLocalLlmBinary: Property<Boolean>

    /**
     * Additional root class names (FQCN) from the warehouse JARs to retain as entry points
     * during bytecode reachability slicing.
     */
    abstract val additionalRootClasses: ListProperty<String>

    /**
     * Extra warehouse JAR files to slice alongside reveila-core.jar.
     */
    abstract val warehouseJars: ConfigurableFileCollection
}

val appExt = extensions.create<ReveilaAppExtension>("reveilaApp").apply {
    requiredComponents.convention(
        listOf(
            "AgenticFabric",
            "OrchestrationService",
            "UiController",
            "KnowledgeVault"
        )
    )
    requiredPlugins.convention(emptyList())
    platforms.convention(listOf("java", "android"))
    includeLocalLlmBinary.convention(
        providers.gradleProperty("includeLocalLlmBinary").map { it.toBoolean() }.orElse(false)
    )
    additionalRootClasses.convention(
        listOf(
            "com.reveila.system.Reveila",
            "com.reveila.system.DefaultPlatformAdapter",
            "com.reveila.system.BasePlatformAdapter",
            "com.reveila.system.SystemProxy",
            "com.reveila.system.ReflectionMethod",
            "com.reveila.ai.LocalLlamaProvider",
            "com.reveila.ai.GeminiLlmProvider",
            "com.reveila.ai.OpenAiLlmProvider",
            "com.reveila.ai.DefaultEmbeddingModel",
            "com.reveila.persistence.DefaultVectorStore",
            "com.reveila.crypto.DefaultCryptographer",
            "com.reveila.data.JavaObjectRepository"
        )
    )
}

/**
 * Task 1: Resolves transitive component dependencies from the Reveila warehouse
 * and stages a minimal, stripped system-home directory tailored to the app.
 */
abstract class PrepareSlimSystemHomeTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceSystemHome: DirectoryProperty

    @get:Input
    abstract val requiredComponents: ListProperty<String>

    @get:Input
    abstract val requiredPlugins: ListProperty<String>

    @get:Input
    abstract val platforms: ListProperty<String>

    @get:Input
    abstract val includeLocalLlmBinary: Property<Boolean>

    @get:OutputDirectory
    abstract val outputSystemHome: DirectoryProperty

    @get:OutputFile
    abstract val resolvedClassesFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val srcHome = sourceSystemHome.get().asFile
        val outHome = outputSystemHome.get().asFile

        if (outHome.exists()) {
            outHome.deleteRecursively()
        }
        outHome.mkdirs()

        // 1. Parse shared/standard.json
        val standardJsonFile = File(srcHome, "configs/shared/standard.json")
        val listType = object : TypeReference<List<Map<String, Any?>>>() {}
        val allSharedEntries: List<Map<String, Any?>> = if (standardJsonFile.exists()) {
            mapper.readValue(standardJsonFile, listType)
        } else {
            emptyList()
        }

        // Map component name -> full entry map & dependencies
        val componentByName = LinkedHashMap<String, Map<String, Any?>>()
        for (entry in allSharedEntries) {
            val comp = entry["component"] as? Map<*, *> ?: continue
            val name = comp["name"]?.toString() ?: continue
            componentByName[name] = entry
        }

        // 2. Compute transitive closure of required shared components
        val neededNames = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        for (req in requiredComponents.get()) {
            if (neededNames.add(req)) {
                queue.add(req)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val entry = componentByName[current] ?: continue
            val comp = entry["component"] as? Map<*, *> ?: continue
            val deps = comp["dependencies"] as? List<*> ?: emptyList<Any?>()
            for (depObj in deps) {
                val depName = depObj?.toString() ?: continue
                if (neededNames.add(depName)) {
                    queue.add(depName)
                }
            }
        }

        // Preserve original declaration order for sliced standard.json
        val slicedSharedEntries = allSharedEntries.filter { entry ->
            val comp = entry["component"] as? Map<*, *>
            val name = comp?.get("name")?.toString()
            name != null && neededNames.contains(name)
        }

        val outConfigsDir = File(outHome, "configs")
        val outSharedDir = File(outConfigsDir, "shared")
        outSharedDir.mkdirs()
        mapper.writeValue(File(outSharedDir, "standard.json"), slicedSharedEntries)

        val resolvedClasses = LinkedHashSet<String>()
        for (entry in slicedSharedEntries) {
            val comp = entry["component"] as? Map<*, *> ?: continue
            val cls = comp["class"]?.toString()
            if (!cls.isNullOrBlank()) {
                resolvedClasses.add(cls)
            }
        }

        // 3. Copy only requested platform component configs (and filter to needed or platform-override components)
        for (plat in platforms.get()) {
            val platFile = File(srcHome, "configs/$plat/$plat.json")
            if (platFile.exists()) {
                val platEntries: List<Map<String, Any?>> = mapper.readValue(platFile, listType)
                val slicedPlatEntries = platEntries.filter { entry ->
                    val comp = entry["component"] as? Map<*, *>
                    val name = comp?.get("name")?.toString()
                    name != null && neededNames.contains(name)
                }
                val outPlatDir = File(outConfigsDir, plat)
                outPlatDir.mkdirs()
                mapper.writeValue(File(outPlatDir, "$plat.json"), slicedPlatEntries)

                for (entry in slicedPlatEntries) {
                    val comp = entry["component"] as? Map<*, *> ?: continue
                    val cls = comp["class"]?.toString()
                    if (!cls.isNullOrBlank()) {
                        resolvedClasses.add(cls)
                    }
                }
            }
        }

        // 4. Copy only explicitly required plugins (omits demo/unused plugins by default)
        val outPluginsDir = File(outConfigsDir, "plugins")
        val reqPlugins = requiredPlugins.get().toSet()
        if (reqPlugins.isNotEmpty()) {
            outPluginsDir.mkdirs()
            val srcPluginsDir = File(srcHome, "configs/plugins")
            srcPluginsDir.listFiles { f -> f.extension == "json" }?.forEach { pluginFile ->
                val pluginName = pluginFile.nameWithoutExtension
                if (reqPlugins.contains(pluginName)) {
                    pluginFile.copyTo(File(outPluginsDir, pluginFile.name), overwrite = true)
                    val pEntries: List<Map<String, Any?>> = mapper.readValue(pluginFile, listType)
                    for (entry in pEntries) {
                        val pMap = (entry["plugin"] ?: entry["component"]) as? Map<*, *> ?: continue
                        val cls = pMap["class"]?.toString()
                        if (!cls.isNullOrBlank()) {
                            resolvedClasses.add(cls)
                        }
                    }
                }
            }
        }

        // 5. Copy essential lightweight configuration files (skipping bulky images, web bundles, postgres, unused)
        val essentialConfigFiles = listOf(
            "configs/reveila.properties",
            "configs/governance-policy.json",
            "configs/global-perimeter.json",
            "configs/logo.txt",
            "configs/ui-schema.json",
            "configs/settings/llm.json",
            "configs/settings/security.properties",
            "configs/settings/identity.properties",
            "configs/templates/intent_validation_prompt.md"
        )
        for (relPath in essentialConfigFiles) {
            val srcFile = File(srcHome, relPath)
            if (srcFile.exists()) {
                val dstFile = File(outHome, relPath)
                dstFile.parentFile.mkdirs()
                srcFile.copyTo(dstFile, overwrite = true)
            }
        }

        // Ensure empty runtime directories exist as lightweight placeholders
        listOf("logs", "data", "temp", "plugins", "libs").forEach { dirName ->
            File(outHome, dirName).mkdirs()
        }

        // Seed a lightweight sovereign architecture markdown doc in default vault so initial scan indexes vectors cleanly
        val seedVaultDir = File(outHome, "ReveilaVault")
        seedVaultDir.mkdirs()
        File(seedVaultDir, "Reveila_Sovereign_Architecture.md").writeText(
            """
            # Reveila Sovereign AI & Aegin Architecture
            
            Aegin is the multi-platform Kotlin & Compose UI client for the Reveila Sovereign AI Suite.
            
            ## Key Capabilities
            - **Sovereign Privacy**: Local document vault indexing and on-device semantic vector embeddings.
            - **Hybrid Inference**: Seamlessly switch between On-Device GGUF models, Google Gemini, and OpenAI.
            - **Guardrail Enforcement**: Dual-model safety auditing, prompt risk scoring, and DLP validation.
            - **Selective Packaging**: Built with Reveila's bytecode class slicer to minimize release binary footprint on mobile, desktop, and wearables.
            """.trimIndent()
        )

        // 6. Optional local LLM binary inclusion
        if (includeLocalLlmBinary.get()) {
            val llamaServer = File(srcHome, "bin/android/llama-server")
            if (llamaServer.exists()) {
                val dstBin = File(outHome, "bin/android/llama-server")
                dstBin.parentFile.mkdirs()
                llamaServer.copyTo(dstBin, overwrite = true)
            }
        }

        // 7. Write resolved component classes manifest
        val outClassesFile = resolvedClassesFile.get().asFile
        outClassesFile.parentFile.mkdirs()
        outClassesFile.writeText(resolvedClasses.joinToString("\n"))

        logger.lifecycle(
            "[ReveilaPackager] Prepared slim system-home: kept ${slicedSharedEntries.size}/${allSharedEntries.size} shared components " +
                "(${neededNames.joinToString(", ")})."
        )
    }
}

/**
 * Task 2: Performs bytecode constant-pool reachability analysis across warehouse JARs
 * and packages ONLY the reachable classes into a slim, app-specific JAR.
 */
abstract class SliceWarehouseJarsTask : DefaultTask() {

    @get:InputFiles
    abstract val inputJars: ConfigurableFileCollection

    @get:InputFile
    abstract val resolvedComponentClassesFile: RegularFileProperty

    @get:Input
    abstract val additionalRootClasses: ListProperty<String>

    @get:OutputFile
    abstract val outputSlimJar: RegularFileProperty

    @get:OutputFile
    abstract val slicingReportFile: RegularFileProperty

    @TaskAction
    fun execute() {
        // Load all .class entries and resource entries from the warehouse JARs
        val classBytesMap = LinkedHashMap<String, ByteArray>() // internalName (e.g. com/reveila/system/Reveila) -> bytes
        val metaEntries = LinkedHashMap<String, ByteArray>()

        for (jarFile in inputJars.files) {
            if (!jarFile.exists()) continue
            JarFile(jarFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                        val internalName = name.removeSuffix(".class")
                        classBytesMap[internalName] = bytes
                    } else if (name.startsWith("META-INF/") && (name.endsWith(".kotlin_module") || name == "META-INF/MANIFEST.MF")) {
                        metaEntries[name] = bytes
                    }
                }
            }
        }

        // Group classes by outer class prefix so inner classes/lambdas/companions (Foo$Bar) are retained when Foo is reachable
        val innerClassGroups = HashMap<String, MutableList<String>>()
        for (internalName in classBytesMap.keys) {
            val outerName = internalName.substringBefore('$')
            innerClassGroups.getOrPut(outerName) { ArrayList() }.add(internalName)
        }

        // Seed reachability roots from resolved component classes + additionalRootClasses
        val roots = LinkedHashSet<String>()
        if (resolvedComponentClassesFile.get().asFile.exists()) {
            resolvedComponentClassesFile.get().asFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { roots.add(it.replace('.', '/')) }
        }
        additionalRootClasses.get().forEach { roots.add(it.trim().replace('.', '/')) }

        val reachable = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()

        fun markReachable(internalName: String) {
            val clean = internalName.trimStart('[').removePrefix("L").removeSuffix(";")
            if (clean !in classBytesMap) return
            if (reachable.add(clean)) {
                queue.add(clean)
            }
            val outer = clean.substringBefore('$')
            innerClassGroups[outer]?.forEach { sibling ->
                if (reachable.add(sibling)) {
                    queue.add(sibling)
                }
            }
        }

        for (root in roots) {
            markReachable(root)
        }

        // BFS over bytecode constant pool references
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val bytes = classBytesMap[current] ?: continue
            val refs = extractReferencedInternalClassNames(bytes)
            for (ref in refs) {
                markReachable(ref)
            }
        }

        // Write slim JAR containing only reachable classes
        val outJar = outputSlimJar.get().asFile
        outJar.parentFile.mkdirs()
        var originalClassBytes = 0L
        for (b in classBytesMap.values) originalClassBytes += b.size
        var slimClassBytes = 0L

        JarOutputStream(FileOutputStream(outJar)).use { jos ->
            for ((name, bytes) in metaEntries) {
                val entry = JarEntry(name)
                entry.time = 0L
                jos.putNextEntry(entry)
                jos.write(bytes)
                jos.closeEntry()
            }
            for (internalName in classBytesMap.keys.sorted()) {
                if (internalName in reachable) {
                    val bytes = classBytesMap[internalName]!!
                    slimClassBytes += bytes.size
                    val entry = JarEntry("$internalName.class")
                    entry.time = 0L
                    jos.putNextEntry(entry)
                    jos.write(bytes)
                    jos.closeEntry()
                }
            }
        }

        val prunedClasses = classBytesMap.keys.filter { it !in reachable }.sorted()
        val savedPercent = if (originalClassBytes > 0) {
            ((originalClassBytes - slimClassBytes) * 100.0 / originalClassBytes)
        } else 0.0

        val reportFile = slicingReportFile.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("=== REVEILA WAREHOUSE BYTECODE SLICING REPORT ===")
                appendLine("Total Warehouse Classes : ${classBytesMap.size}")
                appendLine("Reachable App Classes   : ${reachable.size}")
                appendLine("Pruned Unused Classes   : ${prunedClasses.size}")
                appendLine("Original Class Payload  : $originalClassBytes bytes")
                appendLine("Sliced Class Payload    : $slimClassBytes bytes")
                appendLine("Bytecode Reduction      : ${String.format("%.1f", savedPercent)}%")
                appendLine()
                appendLine("--- Seed Root Classes ---")
                roots.forEach { appendLine("  + ${it.replace('/', '.')}") }
                appendLine()
                appendLine("--- Pruned Warehouse Classes (${prunedClasses.size}) ---")
                prunedClasses.forEach { appendLine("  - ${it.replace('/', '.')}") }
            }
        )

        logger.lifecycle(
            "[ReveilaPackager] Sliced warehouse JAR: kept ${reachable.size}/${classBytesMap.size} classes " +
                "(pruned ${prunedClasses.size} unused classes, ${String.format("%.1f", savedPercent)}% bytecode reduction) -> ${outJar.name}"
        )
    }

    /**
     * Parses the JVM .class Constant Pool (tags 1..20) and extracts all referenced internal class names
     * from CONSTANT_Class entries as well as field/method type descriptors.
     */
    private fun extractReferencedInternalClassNames(classBytes: ByteArray): Set<String> {
        val result = HashSet<String>()
        try {
            val dis = DataInputStream(ByteArrayInputStream(classBytes))
            val magic = dis.readInt()
            if (magic != 0xCAFEBABE.toInt()) return result
            dis.readUnsignedShort() // minor_version
            dis.readUnsignedShort() // major_version

            val cpCount = dis.readUnsignedShort()
            val utf8Pool = arrayOfNulls<String>(cpCount)
            val classNameIndices = ArrayList<Int>()

            var i = 1
            while (i < cpCount) {
                when (dis.readUnsignedByte()) {
                    1 -> { // CONSTANT_Utf8
                        val str = dis.readUTF()
                        utf8Pool[i] = str
                        extractDescriptorClassNames(str, result)
                    }
                    3, 4 -> { // Integer, Float
                        dis.skipBytes(4)
                    }
                    5, 6 -> { // Long, Double (occupies 2 constant pool slots)
                        dis.skipBytes(8)
                        i++
                    }
                    7 -> { // CONSTANT_Class
                        classNameIndices.add(dis.readUnsignedShort())
                    }
                    8, 16, 19, 20 -> { // String, MethodType, Module, Package
                        dis.skipBytes(2)
                    }
                    9, 10, 11, 12, 17, 18 -> { // Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                        dis.skipBytes(4)
                    }
                    15 -> { // MethodHandle
                        dis.skipBytes(3)
                    }
                    else -> {
                        return result
                    }
                }
                i++
            }

            for (idx in classNameIndices) {
                val name = utf8Pool.getOrNull(idx) ?: continue
                if (name.startsWith("[")) {
                    extractDescriptorClassNames(name, result)
                } else {
                    result.add(name)
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun extractDescriptorClassNames(utf8: String, out: MutableSet<String>) {
        // Extract Lpkg/ClassName; tokens from JVM descriptors and signatures
        var idx = utf8.indexOf('L')
        while (idx >= 0 && idx < utf8.length) {
            val semi = utf8.indexOf(';', idx + 1)
            if (semi > idx + 1) {
                val candidate = utf8.substring(idx + 1, semi)
                if (candidate.contains('/') && candidate.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '$' }) {
                    out.add(candidate.substringBefore('<'))
                }
                idx = utf8.indexOf('L', semi + 1)
            } else {
                break
            }
        }
    }
}

/**
 * Task 3: Generates precise R8 / ProGuard keep rules ONLY for the active components
 * in the app's sliced configuration manifest.
 */
abstract class GenerateReveilaKeepRulesTask : DefaultTask() {

    @get:InputFile
    abstract val resolvedComponentClassesFile: RegularFileProperty

    @get:Input
    abstract val additionalRootClasses: ListProperty<String>

    @get:OutputFile
    abstract val outputKeepRulesFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val componentClasses = if (resolvedComponentClassesFile.get().asFile.exists()) {
            resolvedComponentClassesFile.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        val allReflectiveClasses = LinkedHashSet<String>()
        allReflectiveClasses.addAll(componentClasses)
        allReflectiveClasses.addAll(additionalRootClasses.get())

        val proFile = outputKeepRulesFile.get().asFile
        proFile.parentFile.mkdirs()
        proFile.writeText(
            buildString {
                appendLine("# Auto-generated by reveila-app-packager for selective R8 / ProGuard tree-shaking")
                appendLine("# Only retains reflective entry points for components declared in this app's manifest.")
                appendLine()
                appendLine("-dontwarn java.lang.instrument.**")
                appendLine("-dontwarn javax.management.**")
                appendLine("-dontwarn sun.misc.**")
                appendLine()
                appendLine("# Keep base component lifecycle contracts")
                appendLine("-keep class com.reveila.system.SystemComponent { *; }")
                appendLine("-keep class com.reveila.system.PluginComponent { *; }")
                appendLine("-keep class com.reveila.system.Reveila { *; }")
                appendLine()
                appendLine("# Keep active app-specific SystemComponents & reflective methods")
                for (cls in allReflectiveClasses) {
                    appendLine("-keep class $cls {")
                    appendLine("    public <init>(...);")
                    appendLine("    public <methods>;")
                    appendLine("}")
                }
            }
        )
    }
}

val prepareSlimSystemHome = tasks.register<PrepareSlimSystemHomeTask>("prepareSlimSystemHome") {
    group = "reveila-packaging"
    description = "Stages a minimal, app-specific system-home with transitive component resolution."
    sourceSystemHome.set(rootProject.file("system-home/standard"))
    requiredComponents.set(appExt.requiredComponents)
    requiredPlugins.set(appExt.requiredPlugins)
    platforms.set(appExt.platforms)
    includeLocalLlmBinary.set(appExt.includeLocalLlmBinary)
    outputSystemHome.set(layout.buildDirectory.dir("reveila-slim/system-home"))
    resolvedClassesFile.set(layout.buildDirectory.file("reveila-slim/resolved-component-classes.txt"))
}

val sliceWarehouseJars = tasks.register<SliceWarehouseJarsTask>("sliceWarehouseJars") {
    group = "reveila-packaging"
    description = "Slices Reveila warehouse JARs using bytecode reachability analysis to pack only necessary classes."
    dependsOn(prepareSlimSystemHome)
    if (rootProject.findProject(":reveila:core") != null) {
        dependsOn(":reveila:core:exportJvmJar")
    }
    inputJars.from(rootProject.file("distribution/jvm/reveila-core.jar"))
    inputJars.from(appExt.warehouseJars)
    resolvedComponentClassesFile.set(prepareSlimSystemHome.flatMap { it.resolvedClassesFile })
    additionalRootClasses.set(appExt.additionalRootClasses)
    outputSlimJar.set(layout.buildDirectory.file("reveila-slim/libs/reveila-core-slim.jar"))
    slicingReportFile.set(layout.buildDirectory.file("reveila-slim/slicing-report.txt"))
}

val generateReveilaKeepRules = tasks.register<GenerateReveilaKeepRulesTask>("generateReveilaKeepRules") {
    group = "reveila-packaging"
    description = "Generates minimal R8 / ProGuard keep rules for the app's active Reveila components."
    dependsOn(prepareSlimSystemHome)
    resolvedComponentClassesFile.set(prepareSlimSystemHome.flatMap { it.resolvedClassesFile })
    additionalRootClasses.set(appExt.additionalRootClasses)
    outputKeepRulesFile.set(layout.buildDirectory.file("reveila-slim/reveila-keep.pro"))
}
