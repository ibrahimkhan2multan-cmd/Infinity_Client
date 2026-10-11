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
        assertEquals(PackConfig.TOGGLES.map { it.jsKey }, segmentKeys)
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
}
