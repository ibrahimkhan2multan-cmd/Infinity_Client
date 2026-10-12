package com.infinity.launcher

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackConfigTest {

    private fun shippedScript(): String {
        // Gradle runs unit tests with the module directory (app/) as working directory.
        val pack = File("src/main/assets/packs/Infinity_QoL_Behavior.mcpack")
        ZipFile(pack).use { zip ->
            return zip.getInputStream(zip.getEntry("scripts/main.js")).readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun resourceEntries(): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipFile(File("src/main/assets/packs/Infinity_UI_Resources.mcpack")).use { zip ->
            for (e in zip.entries()) out[e.name] = zip.getInputStream(e).readBytes()
        }
        return out
    }

    private fun defaults(): Map<String, Boolean> = PackConfig.TOGGLES.associate { it.jsKey to it.default }

    @Test
    fun injectReplacesOnlyTheMarkedBlock() {
        val js = shippedScript()
        val values = defaults().toMutableMap().apply { put("coords", false); put("speed", true) }
        val block = PackConfig.buildConfigBlock(values, "b", true, 4)
        val out = PackConfig.inject(js, block)
        assertTrue(out.contains("coords: false"))
        assertTrue(out.contains("speed: true"))
        assertTrue(out.contains("accent: \"b\", multiline: true, interval: 4"))
        assertEquals(1, Regex("const CONFIG =").findAll(out).count())
        assertEquals(js.substringBefore(PackConfig.CONFIG_BEGIN), out.substringBefore(PackConfig.CONFIG_BEGIN))
        assertEquals(js.substringAfter(PackConfig.CONFIG_END), out.substringAfter(PackConfig.CONFIG_END))
    }

    @Test
    fun launcherDefaultsMatchDefaultsShippedInScript() {
        val js = shippedScript()
        val block = PackConfig.buildConfigBlock(
            defaults(), PackConfig.DEFAULT_ACCENT, false, PackConfig.DEFAULT_INTERVAL
        )
        fun norm(s: String) = s.replace(Regex("\\s+"), " ")
        val shipped = js.substring(js.indexOf(PackConfig.CONFIG_BEGIN), js.indexOf(PackConfig.CONFIG_END) + PackConfig.CONFIG_END.length)
        assertEquals(norm(block), norm(shipped))
    }

    @Test
    fun toggleKeysMatchScriptToggleList() {
        val js = shippedScript()
        val listText = Regex("const TOGGLE_KEYS = \\[([\\s\\S]*?)\\];").find(js)!!.groupValues[1]
        val scriptKeys = Regex("\"(\\w+)\"").findAll(listText).map { it.groupValues[1] }.toList()
        assertEquals(PackConfig.TOGGLES.map { it.jsKey }, scriptKeys)
    }

    @Test
    fun segmentOrderMatchesPreviewOrder() {
        val js = shippedScript()
        val segmentKeys = Regex("^  \\[\"(\\w+)\", ", RegexOption.MULTILINE).findAll(js).map { it.groupValues[1] }.toList()
        assertEquals(PackConfig.TOGGLES.filter { it.inHud }.map { it.jsKey }, segmentKeys)
    }

    @Test
    fun injectIsSafeWithSpecialCharactersAndRepeatable() {
        val js = shippedScript()
        val once = PackConfig.inject(js, PackConfig.buildConfigBlock(defaults(), "d", false, 2))
        val twice = PackConfig.inject(once, PackConfig.buildConfigBlock(defaults(), "a", false, 1))
        assertTrue(twice.contains("accent: \"a\""))
        assertFalse(twice.contains("accent: \"d\""))
    }

    @Test(expected = IllegalStateException::class)
    fun injectFailsLoudlyWhenMarkersMissing() {
        PackConfig.inject("const x = 1;", PackConfig.buildConfigBlock(defaults(), "d", false, 2))
    }

    @Test
    fun invalidAccentAndIntervalFallBackToDefaults() {
        val block = PackConfig.buildConfigBlock(defaults(), "\"; alert(1); //", false, 999)
        assertTrue(block.contains("accent: \"d\""))
        assertTrue(block.contains("interval: 2"))
    }

    @Test
    fun sampleLineFollowsToggles() {
        val values = defaults().toMutableMap().apply { PackConfig.TOGGLES.forEach { put(it.jsKey, false) }; put("coords", true); put("combo", true) }
        assertEquals("§fXYZ 128 64 -310 §8| §cCombo: 3 §7(best 5) §4✦ HIT -4.0", PackConfig.sampleLine(values, "d", false))
        assertTrue(PackConfig.sampleLine(values, "d", true).contains("\n"))
        assertEquals("§fXYZ 128 64 -310", PackConfig.sampleLine(values.toMutableMap().apply { put("combo", false) }, "d", false))
    }

    @Test
    fun sanitizeNameStripsUnsafeCharactersAndLimitsLength() {
        assertEquals("Infinity's Client", PackConfig.sanitizeName(null))
        assertEquals("Infinity's Client", PackConfig.sanitizeName("   "))
        assertEquals("Nova Client", PackConfig.sanitizeName("§cNova\n  Client%s"))
        assertEquals("Ab\"c", PackConfig.sanitizeName("A\\b\"c"))
        assertEquals(PackConfig.MAX_NAME_LENGTH, PackConfig.sanitizeName("x".repeat(100)).length)
    }

    @Test
    fun customNameIsEscapedInConfigBlock() {
        val block = PackConfig.buildConfigBlock(defaults(), "d", false, 2, "My \"Best\" Client")
        assertTrue(block.contains("name: \"My \\\"Best\\\" Client\""))
        val out = PackConfig.inject(shippedScript(), block)
        assertTrue(out.contains("name: \"My \\\"Best\\\" Client\""))
    }

    @Test
    fun renameManifestChangesOnlyHeaderName() {
        val manifest = """{"header": {"name": "Old • Visuals", "uuid": "u"}, "modules": [{"type": "resources"}]}"""
        val out = PackConfig.renameManifest(manifest, "Visuals", "Nova")
        assertEquals("""{"header": {"name": "Nova • Visuals", "uuid": "u"}, "modules": [{"type": "resources"}]}""", out)
    }

    @Test
    fun resourcePackOptionsSelectEntries() {
        val all = PackConfig.ResourceOptions(true, true, true, true)
        val none = PackConfig.ResourceOptions(false, false, false, false)
        for (name in listOf("fogs/default_fog_setting.json", "ui/inventory_screen.json", "texts/en_US.lang", "textures/ui/title.png")) {
            assertTrue(PackConfig.includeResourceEntry(name, all))
            assertFalse(PackConfig.includeResourceEntry(name, none))
        }
        assertTrue(PackConfig.includeResourceEntry("manifest.json", none))
        assertTrue(PackConfig.includeResourceEntry("pack_icon.png", none))
        assertFalse(PackConfig.includeResourceEntry("ui/inventory_screen.json", PackConfig.ResourceOptions(true, false, true, true)))
    }

    @Test
    fun shippedResourcePackIsComplete() {
        val entries = resourceEntries()
        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("pack_icon.png"))
        assertTrue(entries.containsKey("ui/inventory_screen.json"))
        assertTrue(entries.containsKey("ui/inventory_screen_pocket.json"))
        val logo = entries["textures/ui/title.png"]!!
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), logo.take(4).map { it.toInt() and 0xFF })
        fun int(o: Int) = ((logo[o].toInt() and 0xFF) shl 24) or ((logo[o + 1].toInt() and 0xFF) shl 16) or
            ((logo[o + 2].toInt() and 0xFF) shl 8) or (logo[o + 3].toInt() and 0xFF)
        assertEquals(1768, int(16)); assertEquals(333, int(20))
        val fogs = entries.filterKeys { it.startsWith("fogs/") }
        assertTrue(fogs.size >= 70)
        for ((name, bytes) in fogs) {
            val text = bytes.toString(Charsets.UTF_8)
            for (key in listOf("\"water\"", "\"lava\"", "\"lava_resistance\"")) assertTrue("$name lacks $key", text.contains(key))
            assertFalse("$name still has transition_fog", text.contains("transition_fog"))
        }
        val langs = entries.filterKeys { it.startsWith("texts/") && it.endsWith(".lang") }
        assertTrue(langs.isNotEmpty())
        for ((name, bytes) in langs) {
            val filled = PackConfig.fillLangTemplate(bytes.toString(Charsets.UTF_8), "Nova")
            assertFalse("$name has unfilled placeholder", filled.contains("{NAME}"))
            assertTrue(filled.contains("Nova • "))
        }
    }

    @Test
    fun sampleLineUsesClientName() {
        val values = PackConfig.TOGGLES.associate { it.jsKey to (it.jsKey == "brand") }
        assertEquals("§dNova", PackConfig.sampleLine(values, "d", false, "Nova"))
    }

    @Test
    fun versionComparison() {
        assertEquals(PackConfig.Compat.MATCH, PackConfig.compareVersion("1.26.33.1"))
        assertEquals(PackConfig.Compat.MATCH, PackConfig.compareVersion("1.26.33.1-beta"))
        assertEquals(PackConfig.Compat.SAME_SERIES, PackConfig.compareVersion("1.26.33"))
        assertEquals(PackConfig.Compat.SAME_SERIES, PackConfig.compareVersion("1.26.60.2"))
        assertEquals(PackConfig.Compat.DIFFERENT, PackConfig.compareVersion("1.21.80.3"))
        assertEquals(PackConfig.Compat.UNKNOWN, PackConfig.compareVersion(null))
        assertEquals(PackConfig.Compat.UNKNOWN, PackConfig.compareVersion("unknown"))
    }

    @Test
    fun featureListIsHonest() {
        assertTrue(PackConfig.FEATURES.any { it.support == PackConfig.Support.IMPLEMENTED })
        for (f in PackConfig.FEATURES.filter { it.support == PackConfig.Support.NOT_POSSIBLE }) {
            assertTrue("${f.name} needs a reason", f.how.length > 10)
        }
        assertTrue(PackConfig.TOGGLES.any { it.jsKey == "fullbright" && !it.inHud })
    }
}
