package com.caddie.studyportal

import android.content.Context
import com.caddie.app.composition.NativeExecutionComposition
import com.caddie.app.overlay.OverlaySettings
import com.caddie.study.portal.StudyPortalApi
import com.caddie.study.portal.http.installStudyRoutes
import com.caddie.study.portal.http.StudyPortalHandoffs
import com.caddie.study.portal.http.installStudyPortalAccessRoutes
import com.caddie.study.portal.http.installStudyPortalSecurityHeaders
import com.caddie.study.portal.http.installStudyPortalErrorHandling
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Hosts the study frontend and its Android-native API in the app process. */
class StudyPortalServer private constructor(
    private val context: Context?,
    private val port: Int,
    private val portalApi: StudyPortalApi?,
    @Suppress("UNUSED_PARAMETER") testOnly: Boolean,
) {
    constructor(
        context: Context,
        port: Int,
        portalApi: StudyPortalApi? = null,
    ) : this(context, port, portalApi, false)

    internal constructor(port: Int) : this(null, port, null, true)

    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val handoffs = StudyPortalHandoffs()

    val isRunning: Boolean
        @Synchronized get() = engine != null

    val runtimeSnapshot: JsonObject
        get() = JsonObject(
            mapOf(
                "nativeExecution" to JsonPrimitive(
                    NativeExecutionComposition.shadow().productionEnabled,
                ),
                "contextEngine" to JsonPrimitive(true),
                "pillEnabled" to JsonPrimitive(OverlaySettings.isPillEnabled(requireNotNull(context))),
                "studyGate" to JsonPrimitive(portalApi != null),
            ),
        )

    @Synchronized
    fun start() {
        if (engine != null) return
        val created = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            configurePortal()
        }
        created.start(wait = false)
        engine = created
    }

    @Synchronized
    fun stop() {
        val running = engine ?: return
        try {
            running.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
        } finally {
            engine = null
        }
    }

    private fun Application.configurePortal() {
        intercept(ApplicationCallPipeline.Call) {
            if (!isPortalPeerAllowed(call.request.local.remoteHost)) {
                call.respond(HttpStatusCode.Forbidden)
                finish()
            }
        }
        installStudyPortalSecurityHeaders()
        install(ContentNegotiation) {
            json(Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installStudyPortalErrorHandling()
        install(CallLogging)
        routing {
            get("/study/app") {
                call.respondAsset("study-portal/index.html", ContentType.Text.Html)
            }
            get("/study/app/static/app.css") {
                call.respondAsset("study-portal/app.css", ContentType.Text.CSS)
            }
            get("/study/app/static/app.js") {
                call.respondAsset("study-portal/app.js", ContentType.Text.JavaScript)
            }
            get("/study/app/api/health") {
                call.respond(JsonObject(mapOf("ok" to JsonPrimitive(true))))
            }
            portalApi?.let { api ->
                installStudyPortalAccessRoutes(
                    api = api,
                    auth = null,
                    handoffs = handoffs,
                    runtimeSnapshot = { runtimeSnapshot },
                )
                installStudyRoutes(api, handoffs, auth = null)
            }
        }
    }

    private suspend fun ApplicationCall.respondAsset(path: String, contentType: ContentType) {
        val bytes = withContext(Dispatchers.IO) {
            requireNotNull(context).assets.open(path).use { it.readBytes() }
        }
        respondBytes(bytes, contentType)
    }

    companion object {
        /** Returns a URL another Tailscale peer can open. */
        fun deviceUrl(port: Int): String {
            val addresses = buildList {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@buildList
                while (interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (!network.isUp || network.isLoopback) continue
                    val networkAddresses = network.inetAddresses
                    while (networkAddresses.hasMoreElements()) {
                        val address = networkAddresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            address.hostAddress?.let(::add)
                        }
                    }
                }
            }
            return reachableUrl(port, addresses)
        }

        internal fun reachableUrl(port: Int, addresses: List<String>): String {
            val host = addresses.firstOrNull(::isTailscaleAddress)
                ?: "127.0.0.1"
            return "http://$host:$port/study/app"
        }

        internal fun isPortalPeerAllowed(address: String): Boolean {
            val normalized = address.substringBefore('%').removeSurrounding("[", "]").lowercase()
            if (normalized == "localhost") return true
            val peer = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return false
            if (peer.isLoopbackAddress) return true
            val bytes = peer.address
            val ipv4 = when {
                bytes.size == 4 -> bytes
                bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } &&
                    bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() -> bytes.copyOfRange(12, 16)
                else -> null
            }
            if (ipv4 != null) {
                val first = ipv4[0].toInt() and 0xff
                val second = ipv4[1].toInt() and 0xff
                return first == 100 && second in 64..127
            }
            return bytes.size == 16 &&
                (bytes[0].toInt() and 0xff) == 0xfd &&
                (bytes[1].toInt() and 0xff) == 0x7a &&
                (bytes[2].toInt() and 0xff) == 0x11 &&
                (bytes[3].toInt() and 0xff) == 0x5c &&
                (bytes[4].toInt() and 0xff) == 0xa1 &&
                (bytes[5].toInt() and 0xff) == 0xe0
        }

        private fun isTailscaleAddress(address: String): Boolean {
            val octets = address.split('.').mapNotNull(String::toIntOrNull)
            return octets.size == 4 && octets[0] == 100 && octets[1] in 64..127
        }
    }
}
