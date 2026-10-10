
package com.infinity.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("infinity_client_settings", MODE_PRIVATE)
    }

    private lateinit var content: LinearLayout

    private val purple = Color.rgb(176, 75, 255)
    private val background = Color.rgb(13, 10, 24)
    private val panel = Color.rgb(30, 22, 47)
    private val white = Color.rgb(246, 241, 255)
    private val muted = Color.rgb(174, 160, 194)

    private val packPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some document providers do not support persistent permissions.
                }
                prefs.edit().putString("last_imported_pack_uri", uri.toString()).apply()
                Toast.makeText(
                    this,
                    "Pack selected. Import it through Minecraft if Android offers that option.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        showHome()
    }

    private fun createPage(title: String, subtitle: String): LinearLayout {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(background)
            isFillViewport = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }

        scroll.addView(content)
        setContentView(scroll)

        addText(
            title,
            28f,
            white,
            bold = true,
            bottom = 6
        )
        addText(subtitle, 14f, muted, bottom = 20)

        return content
    }

    private fun showHome() {
        createPage(
            "INFINITY'S CLIENT",
            "BEDROCK EDITION  •  ANDROID LAUNCHER"
        )

        addPanelText(
            "WELCOME BACK",
            "Your launcher settings are saved on this device. Choose a section below."
        )

        addButton("▶   PLAY MINECRAFT") {
            launchMinecraft()
        }

        addButton("✦   CLIENT MENU") {
            showClientMenu()
        }

        addButton("▦   HUD EDITOR") {
            showHudEditor()
        }

        addButton("⚙   SETTINGS") {
            showSettings()
        }

        addButton("＋   SELECT / IMPORT PACK") {
            showPackOptions()
        }

        addText(
            "Launcher-side settings do not automatically modify Minecraft gameplay.",
            12f,
            muted,
            top = 18
        )
    }

    private fun showClientMenu() {
        createPage(
            "CLIENT MENU",
            "Toggle and save your preferred client options."
        )

        addButton("←   BACK TO HOME") { showHome() }

        addPanelText(
            "FEATURE SETTINGS",
            "These switches save your preferences inside Infinity's Client. In-game effects require a separate supported integration."
        )

        addSwitch("FPS Counter", "fps", true)
        addSwitch("CPS Counter", "cps", false)
        addSwitch("Coordinates", "coordinates", true)
        addSwitch("Keystrokes", "keystrokes", false)
        addSwitch("Ping Display", "ping", false)
        addSwitch("Zoom", "zoom", false)
        addSwitch("Fullbright", "fullbright", false)
        addSwitch("Hitbox Display", "hitbox", false)
        addSwitch("Armor HUD", "armor_hud", false)
        addSwitch("Inventory HUD", "inventory_hud", false)
        addSwitch("Custom Crosshair", "custom_crosshair", false)

        addButton("SAVE SETTINGS") {
            Toast.makeText(this, "Settings are saved automatically.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHudEditor() {
        createPage(
            "HUD EDITOR",
            "Choose which HUD elements you want enabled."
        )

        addButton("←   BACK TO HOME") { showHome() }

        addPanelText(
            "HUD OPTIONS",
            "This screen stores your layout preferences in the launcher. It does not draw overlays over Minecraft yet."
        )

        addSwitch("Coordinates HUD", "hud_coordinates", true)
        addSwitch("FPS HUD", "hud_fps", true)
        addSwitch("CPS HUD", "hud_cps", false)
        addSwitch("Ping HUD", "hud_ping", false)
        addSwitch("Keystrokes HUD", "hud_keystrokes", false)
        addSwitch("Armor HUD", "hud_armor", false)
        addSwitch("Inventory HUD", "hud_inventory", false)

        addButton("RESET HUD OPTIONS") {
            AlertDialog.Builder(this)
                .setTitle("Reset HUD options?")
                .setMessage("Restore the default HUD preferences?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESET") { _, _ ->
                    val keys = listOf(
                        "hud_coordinates", "hud_fps", "hud_cps",
                        "hud_ping", "hud_keystrokes", "hud_armor",
                        "hud_inventory"
                    )
                    prefs.edit().apply {
                        keys.forEach { remove(it) }
                        apply()
                    }
                    showHudEditor()
                }
                .show()
        }
    }

    private fun showSettings() {
        createPage(
            "SETTINGS",
            "Infinity's Client launcher preferences."
        )

        addButton("←   BACK TO HOME") { showHome() }

        addPanelText(
            "LAUNCHER",
            "App version: 1.1\nMinecraft target: Bedrock 1.26.33.1"
        )

        addSwitch("Remember selected options", "remember_options", true)

        addButton("CLEAR SAVED CLIENT OPTIONS") {
            AlertDialog.Builder(this)
                .setTitle("Clear client options?")
                .setMessage("This resets the saved feature switches in the launcher.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR") { _, _ ->
                    prefs.edit().clear().apply()
                    showSettings()
                }
                .show()
        }

        addText(
            "Do not uninstall Minecraft to use this launcher. Your worlds belong to Minecraft, not this app.",
            12f,
            muted,
            top = 18
        )
    }

    private fun showPackOptions() {
        AlertDialog.Builder(this)
            .setTitle("Resource packs")
            .setItems(arrayOf("Choose a pack file", "Open Minecraft")) { _, which ->
                if (which == 0) {
                    packPicker.launch(
                        arrayOf(
                            "application/zip",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                } else {
                    launchMinecraft()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun addSwitch(
        label: String,
        key: String,
        defaultValue: Boolean
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
            thumbTintList = android.content.res.ColorStateList.valueOf(purple)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        }

        row.addView(text)
        row.addView(toggle)

        content.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )
    }

    private fun addButton(label: String, action: () -> Unit) {
        val button = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(white)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(panel)
            strokeColor =
                android.content.res.ColorStateList.valueOf(purple)
            strokeWidth = dp(1)
            cornerRadius = dp(14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { action() }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
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

        content.addView(
            box,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        )
    }

    private fun addText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        top: Int = 0,
        bottom: Int = 0
    ) {
        val text = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

        content.addView(
            text,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(bottom)
            }
        )
    }

    private fun launchMinecraft() {
        val knownPackages = listOf(
            "com.mojang.minecraftpe"
        )

        for (packageName in knownPackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                try {
                    startActivity(launchIntent)
                    return
                } catch (_: Exception) {
                    // Try the next available option.
                }
            }
        }

        Toast.makeText(
            this,
            "Minecraft was not found as an installed app. Install or select your game first.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
