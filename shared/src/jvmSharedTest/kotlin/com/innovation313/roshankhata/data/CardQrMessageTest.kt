package com.innovation313.roshankhata.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CardQrMessageTest {
    private val neutral = "Hello, I saw your card"

    @Test fun `nothing saved shows the default`() = assertEquals(neutral, CardQrMessage.shown(null, neutral))

    @Test fun `an old default saved by the app shows the new one`() {
        assertEquals(neutral, CardQrMessage.shown("Assalam o Alaikum, I saw your card", neutral))
        assertEquals(neutral, CardQrMessage.shown("  السلام علیکم ", neutral))
    }

    @Test fun `what the owner typed stays and an emptied box stays empty`() {
        assertEquals("Welcome to my shop", CardQrMessage.shown("Welcome to my shop", neutral))
        assertEquals("", CardQrMessage.shown("", neutral))
    }
}
