package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.domain.Provider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rule "only verified package IDs in the manifest queries": the set declared in
 * AndroidManifest.xml must equal the set in [Provider], so neither can drift or gain a guess.
 */
class ManifestQueriesTest {

    private fun manifest(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).first { it.exists() }

    private val doc by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest()).documentElement
    }

    private fun attr(tag: String): List<String> {
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("android:name").nodeValue
        }
    }

    @Test
    fun `queries declare exactly the provider packages`() {
        val queries = doc.getElementsByTagName("queries").item(0)
        val declared = mutableSetOf<String>()
        for (i in 0 until queries.childNodes.length) {
            val n = queries.childNodes.item(i)
            if (n.nodeName == "package") declared += n.attributes.getNamedItem("android:name").nodeValue
        }
        assertEquals(Provider.entries.map { it.packageName }.toSet(), declared)
    }

    @Test
    fun `provider package ids are well formed and unique`() {
        val ids = Provider.entries.map { it.packageName }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("bad id $it", Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+").matches(it)) }
    }

    @Test
    fun `no broad package visibility or privileged permissions`() {
        val perms = attr("uses-permission")
        assertFalse(perms.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertEquals(listOf("android.permission.INTERNET"), perms)
        assertEquals(0, doc.getElementsByTagName("service").length) // no accessibility service
    }
}
