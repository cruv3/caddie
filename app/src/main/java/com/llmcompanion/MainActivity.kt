package com.llmcompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.llmcompanion.overlay.AvatarService
import com.llmcompanion.accessibility.AccessibilityService
import com.llmcompanion.logic.LocalLlmHandler
import com.llmcompanion.utils.Logger
import com.llmcompanion.utils.Permissions
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var llmHandler: LocalLlmHandler
    private var isTestRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Logger.d(this, "MainActivity wurde erstellt. UI ist geladen.")

        // Init LLM
        llmHandler = LocalLlmHandler(this)
        initLlmInbackground()
    }

    override fun onResume() {
        super.onResume()
        Logger.d(this, "onResume aufgerufen. Prüfe Berechtigungen...")
        checkAllPermissions()
    }

    private fun initLlmInbackground() {
        lifecycleScope.launch {
            Logger.d(this@MainActivity, "Starte LLM-Initialisierung im Hintergrund...")
            Toast.makeText(this@MainActivity, "Lade KI-Modell...", Toast.LENGTH_SHORT).show()

            val success = llmHandler.initialize()

            if (success) {
                Logger.d(this@MainActivity, "Gemini Nano ist im Hintergrund erfolgreich hochgefahren und bereit!")
            } else {
                Logger.e(this@MainActivity, "Fehler beim Laden des KI-Modells.")
                Toast.makeText(this@MainActivity, "KI konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAllPermissions() {
        // 1. Check Overlay Permission
        if (!Permissions.hasOverlayPermission(this)) {
            Logger.i(this, "Overlay-Berechtigung fehlt. Schicke Nutzer in die Einstellungen.")
            Toast.makeText(this, "Bitte erlaube 'Über anderen Apps einblenden'", Toast.LENGTH_LONG).show()
            Permissions.requestOverlayPermission(this)
            return
        }

        // 2. Check Accessibility Permission
        if (!Permissions.hasAccessibilityPermission(this, AccessibilityService::class.java)) {
            Logger.i(this, "Accessibility-Berechtigung fehlt. Schicke Nutzer in die Einstellungen.")
            Toast.makeText(this, "Bitte aktiviere den LLM Companion in den Barrierefreiheitseinstellungen", Toast.LENGTH_LONG).show()
            Permissions.requestAccessibilityPermission(this)
            return
        }

        Logger.d(this, "Alle Berechtigungen sind vorhanden!")
        Toast.makeText(this, "Alle Berechtigungen erteilt. Bereit für die Studie!", Toast.LENGTH_SHORT).show()
        // Start Avatar
        startService(Intent(this, AvatarService::class.java))
    }
}