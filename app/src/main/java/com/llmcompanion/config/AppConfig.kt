package com.llmcompanion.config

import com.llmcompanion.models.LlmBackend

object AppConfig {
    val ACTIVE_BACKEND = LlmBackend.REMOTE_PC
    val REMOTE_MODEL = "qwen2.5:14b"
    val NUM_CTX = 16384
    val PC_IP_ADDRESS = "10.0.2.2"
    val REMOTE_PORT = "11435"
    val OPENAI_KEY = "sk-dein-key..."
}