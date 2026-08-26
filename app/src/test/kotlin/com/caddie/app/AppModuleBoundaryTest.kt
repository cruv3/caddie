package com.caddie.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class AppModuleBoundaryTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val toolsNamespace = "http://schemas.android.com/tools"
    private val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
    private val expectedPackageRoots = setOf(
        "agent",
        "app",
        "context",
        "executor",
        "model",
        "runtime",
        "status",
        "study",
        "studycontrol",
        "studyportal",
        "tool",
        "wakeword",
    )

    @Test
    fun `native production code is consolidated in one app module`() {
        val caddieRoot = File("src/main/kotlin/com/caddie")
        val packageRoots = caddieRoot
            .listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .toSet()
        assertEquals(expectedPackageRoots, packageRoots)

        assertFalse(
            File("src/main")
                .walkTopDown()
                .filter(File::isFile)
                .any { it.extension == "java" },
        )

        val sourceSets = File("src")
            .listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .toSet()

        assertEquals(setOf("androidTest", "debug", "main", "study", "test"), sourceSets)

        val repositoryRoot = File("..")
        listOf(
            "agent-core",
            "android-executor",
            "context-engine",
            "model-client",
            "runtime-persistence",
            "study-runtime",
            "tool-platform",
        ).forEach { formerModule ->
            assertFalse(File(repositoryRoot, "$formerModule/build.gradle.kts").exists())
        }
    }

    @Test
    fun `manifest exposes only expected Android components and permissions`() {
        val manifest = parseManifest(File("src/main/AndroidManifest.xml"))
        val permissions = (0 until manifest.getElementsByTagName("uses-permission").length)
            .map { index ->
                manifest.getElementsByTagName("uses-permission")
                    .item(index)
                    .attributes
                    .getNamedItemNS(androidNamespace, "name")
                    .nodeValue
            }
            .toSet()

        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.RECORD_AUDIO",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MICROPHONE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            ),
            permissions,
        )
        listOf("uses-permission-sdk-23", "uses-permission-sdk-m").forEach { permissionTag ->
            assertEquals(0, manifest.getElementsByTagName(permissionTag).length)
        }
        assertEquals(
            listOf(
                "activity:com.caddie.app.MainActivity:",
                "activity:com.caddie.app.RuntimeSettingsActivity:",
                "service:androidx.room.MultiInstanceInvalidationService:remove",
                "service:.app.CompanionAccessibilityService:",
                "service:.app.overlay.OverlayService:",
                "service:.wakeword.WakeWordService:",
                "service:.studyportal.StudyPortalService:",
            ),
            manifestComponentDirectives(manifest),
        )
        val applications = manifest.getElementsByTagName("application")
        assertEquals(1, applications.length)
        val application = applications.item(0) as Element
        assertEquals(
            "com.caddie.app.CaddieApplication",
            application.getAttributeNS(androidNamespace, "name"),
        )

        val mergedManifest = File(
            "build/intermediates/merged_manifest/normalDebug/" +
                "processNormalDebugMainManifest/AndroidManifest.xml",
        )
        assertTrue("Missing merged normal debug manifest at ${mergedManifest.path}", mergedManifest.isFile)

        // Allow Caddie's activities, AndroidX auto-added components, and our services.
        val allowedMergedComponents = setOf(
            "activity:com.caddie.app.MainActivity",
            "activity:com.caddie.app.RuntimeSettingsActivity",
            "service:com.caddie.app.CompanionAccessibilityService",
            "service:com.caddie.app.overlay.OverlayService",
            "service:com.caddie.studyportal.StudyPortalService",
            "service:com.caddie.wakeword.WakeWordService",
            "provider:androidx.startup.InitializationProvider",
            "receiver:androidx.profileinstaller.ProfileInstallReceiver",
        )
        assertEquals(
            allowedMergedComponents,
            manifestComponents(parseManifest(mergedManifest)),
        )
        assertFalse(
            File("src/main/AndroidManifest.xml").readText().contains("ContextEngine"),
        )
    }

    @Test
    fun `manifest declares package visibility for https navigation resolution`() {
        val manifest = parseManifest(File("src/main/AndroidManifest.xml"))
        val intents = manifest.getElementsByTagName("queries").item(0)
            .childNodes
        val httpsViewQueryExists = (0 until intents.length)
            .mapNotNull { intents.item(it) as? Element }
            .filter { it.tagName == "intent" }
            .any { intent ->
                val actions = intent.getElementsByTagName("action")
                val categories = intent.getElementsByTagName("category")
                val data = intent.getElementsByTagName("data")
                (0 until actions.length).any { index ->
                    (actions.item(index) as Element)
                        .getAttributeNS(androidNamespace, "name") == "android.intent.action.VIEW"
                } && (0 until categories.length).any { index ->
                    (categories.item(index) as Element)
                        .getAttributeNS(androidNamespace, "name") == "android.intent.category.BROWSABLE"
                } && (0 until data.length).any { index ->
                    (data.item(index) as Element)
                        .getAttributeNS(androidNamespace, "scheme") == "https"
                }
            }

        assertTrue("HTTPS VIEW query is required for PackageManager navigation verification", httpsViewQueryExists)
    }

    private fun parseManifest(file: File): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun manifestComponents(manifest: Document): Set<String> = buildSet {
        componentTags.forEach { component ->
            val elements = manifest.getElementsByTagName(component)
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                add("$component:${element.getAttributeNS(androidNamespace, "name")}")
            }
        }
    }

    private fun manifestComponentDirectives(manifest: Document): List<String> = buildList {
        componentTags.forEach { component ->
            val elements = manifest.getElementsByTagName(component)
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                add(
                    "$component:" +
                        "${element.getAttributeNS(androidNamespace, "name")}:" +
                        element.getAttributeNS(toolsNamespace, "node"),
                )
            }
        }
    }
}
