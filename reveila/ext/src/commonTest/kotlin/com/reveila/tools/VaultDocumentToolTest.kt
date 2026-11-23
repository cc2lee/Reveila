package com.reveila.tools

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultDocumentToolTest {

    private fun createTempRoot(prefix: String): String {
        val fs = PlatformFileSystem()
        val root = fs.resolve(fs.getDefaultSystemHome(), "temp-test-$prefix-${Uuid.randomUuid().take(8)}")
        fs.createDirectories(root)
        return root
    }

    @Test
    fun pathGuardBlocksTraversalAndSiblingPrefixEscape() {
        val allowed = listOf("/workspace/vault")
        assertTrue(VaultPathGuard.isPathWithinAllowedRoots("/workspace/vault", allowed))
        assertTrue(VaultPathGuard.isPathWithinAllowedRoots("/workspace/vault/AI-Generated/todo.md", allowed))
        assertFalse(VaultPathGuard.isPathWithinAllowedRoots("/workspace/vault-evil/secret.txt", allowed))
        assertFalse(VaultPathGuard.isPathWithinAllowedRoots("/workspace/vault/../etc/passwd", allowed))
        assertFalse(VaultPathGuard.isPathWithinAllowedRoots("/etc/passwd", allowed))
    }

    @Test
    fun saveBatchWritesThreeFormatsIntoDefaultAiGeneratedFolder() {
        val fs = PlatformFileSystem()
        val vaultRoot = createTempRoot("vault")
        try {
            val tool = VaultDocumentTool(vaultRootProvider = { vaultRoot }, fs = fs)
            val resultJson = tool.execute(
                mapOf(
                    "action" to "save_batch",
                    "files" to listOf(
                        mapOf("filename" to "todo.txt", "content" to "1. Ship Aegin"),
                        mapOf("filename" to "todo.md", "content" to "- [ ] Ship Aegin"),
                        mapOf("filename" to "todo.json", "content" to """{"tasks":["Ship Aegin"]}""")
                    )
                )
            )
            val parsed = JsonUtil.parseJsonStringToMap(resultJson)
            assertEquals(true, parsed["ok"])
            assertEquals(3, (parsed["count"] as Number).toInt())

            val outDir = VaultPathGuard.resolveDefaultOutputDir(vaultRoot, fs = fs)
            assertTrue(fs.exists(fs.resolve(outDir, "todo.txt")))
            assertTrue(fs.exists(fs.resolve(outDir, "todo.md")))
            assertTrue(fs.exists(fs.resolve(outDir, "todo.json")))
            assertEquals("- [ ] Ship Aegin", fs.readText(fs.resolve(outDir, "todo.md")))
        } finally {
            fs.delete(vaultRoot, recursive = true)
        }
    }

    @Test
    fun saveBlocksOutOfRootTargetDirAndTraversalFilenames() {
        val fs = PlatformFileSystem()
        val vaultRoot = createTempRoot("vault-guard")
        val outsideRoot = createTempRoot("outside-dir")
        try {
            val tool = VaultDocumentTool(vaultRootProvider = { vaultRoot }, fs = fs)

            val escapeDirResp = JsonUtil.parseJsonStringToMap(
                tool.execute(
                    mapOf(
                        "action" to "save",
                        "filename" to "leak.txt",
                        "content" to "nope",
                        "targetDir" to outsideRoot
                    )
                )
            )
            assertEquals(false, escapeDirResp["ok"])
            assertEquals(true, escapeDirResp["blocked"])

            val traversalResp = JsonUtil.parseJsonStringToMap(
                tool.execute(
                    mapOf(
                        "action" to "save",
                        "filename" to "../escape.txt",
                        "content" to "nope"
                    )
                )
            )
            assertEquals(false, traversalResp["ok"])
            assertEquals(true, traversalResp["blocked"])
        } finally {
            fs.delete(vaultRoot, recursive = true)
            fs.delete(outsideRoot, recursive = true)
        }
    }
}
