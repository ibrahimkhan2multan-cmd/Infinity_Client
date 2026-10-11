package com.infinity.launcher

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputFilter
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("infinity_client_settings", MODE_PRIVATE)
    }

    private lateinit var content: LinearLayout
    private var previewView: TextView? = null
    private val purple = Color.rgb(176, 75, 255)
    private val pageBackgroundColor = Color.rgb(13, 10, 24)
    private val panel = Color.rgb(30, 22, 47)
    private val white = Color.rgb(246, 241, 255)
    private val muted = Color.rgb(174, 160, 194)

    private val minecraftPackages = listOf("com.mojang.minecraftpe", "com.mojang.minecraftpe.preview")

    private val customPackPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers do not offer persistable permissions.
                }
                openPackForMinecraft(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBackgroundColor
        window.navigationBarColor = pageBackgroundColor
        window.decorView.systemUiVisibility = 0
        showHome()
    }

    private fun createPage(title: String, subtitle: String): LinearLayout {
        previewView = null
        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageBackgroundColor)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)
        addText(title, 28f, white, bold = true, bottom = 6)
        addText(subtitle, 14f, muted, bottom = 20)
        return content
    }

    // ---- Settings read from SharedPreferences ----------------------------------

    private fun hudValues(): Map<String, Boolean> =
        PackConfig.TOGGLES.associate { it.jsKey to prefs.getBoolean(it.prefKey, it.default) }

    private fun accentCode(): String =
        PackConfig.validAccent(prefs.getString(PackConfig.PREF_ACCENT, PackConfig.DEFAULT_ACCENT))

    private fun multiline(): Boolean = prefs.getBoolean(PackConfig.PREF_MULTILINE, false)

    private fun clientName(): String =
        PackConfig.sanitizeName(prefs.getString(PackConfig.PREF_CLIENT_NAME, PackConfig.DEFAULT_CLIENT_NAME))

    private fun resourceOptions(): PackConfig.ResourceOptions = PackConfig.ResourceOptions(
        clearFog = prefs.getBoolean(PackConfig.PREF_RES_FOG, true),
        blackInventory = prefs.getBoolean(PackConfig.PREF_RES_INVENTORY, true),
        loadingText = prefs.getBoolean(PackConfig.PREF_RES_LOADING, true)
    )

    private fun refreshTicks(): Int =
        PackConfig.validInterval(prefs.getInt(PackConfig.PREF_INTERVAL, PackConfig.DEFAULT_INTERVAL))

    // ---- Screens ---------------------------------------------------------------

    private fun showHome() {
        createPage("INFINITY'S CLIENT", "BEDROCK EDITION  •  ANDROID")
        addPanelText(
            "CLIENT HUB",
            "Launch Minecraft, import the built-in Bedrock QoL pack, or manage your own .mcpack files."
        )
        val mcVersion = installedMinecraftVersion()
        addPanelText(
            "MINECRAFT",
            if (mcVersion != null) "Detected on this device (version $mcVersion)."
            else "Not detected. Install Minecraft Bedrock to use PLAY."
        )
        addButton("▶   PLAY MINECRAFT") { launchMinecraft() }
        addButton("✦   CLIENT MENU") { showClientMenu() }
        addButton("▦   HUD EDITOR") { showHudEditor() }
        addButton("🎨   VISUALS PACK") { showVisualsPack() }
        addButton("📦   BEDROCK PACK MANAGER") { showPackManager() }
        addButton("⚙   SETTINGS") { showSettings() }
        addPanelText(
            "IMPORTANT",
            "In-game features are supplied by the behavior pack, not by launcher switches. Import and activate the pack in Minecraft's world settings. Native hitbox rendering, FPS/ping/CPS counters and changing Minecraft's block outline are not exposed by the standard Bedrock Script API."
        )
        addText("Build ${appVersion()} • Target: Bedrock 1.26.33.1", 12f, muted, top = 8)
    }

    private fun showClientMenu() {
        createPage("CLIENT MENU", "Bedrock pack options and launcher preferences.")
        addButton("←   BACK TO HOME") { showHome() }
        addPanelText(
            "BEDROCK QOL PACK",
            "The bundled pack can show coordinates, dimension, the block you are looking at, target block coordinates, and a hit/combo counter through Minecraft's action bar. Import it below, then activate it for a world."
        )
        addButton("IMPORT BUILT-IN QOL PACK") { importBuiltInPack() }
        addSwitch("Show coordinates in pack", "pack_coordinates", true)
        addSwitch("Show dimension in pack", "pack_dimension", true)
        addSwitch("Show looked-at block in pack", "pack_target_block", true)
        addSwitch("Show hit/combo counter in pack", "pack_combo", true)
        addSwitch("Show target entity in pack", "pack_target_entity", true)
        addSwitch("Show estimated reach in pack", "pack_reach", true)
        addButton("MORE HUD OPTIONS (HUD EDITOR)") { showHudEditor() }
        addText(
            "These switches are saved in the launcher. Tap IMPORT BUILT-IN QOL PACK again after changing them to generate and import a pack with your selected options. If Minecraft already imported an older copy, use the newly imported pack and activate it in the world settings.",
            12f, muted, top = 8
        )
    }

    private fun showHudEditor() {
        createPage("HUD EDITOR", "Action-bar HUD options for the bundled behavior pack.")
        addButton("←   BACK TO HOME") { showHome() }
        addPreviewPanel()
        addPanelText(
            "PACK HUD",
            "The built-in pack uses Minecraft's supported action-bar text, so the HUD is one line of text at a fixed spot near the hotbar. Choose the fields below, then tap IMPORT / UPDATE BUILT-IN PACK to generate a pack with those settings. Minecraft still requires you to activate the imported pack in a world. Free positioning, custom crosshair, inventory overlays, FPS/ping/CPS and native hitboxes cannot be drawn by a Script API pack."
        )
        PackConfig.TOGGLES.forEach { toggle ->
            addSwitch(toggle.label, toggle.prefKey, toggle.default) { updatePreview() }
        }
        addSwitch("Multi-line layout (experimental)", PackConfig.PREF_MULTILINE, false) { updatePreview() }
        addButton("CLIENT NAME:  ${clientName()}") { chooseClientName { showHudEditor() } }
        val accentName = PackConfig.ACCENTS.first { it.first == accentCode() }.second
        addButton("ACCENT COLOUR:  $accentName") { chooseAccent() }
        addButton("REFRESH RATE:  ${refreshLabel(refreshTicks())}") { chooseRefresh() }
        addButton("IMPORT / UPDATE BUILT-IN PACK") { importBuiltInPack() }
        addButton("RESET HUD PREFERENCES") {
            AlertDialog.Builder(this)
                .setTitle("Reset HUD preferences?")
                .setMessage("Restore the saved launcher preferences to defaults?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESET") { _, _ ->
                    val editor = prefs.edit()
                    PackConfig.TOGGLES.forEach { editor.remove(it.prefKey) }
                    editor.remove(PackConfig.PREF_ACCENT)
                    editor.remove(PackConfig.PREF_MULTILINE)
                    editor.remove(PackConfig.PREF_INTERVAL)
                    editor.remove(PackConfig.PREF_CLIENT_NAME)
                    editor.apply()
                    showHudEditor()
                }.show()
        }
        addText(
            "In-game toggles (needs cheats/commands enabled in the world): /scriptevent infinity:toggle coords  •  /scriptevent infinity:hud off",
            12f, muted, top = 8
        )
        updatePreview()
    }

    private fun showPackManager() {
        createPage("BEDROCK PACK MANAGER", "Import packs through Android's Minecraft file handler.")
        addButton("←   BACK TO HOME") { showHome() }
        addPanelText(
            "INFINITY QOL PACK",
            "Built into this APK. Import it into Minecraft, then activate it under the world's Behavior Packs. Script API features may require the world settings available in your Minecraft build."
        )
        addButton("IMPORT BUILT-IN INFINITY QOL PACK") { importBuiltInPack() }
        addButton("SAVE PACK TO DOWNLOADS") { savePackToDownloads() }
        addButton("OPEN VISUALS PACK (FOG / INVENTORY / LOADING)") { showVisualsPack() }
        addButton("SELECT CUSTOM .MCPACK FILE") {
            customPackPicker.launch(arrayOf("*/*", "application/zip", "application/octet-stream"))
        }
        addButton("OPEN MINECRAFT") { launchMinecraft() }
        addText(
            "If Minecraft does not appear when opening a pack, tap SAVE PACK TO DOWNLOADS, then open the .mcpack from your Files app (Downloads) and choose Minecraft. The launcher does not install packs silently.",
            12f, muted, top = 12
        )
    }

    private fun showSettings() {
        createPage("SETTINGS", "Infinity's Client launcher.")
        addButton("←   BACK TO HOME") { showHome() }
        addPanelText("APP", "Version ${appVersion()}\nMinecraft target: Bedrock 1.26.33.1")
        addButton("CLIENT NAME:  ${clientName()}") { chooseClientName { showSettings() } }
        addSwitch("Remember launcher preferences", "remember_options", true)
        addButton("CLEAR SAVED CLIENT OPTIONS") {
            AlertDialog.Builder(this)
                .setTitle("Clear saved options?")
                .setMessage("This only clears Infinity's Client preferences. It does not delete Minecraft or your worlds.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR") { _, _ ->
                    prefs.edit().clear().apply()
                    showSettings()
                }.show()
        }
        addText(
            "Your worlds remain stored by Minecraft. This app does not need to uninstall Minecraft or clear its data.",
            12f, muted, top = 18
        )
    }

    private fun showVisualsPack() {
        createPage("VISUALS PACK", "Resource pack: fog, inventory background and loading screens.")
        addButton("←   BACK TO HOME") { showHome() }
        addPanelText(
            "WHAT IT CHANGES",
            "• Clear water and lava fog in every biome.\n" +
                "• Solid black background behind the inventory (instead of the see-through world).\n" +
                "• Loading-screen titles carry your client name.\n" +
                "• The pack shows your client name in Minecraft's resource pack list.\n\n" +
                "Import it, then activate it under Settings → Global Resources (all worlds) or a world's Resource Packs. " +
                "Servers that send their own resource pack may override it. The launcher cannot test these in-game effects."
        )
        addButton("CLIENT NAME:  ${clientName()}") { chooseClientName { showVisualsPack() } }
        addSwitch("Clear water and lava fog", PackConfig.PREF_RES_FOG, true)
        addSwitch("Solid black inventory background", PackConfig.PREF_RES_INVENTORY, true)
        addSwitch("Client name on loading screens", PackConfig.PREF_RES_LOADING, true)
        addButton("IMPORT / UPDATE VISUALS PACK") { importVisualsPack() }
        addButton("SAVE VISUALS PACK TO DOWNLOADS") { saveVisualsPackToDownloads() }
        addText(
            "Tap IMPORT again after changing options or the client name. If Minecraft says the pack already exists, replace it or remove the old copy in Minecraft's storage settings.",
            12f, muted, top = 8
        )
    }

    private fun chooseClientName(onSaved: () -> Unit) {
        val input = EditText(this).apply {
            setText(clientName())
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(PackConfig.MAX_NAME_LENGTH))
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Client name")
            .setMessage("Shown in the HUD tag, pack names and loading screens. Import the packs again after changing it.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("DEFAULT") { _, _ ->
                prefs.edit().remove(PackConfig.PREF_CLIENT_NAME).apply()
                onSaved()
            }
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString(
                    PackConfig.PREF_CLIENT_NAME, PackConfig.sanitizeName(input.text.toString())
                ).apply()
                onSaved()
            }
            .show()
    }

    // ---- HUD editor helpers -----------------------------------------------------

    private fun addPreviewPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(8, 6, 16))
        }
        box.addView(TextView(this).apply {
            text = "PREVIEW (sample values)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
        })
        val preview = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(white)
            setPadding(0, dp(8), 0, 0)
        }
        previewView = preview
        box.addView(preview)
        content.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })
    }

    private fun updatePreview() {
        val line = PackConfig.sampleLine(hudValues(), accentCode(), multiline(), clientName())
        previewView?.text = if (line.isEmpty()) "(nothing selected)" else renderMinecraftText(line)
    }

    private fun renderMinecraftText(text: String): CharSequence {
        val colors = mapOf(
            '0' to 0x000000, '1' to 0x0000AA, '2' to 0x00AA00, '3' to 0x00AAAA,
            '4' to 0xAA0000, '5' to 0xAA00AA, '6' to 0xFFAA00, '7' to 0xAAAAAA,
            '8' to 0x777777, '9' to 0x5555FF, 'a' to 0x55FF55, 'b' to 0x55FFFF,
            'c' to 0xFF5555, 'd' to 0xFF55FF, 'e' to 0xFFFF55, 'f' to 0xFFFFFF
        )
        val out = SpannableStringBuilder()
        var color = 0xFFFFFF
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                if (code == 'r') color = 0xFFFFFF else colors[code]?.let { color = it }
                i += 2
                continue
            }
            val start = out.length
            out.append(ch)
            out.setSpan(
                ForegroundColorSpan(0xFF000000.toInt() or color),
                start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i++
        }
        return out
    }

    private fun refreshLabel(ticks: Int): String = when (ticks) {
        1 -> "every tick (smoothest)"
        2 -> "every 2 ticks (default)"
        4 -> "every 4 ticks"
        else -> "every 10 ticks (lightest)"
    }

    private fun chooseAccent() {
        val names = PackConfig.ACCENTS.map { it.second }.toTypedArray()
        val current = PackConfig.ACCENTS.indexOfFirst { it.first == accentCode() }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Accent colour")
            .setSingleChoiceItems(names, current) { dialog, which ->
                prefs.edit().putString(PackConfig.PREF_ACCENT, PackConfig.ACCENTS[which].first).apply()
                dialog.dismiss()
                showHudEditor()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun chooseRefresh() {
        val labels = PackConfig.INTERVALS.map { refreshLabel(it) }.toTypedArray()
        val current = PackConfig.INTERVALS.indexOf(refreshTicks()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("HUD refresh rate")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.edit().putInt(PackConfig.PREF_INTERVAL, PackConfig.INTERVALS[which]).apply()
                dialog.dismiss()
                showHudEditor()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ---- Pack building / import -------------------------------------------------

    /**
     * Copies a bundled .mcpack into the cache, letting [transform] rewrite or drop each file.
     * Returning null from [transform] leaves the file out of the generated pack.
     */
    private fun buildPack(assetName: String, transform: (String, ByteArray) -> ByteArray?): File {
        val outFile = File(cacheDir, assetName)
        ZipInputStream(assets.open("packs/$assetName")).use { input ->
            ZipOutputStream(FileOutputStream(outFile)).use { output ->
                var entry = input.nextEntry
                while (entry != null) {
                    val bytes = input.readBytes()
                    val result = transform(entry.name, bytes)
                    if (result != null) {
                        output.putNextEntry(ZipEntry(entry.name))
                        output.write(result)
                        output.closeEntry()
                    }
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        }
        return outFile
    }

    /** The HUD behavior pack with the user's HUD options and client name written in. */
    private fun buildConfiguredPack(): File {
        val name = clientName()
        val block = PackConfig.buildConfigBlock(hudValues(), accentCode(), multiline(), refreshTicks(), name)
        return buildPack("Infinity_QoL_Behavior.mcpack") { entry, bytes ->
            when (entry) {
                "scripts/main.js" ->
                    PackConfig.inject(bytes.toString(Charsets.UTF_8), block).toByteArray(Charsets.UTF_8)
                "manifest.json" ->
                    PackConfig.renameManifest(bytes.toString(Charsets.UTF_8), PackConfig.BEHAVIOR_SUFFIX, name)
                        .toByteArray(Charsets.UTF_8)
                else -> bytes
            }
        }
    }

    /** The visuals resource pack with only the chosen parts included. */
    private fun buildVisualsPack(): File {
        val options = resourceOptions()
        require(options.clearFog || options.blackInventory || options.loadingText) {
            "Turn on at least one visuals option first"
        }
        val name = clientName()
        return buildPack("Infinity_UI_Resources.mcpack") { entry, bytes ->
            when {
                !PackConfig.includeResourceEntry(entry, options) -> null
                entry == "manifest.json" ->
                    PackConfig.renameManifest(bytes.toString(Charsets.UTF_8), PackConfig.RESOURCE_SUFFIX, name)
                        .toByteArray(Charsets.UTF_8)
                entry.startsWith("texts/") && entry.endsWith(".lang") ->
                    PackConfig.fillLangTemplate(bytes.toString(Charsets.UTF_8), name)
                        .toByteArray(Charsets.UTF_8)
                else -> bytes
            }
        }
    }

    private fun importBuiltInPack() = importPack("the built-in pack") { buildConfiguredPack() }

    private fun importVisualsPack() = importPack("the visuals pack") { buildVisualsPack() }

    private fun importPack(label: String, builder: () -> File) {
        try {
            val outFile = builder()
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", outFile
            )
            openPackForMinecraft(uri)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not prepare $label: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun savePackToDownloads() = savePack { buildConfiguredPack() }

    private fun saveVisualsPackToDownloads() = savePack { buildVisualsPack() }

    private fun savePack(builder: () -> File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                this,
                "Saving to Downloads needs Android 10 or newer. Use the IMPORT button instead.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val file = builder()
            val name = saveToDownloads(file)
            Toast.makeText(
                this,
                "Saved $name to Downloads. Open it from your Files app and choose Minecraft.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not save the pack: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Downloads is not available")
        val stream = contentResolver.openOutputStream(target)
            ?: throw IllegalStateException("Could not open the Downloads file")
        stream.use { out -> file.inputStream().use { it.copyTo(out) } }
        return file.name
    }

    /**
     * Tries to hand the pack straight to Minecraft first (no chooser), using the MIME types
     * Android file managers commonly send for .mcpack files. Returns true if Minecraft accepted
     * the intent. This only starts Minecraft's own import screen; it cannot confirm the import.
     */
    private fun openPackInMinecraftDirectly(uri: Uri): Boolean {
        val mimeTypes = listOf<String?>(null, "application/octet-stream", "application/zip")
        for (pkg in minecraftPackages) {
            if (installedPackageVersion(pkg) == null) continue
            for (mime in mimeTypes) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    if (mime == null) setData(uri) else setDataAndType(uri, mime)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(intent)
                    return true
                } catch (_: Exception) {
                    // This MIME type did not match Minecraft's filters; try the next one.
                }
            }
        }
        return false
    }

    private fun openPackForMinecraft(uri: Uri) {
        if (openPackInMinecraftDirectly(uri)) return
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(viewIntent, "Import pack into Minecraft"))
        } catch (_: ActivityNotFoundException) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(shareIntent, "Open pack with"))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "No app can open this pack. Try SAVE PACK TO DOWNLOADS, then open it from your Files app.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---- UI building blocks -----------------------------------------------------

    private fun addSwitch(
        label: String, key: String, defaultValue: Boolean, onChange: (() -> Unit)? = null
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(12), dp(8))
            setBackgroundColor(panel)
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 15f
            setTextColor(white)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        val toggle = SwitchCompat(this).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            thumbTintList = ColorStateList.valueOf(purple)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChange?.invoke()
            }
        }
        row.addView(text)
        row.addView(toggle)
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
    }

    private fun addButton(label: String, action: () -> Unit) {
        val button = MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            backgroundTintList = ColorStateList.valueOf(panel)
            strokeColor = ColorStateList.valueOf(purple)
            strokeWidth = dp(1)
            cornerRadius = dp(14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { action() }
        }
        content.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })
    }

    private fun addPanelText(heading: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(panel)
        }
        box.addView(TextView(this).apply {
            text = heading
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
        })
        box.addView(TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(white)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })
    }

    private fun addText(
        value: String, size: Float, color: Int,
        bold: Boolean = false, top: Int = 0, bottom: Int = 0
    ) {
        val text = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }
        content.addView(text, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) })
    }

    // ---- Minecraft / app info ---------------------------------------------------

    private fun installedPackageVersion(pkg: String): String? =
        try {
            packageManager.getPackageInfo(pkg, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            null // Not installed or not visible.
        }

    private fun installedMinecraftVersion(): String? {
        for (pkg in minecraftPackages) {
            installedPackageVersion(pkg)?.let { return it }
        }
        return null
    }

    private fun appVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    private fun launchMinecraft() {
        for (pkg in minecraftPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Try the next package, then show the message below.
            }
        }
        Toast.makeText(
            this,
            "Minecraft was not found as an installed, launchable app.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
