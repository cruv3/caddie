package com.llmcompanion.model

data class AnnotatedCapture(
    val annotatedBase64: String,
    val indexedNodes: List<IndexedUiNode>,
)
