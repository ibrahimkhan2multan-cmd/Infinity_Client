package com.infinity.launcher

/**
 * Describes the HUD options shared between the launcher UI and the Bedrock behavior pack.
 * Pure Kotlin (no Android classes) so it can be unit-tested on the JVM.
 *
 * The pack's scripts/main.js contains a block between CONFIG_BEGIN and CONFIG_END markers;
 * [inject] replaces that block with the user's chosen options.
 */
object PackConfig {
    const val CONFIG_BEGIN = "// INFINITY_CONFIG_BEGIN"
    const val CONFIG_END = "// INFINITY_CONFIG_END"

    const val PREF_ACCENT = "pack_accent"
    const val PREF_MULTILINE = "pack_multiline"
    const val PREF_INTERVAL = "pack_interval"

    const val DEFAULT_ACCENT = "d"
    const val DEFAULT_INTERVAL = 2

    const val PREF_CLIENT_NAME = "client_name"
    const val DEFAULT_CLIENT_NAME = "Infinity's Client"
    const val MAX_NAME_LENGTH = 24

    const val PREF_RES_FOG = "res_clear_fog"
    const val PREF_RES_INVENTORY = "res_black_inventory"
    const val PREF_RES_LOADING = "res_loading_text"

    const val BEHAVIOR_SUFFIX = "Bedrock QoL"
    const val RESOURCE_SUFFIX = "Visuals"

    /** Which parts of the bundled resource pack to include when the launcher builds it. */
    data class ResourceOptions(
        val clearFog: Boolean = true,
        val blackInventory: Boolean = true,
        val loadingText: Boolean = true
    )

    /** [sample] is what the HUD editor preview shows; "{A}" is replaced by the accent colour code. */
    data class Toggle(
        val jsKey: String,
        val prefKey: String,
        val label: String,
        val default: Boolean,
        val sample: String
    )

    /** Order here is the order the HUD is drawn in (must match SEGMENTS order in main.js). */
    val TOGGLES: List<Toggle> = listOf(
        Toggle("brand", "pack_brand", "Client name tag", true, "§{A}{NAME}"),
        Toggle("coords", "pack_coordinates", "Coordinates", true, "§fXYZ 128 64 -310"),
        Toggle("facing", "pack_facing", "Facing direction", false, "§fW §790°"),
        Toggle("dimension", "pack_dimension", "Dimension", true, "§7Overworld"),
        Toggle("nether", "pack_nether", "Nether / Overworld coordinates", false, "§cNether≈ 16 -39"),
        Toggle("target", "pack_target_block", "Target block and coordinates", true, "§bLooking at: §fstone (130, 64, -308)"),
        Toggle("reach", "pack_reach", "Distance to target block", true, "§eReach≈ 2.4m"),
        Toggle("entity", "pack_target_entity", "Target entity (health, distance)", true, "§6Target: §fzombie §c♥18 §73.0m"),
        Toggle("health", "pack_health", "Your health", false, "§c♥ 20/20"),
        Toggle("held", "pack_held", "Held item and durability", false, "§fHeld: iron_sword §7220/250"),
        Toggle("armor", "pack_armor", "Armor durability", false, "§fArmor H:100% C:50% L:-- B:90%"),
        Toggle("speed", "pack_speed", "Speed (blocks/second)", false, "§fSpeed 4.3 b/s"),
        Toggle("time", "pack_time", "In-game time and day", false, "§f09:00 §7Day 5"),
        Toggle("combo", "pack_combo", "Hit / combo counter", true, "§cCombo: 3 §7(best 5) §4✦ HIT -4.0")
    )

    /** Minecraft colour code character to display name. */
    val ACCENTS: List<Pair<String, String>> = listOf(
        "d" to "Purple",
        "b" to "Aqua",
        "a" to "Green",
        "6" to "Gold",
        "c" to "Red",
        "f" to "White"
    )

    val INTERVALS: List<Int> = listOf(1, 2, 4, 10)

