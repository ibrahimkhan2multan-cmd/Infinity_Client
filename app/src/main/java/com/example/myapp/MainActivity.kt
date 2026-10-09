package com.infinity.launcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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

private val defaultMinecraftPackage = "com.mojang.minecraftpe"
private var pendingApkUri: Uri? = null

private val apkPicker =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingApkUri = uri
            installSelectedApk(uri)
        }
    }

private val installSettings =
    registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingApkUri?.let { uri ->
            if (packageManager.canRequestPackageInstalls()) {
                installSelectedApk(uri)
            } else {
                toast("Allow installation for Infinity's Launcher, then try again.")
            }
        }
    }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    supportActionBar?.hide()
    window.statusBarColor = Color.rgb(8, 11, 24)
    window.navigationBarColor = Color.rgb(8, 11, 24)

    buildHomeScreen()
}

private fun buildHomeScreen() {
    val background = Color.rgb(8, 11, 24)
    val panel = Color.rgb(18, 24, 42)
    val purple = Color.rgb(118, 83, 232)
    val white = Color.WHITE
    val muted = Color.rgb(146, 155, 184)

    val scroll = ScrollView(this).apply {
        setBackgroundColor(background)
        isFillViewport = true
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(32), dp(24), dp(28))
    }

    fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    content.addView(
        text("INFINITY", 13f, purple, true),
        matchWidth()
    )

    content.addView(
        text("INFINITY'S LAUNCHER", 27f, white, true).apply {
            letterSpacing = 0.02f
            setPadding(0, dp(8), 0, dp(6))
        },
        matchWidth()
    )

    content.addView(
        text("YOUR WORLD. YOUR RULES.", 12f, muted),
        matchWidth()
    )

    val gamePanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        setBackgroundColor(panel)
    }

    gamePanel.addView(
        text("MINECRAFT BEDROCK", 15f, white, true),
        matchWidth()
    )

    gamePanel.addView(
        text("Target version: 1.26.33.1", 13f, purple).apply {
            setPadding(0, dp(8), 0, dp(5))
        },
        matchWidth()
    )

    gamePanel.addView(
        text(
            "Keep your existing game and worlds. " +
                "Select your installed game below.",
            12f,
            muted
        ),
        matchWidth()
    )

    val gamePanelParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(28)
    }

    content.addView(gamePanel, gamePanelParams)

    content.addView(
        text("MAIN MENU", 11f, muted, true).apply {
            gravity = Gravity.START
            letterSpacing = 0.15f
            setPadding(0, dp(26), 0, dp(10))
        },
        matchWidth()
    )

    content.addView(
        menuButton("PLAY", purple, white) {
            playSelectedGame()
        },
        buttonParams(58, 0)
    )

    content.addView(
        menuButton("SELECT INSTALLED APP", panel, white) {
            showInstalledApps()
        },
        buttonParams(54, 10)
    )

    content.addView(
        menuButton("IMPORT MINECRAFT APK", panel, white) {
            apkPicker.launch(arrayOf("*/*"))
        },
        buttonParams(54, 10)
    )

    content.addView(
        menuButton("CLIENT MENU", panel, white) {
            showClientMenu()
        },
        buttonParams(54, 10)
    )

    content.addView(
        menuButton("HUD EDITOR", panel, white) {
            showHudEditor()
        },
        buttonParams(54, 10)
    )

    content.addView(
        menuButton("SETTINGS", panel, white) {
            showSettings()
        },
        buttonParams(54, 10)
    )

    content.addView(
        text("INFINITY'S LAUNCHER • ANDROID", 10f, muted).apply {
            setPadding(0, dp(24), 0, 0)
        },
        matchWidth()
    )

    scroll.addView(content)
    setContentView(scroll)
}

private fun menuButton(
    label: String,
    background: Int,
    foreground: Int,
    action: () -> Unit
): MaterialButton {
    return MaterialButton(this).apply {
        text = label
        textSize = 13f
        setTextColor(foreground)
        setAllCaps(false)
        cornerRadius = dp(13)
        backgroundTintList =
            android.content.res.ColorStateList.valueOf(background)
        setOnClickListener { action() }
    }
}

private fun playSelectedGame() {
    val selected = prefs.getString(
        "selected_game_package",
        defaultMinecraftPackage
    ) ?: defaultMinecraftPackage

    val launchIntent = packageManager.getLaunchIntentForPackage(selected)

    if (launchIntent != null) {
        try {
            startActivity(launchIntent)
        } catch (_: Exception) {
            showGameNotFound()
        }
    } else {
        showGameNotFound()
    }
}

