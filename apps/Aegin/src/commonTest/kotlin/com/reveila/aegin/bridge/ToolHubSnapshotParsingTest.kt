package com.reveila.aegin.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolHubSnapshotParsingTest {

    @Test
    fun testParseToolHubSnapshotWithWaitingAuthAndLiveStates() {
        val raw = """
            {
              "browser": {
                "isVisible": true,
                "url": "https://example.org/login",
                "title": "Example Login",
                "statusCode": 200,
                "lastAction": "Navigated to https://example.org/login",
                "contentPreview": "Sign in",
                "controlOwner": "AGENT",
                "waitingForUser": false
              },
              "terminal": {
                "isVisible": true,
                "shellName": "powershell.exe",
                "currentScript": "Write-Output 'ok'",
                "outputText": "ok",
                "exitCode": 0,
                "status": "COMPLETED",
                "controlOwner": "USER"
              },
              "downloads": {
                "isVisible": true,
                "controlOwner": "AGENT",
                "availableStorageBytes": 21474836480,
                "items": [
                  {
                    "downloadId": "gguf-1",
                    "fileName": "qwen2.5-3b-instruct-q4_k_m.gguf",
                    "status": "DOWNLOADING",
                    "percentage": 42,
                    "downloadedBytes": 1000000000,
                    "totalBytes": 2400000000,
                    "message": "Downloading (42%)"
                  }
                ]
              },
              "pendingAuthorization": {
                "requestId": "auth-1234",
                "toolName": "terminal",
                "action": "execute_script",
                "scope": "execute_script",
                "title": "Run Script on Device",
                "reason": "Authorization required",
                "commandPreview": "echo hello",
                "requiresCredentials": false,
                "credentialType": "NONE"
              },
              "waitingForUserInput": true
            }
        """.trimIndent()

        val snap = AeginBridge.parseToolHubSnapshot(raw)
        assertTrue(snap.waitingForUserInput)
        assertEquals("https://example.org/login", snap.browser.url)
        assertEquals("USER", snap.terminal.controlOwner)
        assertEquals(1, snap.downloads.items.size)
        assertEquals(42, snap.downloads.items.first().percentage)
        assertNotNull(snap.pendingAuthorization)
        assertEquals("auth-1234", snap.pendingAuthorization?.requestId)
    }
}
