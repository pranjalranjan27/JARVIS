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


## Early Features

- Java backend server
- Ollama LLM integration
- Basic frontend chat UI
- API request handling

## Chat Persistence

Jarvis now supports persistent SQLite-based chat history.

Features:
- Multiple chat sessions
- Saved conversations
- Chat history restoration
- Persistent local storage

## Multi-Chat Support

Jarvis supports independent chat sessions.

Capabilities:
- Sidebar chat history
- Session switching
- Persistent chat restoration
- Independent mode selection per chat

## One-Click Startup

Jarvis supports automatic startup using `run.bat`.

Capabilities:
- Starts backend server automatically
- Launches frontend UI
- Opens browser automatically
- Simplified local setup

## Stability Improvements

Major UI stability fixes were implemented.

Fixes:
- Prevented unwanted page refreshes
- Preserved active chat sessions
- Improved frontend state handling
- Reduced chat interruption issues

## Personality System

Jarvis now includes a sarcastic conversational personality.

Characteristics:
- Witty responses
- Slightly arrogant humor
- Human-like conversational style
- Entertaining interactions

## Companion Mode

Jarvis includes a supportive companion interaction mode.

Features:
- Warm conversational tone
- Emotionally supportive replies
- Calm and caring interactions
- Human-like empathetic behavior

## Focus Mode

Jarvis includes a productivity-oriented focus mode.

Features:
- Concise technical responses
- Better coding assistance
- Reduced conversational clutter
- Productivity-focused interactions

## Markdown Rendering

Jarvis supports formatted AI responses.

Supported formatting:
- Markdown-style bold and italic text
- Inline code formatting
- Syntax-style code blocks
- Structured technical responses

## UI Redesign

Jarvis now features a redesigned neon dark-blue interface.

Enhancements:
- Modern futuristic styling
- Animated dropdown interactions
- Neon glow effects
- Improved readability
- Glassmorphism-inspired components
- Responsive layout improvements

## Chat Management

Jarvis includes chat management functionality.

Features:
- Permanent chat deletion
- Confirmation dialog before deletion
- Automatic sidebar refresh
- SQLite cleanup for deleted chats