    fun validAccent(code: String?): String =
        if (ACCENTS.any { it.first == code }) code!! else DEFAULT_ACCENT

    fun validInterval(ticks: Int): Int = if (ticks in INTERVALS) ticks else DEFAULT_INTERVAL

    /** Builds the JavaScript object literal body. Only fixed keys and validated values are emitted. */
    fun buildConfigBlock(
        values: Map<String, Boolean>,
        accent: String,
        multiline: Boolean,
        interval: Int,
        name: String = DEFAULT_CLIENT_NAME
    ): String {
        val flags = TOGGLES.joinToString(", ") { t ->
            "${t.jsKey}: ${values[t.jsKey] ?: t.default}"
        }
        return "$CONFIG_BEGIN\n" +
            "const CONFIG = {\n" +
            "  $flags,\n" +
            "  accent: \"${validAccent(accent)}\", multiline: $multiline, interval: ${validInterval(interval)}, name: \"${jsonEscape(sanitizeName(name))}\"\n" +
            "};\n" +
            CONFIG_END
    }

    /** Replaces the marked config block in [js]. Throws if the markers are missing. */
    fun inject(js: String, block: String): String {
        val regex = Regex(
            Regex.escape(CONFIG_BEGIN) + "[\\s\\S]*?" + Regex.escape(CONFIG_END)
        )
        check(regex.containsMatchIn(js)) { "Pack script has no Infinity config markers" }
        return regex.replaceFirst(js, Regex.escapeReplacement(block))
    }

    /**
     * Cleans a user-entered client name: no colour-code, format or escape characters, no control
     * characters, single spaces, limited length. Falls back to the default when nothing is left.
     */
    fun sanitizeName(raw: String?): String {
        val cleaned = (raw ?: "")
            .replace(Regex("§."), "")              // colour/format codes
            .replace(Regex("%[0-9A-Za-z]?"), "")   // lang format specifiers such as %s or %1
            .filter { it != '§' && it != '%' && it != '\\' && !it.isISOControl() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_LENGTH)
            .trim()
        return cleaned.ifEmpty { DEFAULT_CLIENT_NAME }
    }

    /** Escapes a sanitized name for use inside a JSON or JavaScript double-quoted string. */
    fun jsonEscape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Replaces the pack header name, e.g. "Infinity's Client • Bedrock QoL". First "name" key only. */
    fun renameManifest(manifestJson: String, suffix: String, name: String): String {
        val match = Regex("(\"name\"\\s*:\\s*\")[^\"]*(\")").find(manifestJson)
            ?: throw IllegalStateException("Manifest has no name field")
        val full = jsonEscape("${sanitizeName(name)} • $suffix")
        return manifestJson.replaceRange(
            match.range, match.groupValues[1] + full + match.groupValues[2]
        )
    }

    /** Fills the {NAME} placeholder in a language file template. */
    fun fillLangTemplate(template: String, name: String): String =
        template.replace("{NAME}", sanitizeName(name))

    /** Decides whether a file in the bundled resource pack is included for the chosen options. */
    fun includeResourceEntry(entryName: String, o: ResourceOptions): Boolean = when {
        entryName.startsWith("fogs/") -> o.clearFog
        entryName.startsWith("ui/") -> o.blackInventory
        entryName.startsWith("texts/") -> o.loadingText
        else -> true
    }

    /** Text for the HUD editor preview, using Minecraft colour codes. */
    fun sampleLine(
        values: Map<String, Boolean>,
        accent: String,
        multiline: Boolean,
        name: String = DEFAULT_CLIENT_NAME
    ): String {
        val a = validAccent(accent)
        val n = sanitizeName(name)
        val parts = TOGGLES
            .filter { values[it.jsKey] ?: it.default }
            .map { it.sample.replace("{A}", a).replace("{NAME}", n) }
        return parts.joinToString(if (multiline) "\n" else " §8| ")
    }
}
