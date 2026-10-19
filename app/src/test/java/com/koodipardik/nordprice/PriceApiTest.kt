package com.koodipardik.nordprice

import com.koodipardik.nordprice.core.PricePoint
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.data.PriceApi
import com.koodipardik.nordprice.data.PriceApiException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Network layer tests, run against the payload Elering really returned on 2026-10-17 (captured
 * into `src/test/resources/elering_2026-10-17.json`) and served from a loopback stub.
 *
 * This is how the fetch path gets verified here rather than on a device: on this machine's work
 * network every TLS connection is re-signed by a corporate forward proxy, so an emulator holding
 * only the AOSP trust anchors cannot reach the real API — a failure that says nothing about the
 * code. Decoding, the request window, gzip and the error path are all exercised below.
 */
class PriceApiTest {

    private val payload: String by lazy {
        checkNotNull(javaClass.getResourceAsStream("/elering_2026-10-17.json"))
            { "missing captured payload" }.bufferedReader().readText()
    }

    private lateinit var server: StubEleringServer

    @Before
    fun start() {
        server = StubEleringServer(payload.toByteArray())
    }

    @After
    fun stop() = server.close()

    private fun api() = PriceApi(endpoint = server.baseUrl)

    @Test
    fun `fetches and decodes the real payload`() = runBlocking {
        val points = api().decode(
            api().fetchRange(
                Instant.parse("2026-10-17T00:00:00Z"),
                Instant.parse("2026-10-17T21:59:59.999Z"),
            ),
            Region.ESTONIA,
        )

        assertEquals(88, points.size)
        assertEquals(1792195200L, points.first().timestamp)
        assertEquals(106.0, points.first().price, 1e-9)
        // The window has to leave in Elering's ISO-8601 UTC shape, milliseconds included.
        assertTrue(
            "request line was ${server.lastRequestLine}",
            server.lastRequestLine!!.contains("start=2026-10-17T00:00:00.000Z"),
        )
        assertTrue(server.lastRequestLine!!.contains("end=2026-10-17T21:59:59.999Z"))
    }

    @Test
    fun `reads a gzipped response`() {
        StubEleringServer(payload.toByteArray(), gzip = true).use { gzipped ->
            val points = runBlocking {
                PriceApi(endpoint = gzipped.baseUrl).let { it.decodeAll(it.fetchRange(Instant.now(), Instant.now())) }
            }
            assertEquals(88, points.getValue(Region.FINLAND).size)
        }
    }

    @Test
    fun `all four markets come out of one payload`() {
        val all = api().decodeAll(payload)
        assertEquals(
            setOf(Region.ESTONIA, Region.LATVIA, Region.LITHUANIA, Region.FINLAND),
            all.keys,
        )
        assertTrue(all.values.all { it.size == 88 })
        assertEquals(22.7, all.getValue(Region.FINLAND).first().price, 1e-9)
    }

    @Test
    fun `a market absent from the payload yields no points instead of throwing`() {
        // Elering answers with null for a market with no data; the iOS decoder assumes the four
        // lists are always present and would throw.
        val sparse = """{"success":true,"data":{"ee":null,"fi":[{"timestamp":1,"price":2.0}],"lt":[],"lv":[]}}"""
        val all = api().decodeAll(sparse)
        assertEquals(0, all.getValue(Region.ESTONIA).size)
        assertEquals(listOf(PricePoint(1L, 2.0)), all.getValue(Region.FINLAND))
    }

    @Test
    fun `a non-200 response becomes a typed failure`() {
        StubEleringServer(payload.toByteArray(), status = 503).use { unavailable ->
            try {
                runBlocking { PriceApi(endpoint = unavailable.baseUrl).fetchRange(Instant.now(), Instant.now()) }
                fail("Expected PriceApiException")
            } catch (expected: PriceApiException) {
                assertEquals(503, expected.statusCode)
            }
        }
    }
}
