package com.caddie.app

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

class AppBackupRulesTest {
    @Test
    fun `private databases and sidecars are excluded from backup and transfer`() {
        val expected =
            setOf("caddie-runtime.db", "caddie-context.db", "caddie-study.db")
                .flatMap { name -> listOf(name, "$name-shm", "$name-wal", "$name-journal") }
                .toSet()

        assertEquals(
            expected,
            databaseExcludes(
                xml = resourceText("backup_rules.xml"),
                section = "full-backup-content",
            ),
        )
        val extraction = resourceText("data_extraction_rules.xml")
        assertEquals(expected, databaseExcludes(extraction, "cloud-backup"))
        assertEquals(expected, databaseExcludes(extraction, "device-transfer"))
    }

    @Test
    fun `XML inspection ignores includes wrong domains and other sections`() {
        val xml =
            """
            <data-extraction-rules>
                <cloud-backup>
                    <include domain="database" path="included.db"/>
                    <exclude domain="file" path="wrong-domain.db"/>
                </cloud-backup>
                <device-transfer>
                    <exclude domain="database" path="other-section.db"/>
                </device-transfer>
            </data-extraction-rules>
            """.trimIndent()

        assertTrue(databaseExcludes(xml, "cloud-backup").isEmpty())
        assertEquals(
            setOf("other-section.db"),
            databaseExcludes(xml, "device-transfer"),
        )
    }

    private fun resourceText(name: String): String =
        File("src/main/res/xml/$name").readText()

    private fun databaseExcludes(
        xml: String,
        section: String,
    ): Set<String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        val sections = document.getElementsByTagName(section)
        assertEquals("Expected one <$section> section", 1, sections.length)
        val parent = sections.item(0) as Element
        return buildSet {
            for (index in 0 until parent.childNodes.length) {
                val child = parent.childNodes.item(index) as? Element ?: continue
                if (
                    child.tagName == "exclude" &&
                    child.getAttribute("domain") == "database"
                ) {
                    add(child.getAttribute("path"))
                }
            }
        }
    }
}
