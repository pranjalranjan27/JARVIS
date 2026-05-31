<p align="center">
  <img src="https://img.shields.io/badge/Java-17+-blue?style=flat-square&logo=openjdk" />
  <img src="https://img.shields.io/badge/Ollama-Mistral-green?style=flat-square" />
  <img src="https://img.shields.io/badge/Frontend-Vanilla%20JS-yellow?style=flat-square" />
  <img src="https://img.shields.io/badge/Database-SQLite-lightgrey?style=flat-square" />
</p>

# 🤖 J.A.R.V.I.S — AI Desktop Assistant

A local AI desktop assistant with a **neon dark-blue cyberpunk UI**, powered by [Ollama](https://ollama.com/) and the Mistral LLM. Jarvis runs entirely on your machine — no API keys, no cloud, no subscriptions.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🧠 **Multi-Mode Personality** | Switch between **Sarcastic**, **Companion**, and **Focus** modes via animated dropdown |
| 💻 **Code Block Rendering** | Fenced code blocks render with syntax-aware dark containers, language labels, and proper formatting |
| 📝 **Markdown Support** | Bold, italic, inline code, headings, and lists render beautifully inside chat |
| 💬 **Persistent Chat History** | Conversations are stored in SQLite and survive restarts |
| 🗑️ **Chat Deletion** | Hover to reveal a delete button — chats are removed from both UI and database |
| 🖥️ **App Control** | Open and close desktop applications via natural language ("Open Chrome", "Close WhatsApp") |
| 🔢 **Math Engine** | Evaluates mathematical expressions directly |
| 📅 **Date & Time** | Responds to time, date, and day queries locally |
| 🎨 **Neon Dark-Blue Theme** | Premium glassmorphism UI with smooth animations and responsive layout |

---

## 🏗️ Architecture

```
JARVIS/
├── frontend/           # Browser UI (HTML + CSS + JS)
│   ├── index.html      # Main page
│   ├── styles.css      # Neon dark-blue theme
│   └── script.js       # Chat controller, markdown renderer
├── src/main/java/com/jarvis/
│   ├── JarvisApp.java          # Entry point
│   ├── api/ApiServer.java      # HTTP server (chat, history, delete)
│   ├── db/DatabaseManager.java # SQLite persistence
│   ├── engine/
│   │   ├── CommandExecutor.java    # App open/close, math, date
│   │   ├── IntentDetector.java     # Intent classification
│   │   ├── InputProcessor.java     # Input normalization
│   │   ├── ConversationContext.java # Session context
│   │   ├── OppositionEngine.java   # Debate/counter-argument engine
│   │   └── ResponseGenerator.java  # Response formatting
│   └── llm/
│       ├── LLMService.java     # LLM interface
│       └── OllamaService.java  # Ollama/Mistral integration
├── lib/                # Runtime dependencies (JARs)
├── run.bat             # One-click build + launch script
└── .gitignore
```

---

## 🚀 Prerequisites

1. **Java 17+** — [Download](https://adoptium.net/)
2. **Ollama** — [Download](https://ollama.com/download)
3. **Mistral model** — Pull it once:
   ```bash
   ollama pull mistral
   ```

---

## ▶️ Quick Start

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/JARVIS.git
cd JARVIS

# Run Jarvis (builds, starts Ollama, launches browser)
run.bat
```

Jarvis will:
1. Compile the Java backend
2. Start Ollama in the background
3. Launch the API server on `http://localhost:8080`
4. Open your browser automatically

---

## 🎭 Personality Modes

| Mode | Style |
|---|---|
| **Sarcastic** (default) | Witty, slightly arrogant, but helpful |
| **Companion** | Warm, emotionally supportive, calm and caring |
| **Focus** | Concise, technical, productivity-oriented — great for coding |

New chats always start in **Sarcastic** mode. Each chat remembers its own mode independently.

---

## 🛠️ Tech Stack

- **Backend:** Java 17 (no frameworks — pure `com.sun.net.httpserver`)
- **LLM:** Ollama + Mistral (local, private, offline-capable)
- **Database:** SQLite via JDBC
- **Frontend:** Vanilla HTML/CSS/JS (no build tools, no npm)
- **Dependencies:** `json-20240303.jar`, `sqlite-jdbc`, `slf4j`

---

## 📜 License

This project is for personal and portfolio use.

---

<p align="center">
  Built with ☕ Java and 🧠 Mistral
</p>