private fun showGameNotFound() {
    AlertDialog.Builder(this)
        .setTitle("Game not detected")
        .setMessage(
            "Infinity's Launcher could not open the selected game. " +
                "Choose an installed app or import your Minecraft APK."
        )
        .setPositiveButton("Select installed app") { _, _ ->
            showInstalledApps()
        }
        .setNeutralButton("Import APK") { _, _ ->
            apkPicker.launch(arrayOf("*/*"))
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun showInstalledApps() {
    try {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(
            launcherIntent,
            0
        ).mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName

            if (packageName == packageNameForSelf()) {
                return@mapNotNull null
            }

            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@mapNotNull null

            Triple(
                resolveInfo.loadLabel(packageManager).toString(),
                packageName,
                launchIntent
            )
        }.distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        if (apps.isEmpty()) {
            toast("No launchable apps were found.")
            return
        }

        val labels = apps.map {
            "${it.first}\n${it.second}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select an installed app")
            .setItems(labels) { _, index ->
                val chosen = apps[index]
                prefs.edit()
                    .putString("selected_game_package", chosen.second)
                    .apply()

                toast("Selected ${chosen.first}")
                buildHomeScreen()
            }
            .setNegativeButton("Cancel", null)
            .show()

    } catch (_: Exception) {
        toast(
            "Android could not list installed apps. " +
                "Check the app visibility permission."
        )
    }
}

private fun packageNameForSelf(): String = applicationInfo.packageName

private fun installSelectedApk(uri: Uri) {
    if (!packageManager.canRequestPackageInstalls()) {
        pendingApkUri = uri

        AlertDialog.Builder(this)
            .setTitle("Allow APK installation")
            .setMessage(
                "Android requires your permission before this launcher " +
                    "can open the APK installer."
            )
            .setPositiveButton("Open settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                installSettings.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
        return
    }

    try {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(installIntent)

    } catch (_: Exception) {
        toast("Could not open the APK installer. Try selecting the APK again.")
    }
}

private fun showClientMenu() {
    val modules = arrayOf(
        "FPS counter",
        "CPS counter",
        "Coordinates",
        "Keystrokes",
        "Ping",
        "Zoom",
        "Fullbright",
        "Hitbox display"
    )

    val keys = arrayOf(
        "module_fps",
        "module_cps",
        "module_coordinates",
        "module_keystrokes",
        "module_ping",
        "module_zoom",
        "module_fullbright",
        "module_hitbox"
    )

    val checked = BooleanArray(keys.size) { index ->
        prefs.getBoolean(keys[index], index == 0 || index == 2)
    }

    AlertDialog.Builder(this)
        .setTitle("Client Menu")
        .setMultiChoiceItems(modules, checked) { _, which, isChecked ->
            prefs.edit().putBoolean(keys[which], isChecked).apply()
        }
        .setPositiveButton("Save", null)
        .setNegativeButton("Close", null)
        .show()
}

private fun showHudEditor() {
    val options = arrayOf(
        "Show FPS",
        "Show coordinates",
        "Show keystrokes",
        "Show CPS"
    )

    val keys = arrayOf(
        "hud_fps",
        "hud_coordinates",
        "hud_keystrokes",
        "hud_cps"
    )

    val checked = BooleanArray(keys.size) { index ->
        prefs.getBoolean(keys[index], index < 2)
    }

    AlertDialog.Builder(this)
        .setTitle("HUD Editor")
        .setMessage(
            "These are saved layout preferences. " +
                "In-game rendering requires compatible client integration."
        )
        .setMultiChoiceItems(options, checked) { _, which, isChecked ->
            prefs.edit().putBoolean(keys[which], isChecked).apply()
        }
        .setPositiveButton("Save", null)
        .setNegativeButton("Close", null)
        .show()
}

private fun showSettings() {
    val selected = prefs.getString(
        "selected_game_package",
        defaultMinecraftPackage
    ) ?: defaultMinecraftPackage

    AlertDialog.Builder(this)
        .setTitle("Infinity's Launcher Settings")
        .setMessage(
            "Target version: 1.26.33.1\n\n" +
                "Selected package:\n$selected\n\n" +
                "App version: 1.0"
        )
        .setPositiveButton("Select game") { _, _ ->
            showInstalledApps()
        }
        .setNeutralButton("Reset game selection") { _, _ ->
            prefs.edit()
                .remove("selected_game_package")
                .apply()
            toast("Game selection reset.")
        }
        .setNegativeButton("Close", null)
        .show()
}

private fun matchWidth(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

private fun buttonParams(
    heightDp: Int,
    topMarginDp: Int
): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(heightDp)
    ).apply {
        topMargin = dp(topMarginDp)
    }

private fun dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

}
