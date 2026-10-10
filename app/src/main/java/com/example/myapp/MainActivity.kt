package com.infinity.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("infinity_launcher", MODE_PRIVATE)
    }

    private val minecraftPackage = "com.mojang.minecraftpe"
    private var selectedPackage: String
        get() = prefs.getString("minecraft_package", minecraftPackage)
            ?: minecraftPackage
        set(value) {
            prefs.edit().putString("minecraft_package", value).apply()
        }

    private lateinit var content: LinearLayout

    private val packPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importPack(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        content = createScreen()
        addTitle("INFINITY'S CLIENT")
        addSubtitle("BEDROCK EDITION • ANDROID")

        addButton("PLAY MINECRAFT") {
            launchMinecraft()
        }

        addButton("CLIENT SETUP") {
            showClientSetup()
        }

        addButton("CLIENT MENU") {
            showClientMenu()
        }

        addButton("HUD EDITOR") {
            showHudEditor()
        }

        addButton("SELECT INSTALLED GAME") {
            selectMinecraftApp()
        }

        addButton("IMPORT CLIENT PACK") {
            packPicker.launch(
                arrayOf(
                    "application/octet-stream",
                    "application/zip",
                    "*/*"
                )
            )
        }

        addButton("SETTINGS") {
            showSettings()
        }

        addFooter("Made for Minecraft Bedrock")
        setContentView(makeScrollable(content))
    }

    private fun showClientSetup() {
        content = createScreen()
        addTitle("CLIENT SETUP")
        addSubtitle("Prepare your Minecraft client")

        addInfo(
            "1. Import a compatible .mcpack resource pack.\n\n" +
            "2. Minecraft should open its pack-import screen.\n\n" +
            "3. In Minecraft, activate the pack in the appropriate " +
            "Resource Packs settings.\n\n" +
            "4. Return here and tap PLAY MINECRAFT."
        )

        addButton("IMPORT .MCPACK") {
            packPicker.launch(
                arrayOf(
                    "application/octet-stream",
                    "application/zip",
                    "*/*"
                )
            )
        }

        addButton("OPEN MINECRAFT") {
            launchMinecraft()
        }

        addButton("BACK") {
            showHome()
        }

        setContentView(makeScrollable(content))
    }

    private fun showClientMenu() {
        content = createScreen()
        addTitle("CLIENT MENU")
        addSubtitle("Launcher-side preferences")

        addToggle("FPS HUD", "fps", true)
        addToggle("CPS HUD", "cps", false)
        addToggle("Coordinates HUD", "coordinates", true)
        addToggle("Keystrokes HUD", "keystrokes", false)
        addToggle("Ping HUD", "ping", false)
        addToggle("Zoom", "zoom", false)
        addToggle("Fullbright", "fullbright", false)
        addToggle("Hitbox display", "hitbox", false)

        addInfo(
            "These switches save your preferences in the launcher. " +
            "They do not activate in-game features by themselves; " +
            "each feature needs a compatible Minecraft-side implementation."
        )

        addButton("BACK") {
            showHome()
        }

        setContentView(makeScrollable(content))
    }

    private fun showHudEditor() {
        content = createScreen()
        addTitle("HUD EDITOR")
        addSubtitle("Infinity HUD")

        addInfo(
            "HUD layout editing will work after a compatible " +
            "Minecraft-side HUD implementation is added.\n\n" +
            "Your current launcher can save feature preferences, " +
            "but it cannot reposition Minecraft's in-game HUD directly."
        )

        addButton("BACK") {
            showHome()
        }

        setContentView(makeScrollable(content))
    }

    private fun showSettings() {
        content = createScreen()
        addTitle("SETTINGS")
        addSubtitle("Launcher configuration")

        addInfo("Selected Minecraft package:\n$selectedPackage")

        addButton("USE DEFAULT MINECRAFT") {
            selectedPackage = minecraftPackage
            toast("Default Minecraft package selected")
            showSettings()
        }

        addButton("SELECT INSTALLED GAME") {
            selectMinecraftApp()
        }

        addButton("BACK") {
            showHome()
        }

        setContentView(makeScrollable(content))
    }

    private fun selectMinecraftApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )

        val candidates = activities
            .filter {
                val packageName = it.activityInfo.packageName
                packageName != packageNameOfLauncher()
            }
            .sortedBy {
                it.loadLabel(packageManager).toString()
            }

        if (candidates.isEmpty()) {
            toast("No launchable apps found")
            return
        }

        val labels = candidates.map {
            "${it.loadLabel(packageManager)} (${it.activityInfo.packageName})"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Minecraft")
            .setItems(labels) { _, which ->
                selectedPackage = candidates[which].activityInfo.packageName
                toast("Selected ${selectedPackage}")
                showHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun packageNameOfLauncher(): String =
        applicationContext.packageName

    private fun launchMinecraft() {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(selectedPackage)

        if (launchIntent == null) {
            toast(
                "Minecraft was not found. Install it first or select " +
                "the correct installed game package."
            )
            return
        }

        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            toast("Could not launch Minecraft: ${e.message}")
        }
    }

    private fun importPack(uri: Uri) {
        val mimeTypes = arrayOf(
            "application/octet-stream",
            "application/zip",
            "application/x-minecraft-pack"
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri) ?: mimeTypes[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(fallback)
            } catch (_: Exception) {
                toast(
                    "Could not open the pack. Open the .mcpack file " +
                    "from your file manager and choose Minecraft."
                )
            }
        }
    }

    private fun createScreen(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(28))
            setBackgroundColor(Color.rgb(14, 12, 27))
        }
    }

    private fun makeScrollable(view: LinearLayout): ScrollView {
        return ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 12, 27))
            isFillViewport = true
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun addTitle(text: String) {
        val title = TextView(this).apply {
            this.text = text
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(192, 139, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }
        content.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSubtitle(text: String) {
        val subtitle = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(24))
        }
        content.addView(subtitle)
    }

    private fun addInfo(text: String) {
        val info = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackgroundColor(Color.rgb(30, 26, 48))
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(8), 0, dp(12))
        content.addView(info, params)
    }

    private fun addButton(text: String, action: () -> Unit) {
        val button = MaterialButton(this).apply {
            this.text = text
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(103, 52, 181)
                )
            cornerRadius = dp(14)
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        )
        params.setMargins(0, dp(6), 0, dp(6))
        content.addView(button, params)
    }

    private fun addToggle(
        label: String,
        key: String,
        defaultValue: Boolean
    ) {
        val checkBox = android.widget.CheckBox(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            buttonTintList =
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(192, 139, 255)
                )
            isChecked = prefs.getBoolean(key, defaultValue)

            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        }

        content.addView(
            checkBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )
    }

    private fun addFooter(text: String) {
        val footer = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, dp(24), 0, dp(10))
        }
        content.addView(footer)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
