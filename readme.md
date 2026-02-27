# llm-smartphone-companion 📱🤖

> **Master's Thesis Project:** Enhancing User Trust and Reducing Cognitive Load in Autonomous LLM-based Smartphone Agents through Visual UI Abstraction.

## 📌 Overview
This project introduces a **Generative UI Layer** for Android that acts as a bridge between autonomous LLM agents and the user. While current agents (like AppAgent or Mobile-Agent) focus on technical feasibility, this companion focuses on **Human-Computer Interaction (HCI)**. 

By using a **Persistent On-Screen Avatar**, the system abstracts complex, "restless" AI interactions into a calm, explainable, and trustworthy experience.

## ✨ Key Features & Research Concepts
The companion implements four distinct levels of visual transparency to evaluate their impact on user experience:

1.  **Baseline (Raw Execution):** Direct interaction via `AccessibilityService` without visual overlays.
2.  **Transparent Companion:** Adding the Avatar to explain "intent" via text bubbles while the app remains fully visible.
3.  **Selective Spotlight (Hybrid):** Dimming the background and highlighting only the UI element the AI is currently focused on.
4.  **Solid Canvas (Zero-UI):** Full abstraction where the user only interacts with the Avatar while the agent works invisibly in the background.

## 🛠 Tech Stack
* **Platform:** Android (Min SDK 28+)
* **Language:** Kotlin
* **Core APIs:**
    * `AccessibilityService`: To inspect the UI tree and perform programmatic clicks/scrolls.
    * `WindowManager`: To render the persistent Avatar and Spotlight overlays across the entire OS.
* **AI Backend:** Integration with LLMs (e.g., GPT-4o or Gemini 1.5 Pro) for reasoning and navigation planning.

## 🚀 Getting Started

### Prerequisites
* Android Studio (Latest Version)
* A physical Android device (Recommended for Overlay & Accessibility testing)
* LLM API Key (OpenAI/Google)

### Permissions
This app requires high-level system permissions to function:
1.  **Display over other apps:** Required for the Avatar and Spotlight effects.
2.  **Accessibility Service:** Required to "read" the screen and "click" buttons autonomously.

## 📊 Research Context
This repository is part of a Master's Thesis. The goal is to answer:
* **RQ1:** How do different UI abstraction levels affect system trust?
* **RQ2:** Can a guided visual focus (Spotlight) reduce the user's cognitive load?
* **RQ3:** How does task criticality (e.g., bank transfer vs. music search) influence the need for "Swipe-to-Confirm" control?
