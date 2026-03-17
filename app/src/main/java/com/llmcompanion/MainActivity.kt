package com.llmcompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.llmcompanion.overlay.AvatarService
import com.llmcompanion.accessibility.AccessibilityService
import com.llmcompanion.config.AppConfig
import com.llmcompanion.logic.AgentManager
import android.provider.Settings
import com.llmcompanion.logic.NpuLLMHandler
import com.llmcompanion.logic.OpenAILLMHandler
import com.llmcompanion.logic.RemoteNetworkLLMHandler
import com.llmcompanion.models.LlmBackend
import com.llmcompanion.utils.Logger
import com.llmcompanion.utils.Permissions
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val activeBackend = LlmBackend.REMOTE_PC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Logger.d(this, "MainActivity wurde erstellt. UI ist geladen.")

        AgentManager.activeLlm  = when (AppConfig.ACTIVE_BACKEND) {
            LlmBackend.NPU -> NpuLLMHandler()
            LlmBackend.OPENAI -> OpenAILLMHandler()
            LlmBackend.REMOTE_PC -> RemoteNetworkLLMHandler()
        }

        initLlmBackground()
    }

    override fun onResume() {
        super.onResume()
        Logger.d(this, "onResume aufgerufen. Prüfe Berechtigungen...")
        checkPermissions()
    }

    private fun initLlmBackground() {
        lifecycleScope.launch {
            Logger.d(this@MainActivity, "Starte LLM-Initialisierung im Hintergrund ($activeBackend)...")
            Toast.makeText(this@MainActivity, "Lade $activeBackend...", Toast.LENGTH_SHORT).show()

            if (AgentManager.activeLlm == null) {
                Logger.e(this@MainActivity, "Kritischer Fehler: AgentManager hat kein aktives LLM! Initialisierung abgebrochen.")
                return@launch
            }

            val success = AgentManager.activeLlm!!.initialize()

            if (success) {
                Logger.d(this@MainActivity, "KI-Modell ($activeBackend) erfolgreich hochgefahren und bereit!")
                Toast.makeText(this@MainActivity, "KI ist bereit!", Toast.LENGTH_SHORT).show()
            } else {
                Logger.e(this@MainActivity, "Fehler beim Laden des KI-Modells ($activeBackend).")
                Toast.makeText(this@MainActivity, "KI konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissions() {
        if (!Permissions.hasAccessibilityPermission(this, AccessibilityService::class.java)) {
            Logger.i(this, "Accessibility-Berechtigung fehlt. Schicke Nutzer in die Einstellungen.")
            Toast.makeText(this, "Bitte aktiviere den LLM Companion", Toast.LENGTH_LONG).show()
            Permissions.requestAccessibilityPermission(this)
            return
        }

        Logger.d(this, "Accessibility erteilt. App wird in den Hintergrund verschoben.")

        moveTaskToBack(true)
    }
}