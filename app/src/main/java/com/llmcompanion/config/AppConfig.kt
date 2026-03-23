package com.llmcompanion.config

import com.llmcompanion.models.LlmBackend

object AppConfig {
    val ACTIVE_BACKEND = LlmBackend.REMOTE_PC
    // --- QWEN 3 SERIES (Die neueste Generation) --
    // val REMOTE_MODEL = "qwen3.5" //
    // val REMOTE_MODEL = "qwen3:14b"      // sehr gut
    // val REMOTE_MODEL = "qwen3:8b" // auch gut
    // val REMOTE_MODEL = "qwen3:4b" // Viel zu klein

    // --- QWEN 2.5 ---
    // val REMOTE_MODEL = "qwen2.5:14b" // Schnell genug
    // val REMOTE_MODEL = "qwen2.5-coder:14b" // Befolgt nur anweisungen

    // --- DEEPSEEK R1 (Best Reasoning) ---
    // val REMOTE_MODEL = "deepseek-r1:14b" // Extrem stark in Logik - zu langen Antwortzeit
    val REMOTE_MODEL = "qwen3:14b"
    val NUM_CTX = 16384     // Viel mehr Platz für UI-Baum + Historie
    val MAX_TOKENS = 2048   // Genug Platz, damit das JSON nicht mitten im Wort abbricht
    val TEMPERATURE = 0.1   // Niedriger = Striktere Einhaltung des JSON-Formats
    val PC_IP_ADDRESS = "10.0.2.2"
    val REMOTE_PORT = "11435"
    val OPENAI_KEY = "sk-dein-key..."
}