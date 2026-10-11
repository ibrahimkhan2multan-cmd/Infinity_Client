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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
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
        val line = PackConfig.sampleLine(hudValues(), accentCode(), multiline())
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

    /** Copies the bundled pack into the cache with the user's HUD options written into main.js. */
    private fun buildConfiguredPack(): File {
        val assetName = "Infinity_QoL_Behavior.mcpack"
        val outFile = File(cacheDir, assetName)
        val block = PackConfig.buildConfigBlock(hudValues(), accentCode(), multiline(), refreshTicks())
        ZipInputStream(assets.open("packs/$assetName")).use { input ->
            ZipOutputStream(FileOutputStream(outFile)).use { output ->
                var entry = input.nextEntry
                while (entry != null) {
                    val bytes = input.readBytes()
                    output.putNextEntry(ZipEntry(entry.name))
                    val contentBytes = if (entry.name == "scripts/main.js") {
                        PackConfig.inject(bytes.toString(Charsets.UTF_8), block)
                            .toByteArray(Charsets.UTF_8)
                    } else {
                        bytes
                    }
                    output.write(contentBytes)
                    output.closeEntry()
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        }
        return outFile
    }

    private fun importBuiltInPack() {
        try {
            val outFile = buildConfiguredPack()
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", outFile
            )
            openPackForMinecraft(uri)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not prepare the built-in pack: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun savePackToDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                this,
                "Saving to Downloads needs Android 10 or newer. Use IMPORT BUILT-IN INFINITY QOL PACK instead.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val file = buildConfiguredPack()
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

    private fun openPackForMinecraft(uri: Uri) {
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

    private fun installedMinecraftVersion(): String? {
        for (pkg in minecraftPackages) {
            try {
                val info = packageManager.getPackageInfo(pkg, 0)
                return info.versionName ?: "unknown"
            } catch (_: Exception) {
                // Not installed or not visible; try the next package name.
            }
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
