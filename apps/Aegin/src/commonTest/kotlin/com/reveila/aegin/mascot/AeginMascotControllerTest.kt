package com.reveila.aegin.mascot

import com.reveila.aegin.ui.mascot.AeginMascotController
import com.reveila.aegin.ui.mascot.AeginMascotPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AeginMascotControllerTest {

    @Test
    fun testFullMascotLifecycleTransitions() {
        val controller = AeginMascotController()

        // 1. Initial IDLE
        assertEquals(AeginMascotPhase.IDLE, controller.state.value.phase)
        assertFalse(controller.state.value.isBusy)

        // 2. LISTENING_USER on typing, returns to IDLE when cleared
        controller.onUserInputActive(true)
        assertEquals(AeginMascotPhase.LISTENING_USER, controller.state.value.phase)
        assertFalse(controller.state.value.isBusy)
        controller.onUserInputActive(false)
        assertEquals(AeginMascotPhase.IDLE, controller.state.value.phase)

        // 3. ROUTING_LOCAL (busy) — typing does not override busy state
        controller.onLocalSlmRouting()
        assertEquals(AeginMascotPhase.ROUTING_LOCAL, controller.state.value.phase)
        assertTrue(controller.state.value.isBusy)
        controller.onUserInputActive(true)
        assertEquals(AeginMascotPhase.ROUTING_LOCAL, controller.state.value.phase)

        // 4. COORDINATING_CLOUD
        controller.onCoordinatingWithModel("DeepSeek")
        assertEquals(AeginMascotPhase.COORDINATING_CLOUD, controller.state.value.phase)
        assertTrue(controller.state.value.statusLabel.contains("DeepSeek"))
        assertTrue(controller.state.value.isBusy)

        // 5. SPEAKING_RESPONSE
        controller.onStreamingResponse(tokensPerSecond = 24.5f)
        assertEquals(AeginMascotPhase.SPEAKING_RESPONSE, controller.state.value.phase)
        assertEquals(24.5f, controller.state.value.tokensPerSecond)
        assertTrue(controller.state.value.isBusy)

        // 6. ERROR_ALERT & recovery to IDLE
        controller.onError("Timeout")
        assertEquals(AeginMascotPhase.ERROR_ALERT, controller.state.value.phase)
        assertEquals("Timeout", controller.state.value.statusLabel)
        assertFalse(controller.state.value.isBusy)

        controller.onIdle()
        assertEquals(AeginMascotPhase.IDLE, controller.state.value.phase)
    }
}
