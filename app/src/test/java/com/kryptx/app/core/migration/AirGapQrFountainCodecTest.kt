package com.kryptx.app.core.migration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class AirGapQrFountainCodecTest {

    private val random = SecureRandom()

    @Test
    fun `test small payload single frame roundtrip`() {
        val payload = "Hello, Air-Gapped Sovereign World!".toByteArray(Charsets.UTF_8)
        val frames = AirGapQrFountainCodec.encodeFountain(payload, partSize = 100, sessionId = "sess1234")

        // 1 meta frame + 1 data frame
        assertEquals(2, frames.size)

        val reassembler = AirGapQrFountainCodec.FountainReassembler()
        val metaRes = reassembler.feedFrame(frames[0])
        assertTrue(metaRes is AirGapQrFountainCodec.FountainReassembler.Result.Progress)

        val partRes = reassembler.feedFrame(frames[1])
        assertTrue(partRes is AirGapQrFountainCodec.FountainReassembler.Result.Complete)

        val complete = partRes as AirGapQrFountainCodec.FountainReassembler.Result.Complete
        assertArrayEquals(payload, complete.payload)
        assertEquals("sess1234", complete.sessionId)
    }

    @Test
    fun `test large payload out of order reassembly`() {
        val payload = ByteArray(25000)
        random.nextBytes(payload)

        val frames = AirGapQrFountainCodec.encodeFountain(payload, partSize = 300, sessionId = "sessionQ")
        assertTrue(frames.size > 80)

        // Separate meta and part frames, shuffle the part frames completely
        val metaFrame = frames[0]
        val partFrames = frames.drop(1).shuffled(kotlin.random.Random(42))

        val reassembler = AirGapQrFountainCodec.FountainReassembler()
        // Feed meta first
        reassembler.feedFrame(metaFrame)

        var finalResult: AirGapQrFountainCodec.FountainReassembler.Result? = null
        for (frame in partFrames) {
            finalResult = reassembler.feedFrame(frame)
        }

        assertTrue(finalResult is AirGapQrFountainCodec.FountainReassembler.Result.Complete)
        val complete = finalResult as AirGapQrFountainCodec.FountainReassembler.Result.Complete
        assertArrayEquals(payload, complete.payload)
        assertEquals("sessionQ", complete.sessionId)
    }

    @Test
    fun `test duplicate frames and parts arrive before meta`() {
        val payload = ByteArray(1200)
        random.nextBytes(payload)

        val frames = AirGapQrFountainCodec.encodeFountain(payload, partSize = 250, sessionId = "sessPre")
        val metaFrame = frames[0]
        val partFrames = frames.drop(1)

        val reassembler = AirGapQrFountainCodec.FountainReassembler()
        // Feed parts BEFORE meta frame
        for (part in partFrames) {
            val res = reassembler.feedFrame(part)
            // Progress until all parts received
            assertTrue(res is AirGapQrFountainCodec.FountainReassembler.Result.Progress ||
                       res is AirGapQrFountainCodec.FountainReassembler.Result.Complete)
        }

        // Feed duplicate part
        val dupRes = reassembler.feedFrame(partFrames[0])
        assertTrue(dupRes is AirGapQrFountainCodec.FountainReassembler.Result.Complete)

        // Finally feed meta frame (confirms SHA-256)
        val metaRes = reassembler.feedFrame(metaFrame)
        assertTrue(metaRes is AirGapQrFountainCodec.FountainReassembler.Result.Complete)
        val complete = metaRes as AirGapQrFountainCodec.FountainReassembler.Result.Complete
        assertArrayEquals(payload, complete.payload)
    }

    @Test
    fun `test corrupted CRC32 frame is rejected`() {
        val payload = "Important secret credentials".toByteArray(Charsets.UTF_8)
        val frames = AirGapQrFountainCodec.encodeFountain(payload, partSize = 50, sessionId = "crcFail")

        val reassembler = AirGapQrFountainCodec.FountainReassembler()
        reassembler.feedFrame(frames[0]) // meta

        // Tamper with the CRC segment in frames[1]
        // format: kryptx:ur:part/{sessionId}/{partIndex}/{totalParts}/{crc32Hex}/{base64Data}
        val segments = frames[1].split('/').toMutableList()
        segments[4] = "00000000" // Corrupt CRC
        val tamperedFrame = segments.joinToString("/")

        val res = reassembler.feedFrame(tamperedFrame)
        assertTrue(res is AirGapQrFountainCodec.FountainReassembler.Result.Corrupted)
    }

    @Test
    fun `test payload size mismatch in metadata is rejected`() {
        val payload = "Important secret credentials for testing length check".toByteArray(Charsets.UTF_8)
        val frames = AirGapQrFountainCodec.encodeFountain(payload, partSize = 25, sessionId = "lenFail")

        val reassembler = AirGapQrFountainCodec.FountainReassembler()

        // Format: kryptx:ur:meta/{sessionId}/{totalParts}/{totalSizeBytes}/{sha256Hex}
        val metaSegments = frames[0].split('/').toMutableList()
        metaSegments[3] = (payload.size + 100).toString() // Claim 100 more bytes than actual assembled
        val tamperedMeta = metaSegments.joinToString("/")

        reassembler.feedFrame(tamperedMeta)
        var lastRes: AirGapQrFountainCodec.FountainReassembler.Result? = null
        for (f in frames.drop(1)) {
            lastRes = reassembler.feedFrame(f)
        }

        assertTrue("Size mismatch must be detected as Corrupted", lastRes is AirGapQrFountainCodec.FountainReassembler.Result.Corrupted)
        assertTrue((lastRes as AirGapQrFountainCodec.FountainReassembler.Result.Corrupted).reason.contains("Payload size mismatch"))
    }
}
