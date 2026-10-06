# ⚡ OmniContext — Universal Cross-Platform AI Context Hub

> **Store, distill, and synchronize project context across ChatGPT, Claude, Gemini, Cursor, and Copilot with up to 85% token compression.**

OmniContext solves the context fragmentation problem across modern AI models. It captures architecture decisions, state machines, active task queues, and constraints, compresses them into an ultra-dense, token-minimal representation, and exposes unique shareable URLs, MCP tools, and browser sync buttons.

---

## 📂 Project Suite Architecture

```
d:\PersonalProject\
├── backend/                  # Java 21 + Spring Boot 3.3.4 REST API & Compression Engine (Port 8085)
├── frontend/                 # Modern Angular 18 Web Dashboard & Distillation Studio (Port 4250)
├── watcher-exe/              # Standalone Windows EXE watcher & MCP server daemon for IDEs & CLIs
│   └── bin/
│       └── omnicontext-watcher.exe  # Double-click executable or MCP server
└── browser-extension/        # Chrome/Edge Manifest V3 extension for ChatGPT, Claude.ai, & Gemini
```

---

## 🚀 The 3 Ways to Capture Context Automatically

### 1. Standalone Windows Executable (`watcher-exe`)
A single, zero-dependency `.exe` that runs silently in the background:
- **Location**: [`watcher-exe/bin/omnicontext-watcher.exe`](file:///d:/PersonalProject/watcher-exe/bin/omnicontext-watcher.exe)
- **Usage**: Double-click it once! It automatically monitors session files from:
  - Antigravity IDE (`%USERPROFILE%\.gemini\antigravity-ide\brain\`)
  - Claude Code CLI (`%USERPROFILE%\.claude\projects\`)
  - Local project drops (`./.omnicontext/`)
- Whenever you work in the terminal/IDE, it automatically compresses and uploads turns to MongoDB.

### 2. Model Context Protocol (MCP) for IDEs (Cursor, Claude Desktop, Antigravity)
Run the executable in MCP mode:
```powershell
.\watcher-exe\bin\omnicontext-watcher.exe --mcp
```
Add to your IDE's `mcp.json` or `mcp_config.json`:
```json
{
  "mcpServers": {
    "omnicontext": {
      "command": "D:\\PersonalProject\\watcher-exe\\bin\\omnicontext-watcher.exe",
      "args": ["--mcp"]
    }
  }
}
```
*Your AI now has native `save_context_to_hub` and `get_project_context` tools.*

### 3. Browser Extension (`browser-extension`)
For web chats on [ChatGPT](https://chatgpt.com), [Claude.ai](https://claude.ai), and [Gemini](https://gemini.google.com):
1. Open `chrome://extensions/` or `edge://extensions/`.
2. Turn on **Developer mode**.
3. Click **"Load unpacked"** and select [`d:\PersonalProject\browser-extension`](file:///d:/PersonalProject/browser-extension).
4. A floating **`⚡ Sync to OmniContext`** button will appear on chat pages. Click it anytime to compress and sync the chat to your hub in 1 click!

---

## 🛠️ Technology Stack

- **Database**: MongoDB (`mongodb://localhost:27017/omnicontext_db`) with Spring Data MongoDB.
- **Backend**: Java 21, Spring Boot 3.3.4, Spring Data MongoDB, Jakarta Validation.
- **Frontend**: Angular 18 (Standalone Components, Signals, Reactive Forms, Router, Glassmorphic CSS).
- **Executable**: Standalone Windows 64-bit binary (`omnicontext-watcher.exe`).
- **Browser Extension**: Manifest V3 (Chrome, Edge, Brave).

---

## 🏃 Running the Servers

### 1. Backend (Spring Boot & MongoDB)
Connected to MongoDB on `mongodb://localhost:27017/omnicontext_db`. Runs on **Port 8085**:
```bash
cd backend
mvn spring-boot:run
```

### 2. Frontend (Angular)
Runs on **Port 4250**:
```bash
cd frontend
npm start
```
Open **`http://localhost:4250`** in your browser.
