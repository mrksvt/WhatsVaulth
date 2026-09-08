package com.mrksvt.waen.xposed.features.voice_tts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the synthetic-bubble store and its flattened index.
 * This is the most bug-prone part of the feature: the flattened list is what
 * gets bound to WhatsApp's adapter, so ordering and delete-shift semantics
 * must be exact.
 */
class SyntheticBubbleStoreTest {

    private lateinit var store: SyntheticBubbleStore

    @Before
    fun setUp() {
        store = SyntheticBubbleStore()
    }

    // ---- insertion order ----

    @Test
    fun `bubbles append at end never in the middle`() {
        val a = store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "A")
        val b = store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "B")
        val c = store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "C")

        val ids = store.bubblesFor("m1").map { it.bubbleId }
        assertEquals(listOf(a.bubbleId, b.bubbleId, c.bubbleId), ids)
    }

    @Test
    fun `insertion order is not reordered by type`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "tts1")
        store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "tr1")
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "tts2")

        val types = store.bubblesFor("m1").map { it.type }
        assertEquals(listOf(BubbleType.TTS, BubbleType.TRANSLATION, BubbleType.TTS), types)
    }

    @Test
    fun `bubble ids are unique across messages`() {
        val ids = (1..50).map {
            store.addBubble("m$it", BubbleType.TTS, BubbleState.LOADING).bubbleId
        }
        assertEquals(50, ids.toSet().size)
    }

    // ---- flattened index ----

    @Test
    fun `flattened index keeps original first then bubbles in order`() {
        store.addBubble("m2", BubbleType.TRANSLATION, BubbleState.READY, "tr")
        store.addBubble("m2", BubbleType.TTS, BubbleState.READY, "tts")

        val flat = store.buildFlattenedIndex(listOf("m1", "m2", "m3"))

        assertEquals(5, flat.size)
        assertTrue(flat[0] is SyntheticBubbleStore.FlattenedEntry.Original)
        assertEquals("m1", (flat[0] as SyntheticBubbleStore.FlattenedEntry.Original).messageId)
        assertEquals("m2", (flat[1] as SyntheticBubbleStore.FlattenedEntry.Original).messageId)

        val synth2 = flat[2] as SyntheticBubbleStore.FlattenedEntry.Synthetic
        assertEquals("tr", synth2.bubble.content)
        val synth3 = flat[3] as SyntheticBubbleStore.FlattenedEntry.Synthetic
        assertEquals("tts", synth3.bubble.content)

        assertEquals("m3", (flat[4] as SyntheticBubbleStore.FlattenedEntry.Original).messageId)
    }

    @Test
    fun `delete middle bubble shifts following bubbles up`() {
        val a = store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "A")
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "B")
        store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "C")

        assertTrue(store.removeBubble(a.bubbleId))

        val flat = store.buildFlattenedIndex(listOf("m1"))
        assertEquals(3, flat.size)
        assertEquals("B", (flat[1] as SyntheticBubbleStore.FlattenedEntry.Synthetic).bubble.content)
        assertEquals("C", (flat[2] as SyntheticBubbleStore.FlattenedEntry.Synthetic).bubble.content)
    }

    @Test
    fun `delete last bubble of a message prunes the message entry`() {
        val a = store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        store.removeBubble(a.bubbleId)
        assertTrue(store.bubblesFor("m1").isEmpty())
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `orphan bubbles are pruned when original message disappears`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        store.addBubble("gone", BubbleType.TRANSLATION, BubbleState.READY, "B")

        val flat = store.buildFlattenedIndex(listOf("m1"))
        assertEquals(2, flat.size)

        // "gone" pruned from the store itself, not just the output
        assertTrue(store.bubblesFor("gone").isEmpty())
    }

    @Test
    fun `remove all bubbles for a message`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "B")
        store.addBubble("m2", BubbleType.TTS, BubbleState.READY, "C")

        store.removeBubblesForMessage("m1")

        assertTrue(store.bubblesFor("m1").isEmpty())
        assertEquals(1, store.bubblesFor("m2").size)
    }

    // ---- state updates ----

    @Test
    fun `update bubble state and content`() {
        val b = store.addBubble("m1", BubbleType.TTS, BubbleState.LOADING)
        assertTrue(store.updateBubble(b.bubbleId, BubbleState.READY, "/tmp/x.wav"))
        assertEquals(BubbleState.READY, store.bubblesFor("m1").first().state)
        assertEquals("/tmp/x.wav", store.bubblesFor("m1").first().content)
    }

    @Test
    fun `update missing bubble returns false`() {
        assertFalse(store.updateBubble("nope", BubbleState.ERROR, "x"))
        assertFalse(store.removeBubble("nope"))
    }

    @Test
    fun `update does not change bubble position`() {
        val a = store.addBubble("m1", BubbleType.TTS, BubbleState.LOADING)
        store.addBubble("m1", BubbleType.TRANSLATION, BubbleState.READY, "B")
        store.updateBubble(a.bubbleId, BubbleState.READY, "audio")

        val contents = store.bubblesFor("m1").map { it.content }
        assertEquals(listOf("audio", "B"), contents)
    }

    // ---- hasBubbleFor ----

    @Test
    fun `hasBubbleFor is type-scoped`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        assertTrue(store.hasBubbleFor("m1", BubbleType.TTS))
        assertFalse(store.hasBubbleFor("m1", BubbleType.TRANSLATION))
        assertFalse(store.hasBubbleFor("m2", BubbleType.TTS))
    }

    // ---- no cross-bubble references ----

    @Test
    fun `every bubble references only its original message id`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        store.addBubble("m2", BubbleType.TRANSLATION, BubbleState.READY, "B")
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "C")

        for (b in store.snapshot()) {
            assertNotNull(b.originalMessageId)
            // bubbleId encodes type + original id + seq; no parent field exists
            // on the model at all (data class shape check)
            val fields = SyntheticBubble::class.java.declaredFields.map { it.name }
            assertFalse("bubble must not carry a parent/prev/next reference",
                fields.any { it.lowercase() in listOf("parentid", "previousid", "nextid", "prev", "next") })
        }
    }

    // ---- clear ----

    @Test
    fun `clear empties everything`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        store.addBubble("m2", BubbleType.TTS, BubbleState.READY, "B")
        store.clear()
        assertTrue(store.snapshot().isEmpty())
        assertTrue(store.buildFlattenedIndex(listOf("m1", "m2")).all {
            it is SyntheticBubbleStore.FlattenedEntry.Original
        })
    }

    // ---- interleaved multi-message ordering ----

    @Test
    fun `flattened index follows original list order not insertion order`() {
        // bubbles inserted newest-first, but original list is oldest-first
        store.addBubble("m3", BubbleType.TTS, BubbleState.READY, "X")
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "Y")
        store.addBubble("m2", BubbleType.TTS, BubbleState.READY, "Z")

        val flat = store.buildFlattenedIndex(listOf("m1", "m2", "m3"))
        val originals = flat.filterIsInstance<SyntheticBubbleStore.FlattenedEntry.Original>()
            .map { it.messageId }
        assertEquals(listOf("m1", "m2", "m3"), originals)

        val synth = flat.filterIsInstance<SyntheticBubbleStore.FlattenedEntry.Synthetic>()
        assertEquals(listOf("Y", "Z", "X"), synth.map { it.bubble.content })
        assertEquals(listOf("m1", "m2", "m3"), synth.map { it.originalMessageId })
    }

    @Test
    fun `empty original list yields empty flattened index and prunes all`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        val flat = store.buildFlattenedIndex(emptyList())
        assertTrue(flat.isEmpty())
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `duplicate message ids in original list are caller error not crash`() {
        store.addBubble("m1", BubbleType.TTS, BubbleState.READY, "A")
        val flat = store.buildFlattenedIndex(listOf("m1", "m1"))
        // each occurrence gets its own copy of the bubble in output; store keeps one
        assertEquals(4, flat.size)
        assertEquals(1, store.bubblesFor("m1").size)
    }
}
