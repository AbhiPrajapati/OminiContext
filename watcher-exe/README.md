# ⚡ OmniContext Standalone Watcher & MCP Executable

A single, zero-dependency Windows executable (`omnicontext-watcher.exe`) for:
1. **Background Auto-Watcher**: Monitors local IDE transcripts (Antigravity IDE, Claude Code CLI, Cursor) and automatically syncs conversations & decisions to your OmniContext Hub (Spring Boot + MongoDB).
2. **Model Context Protocol (MCP) Server**: Provides `save_context_to_hub` and `get_project_context` tools to Cursor, Claude Desktop, and Antigravity IDE over stdio.

---

## 🏃 1. Auto-Watcher Mode (Double-Click & Go)

Simply double-click or run from terminal:
```powershell
.\bin\omnicontext-watcher.exe
```

It will monitor:
- **Antigravity IDE**: `%USERPROFILE%\.gemini\antigravity-ide\brain\`
- **Claude Code CLI**: `%USERPROFILE%\.claude\projects\`
- **Local Drop Folder**: Any `.md` or `.jsonl` dropped in `./.omnicontext/`

Whenever you chat or complete tasks in the terminal/IDE, it automatically compresses and uploads them to `http://localhost:8085/api/contexts`.

---

## 🤖 2. MCP Mode for IDEs (Cursor, Claude Desktop, Antigravity)

To let your AI automatically call `save_context_to_hub` and `get_project_context`, configure your IDE:

### In Cursor (`Settings -> Features -> MCP -> Add New MCP Server`):
- **Name**: `omnicontext`
- **Type**: `command`
- **Command**: `D:\PersonalProject\watcher-exe\bin\omnicontext-watcher.exe --mcp`

### In Claude Desktop (`claude_desktop_config.json`):
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

### In Antigravity IDE (`.agents\mcp_config.json`):
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

---

## 🔨 Rebuilding the EXE (Optional)
If you modify `watcher.py`:
```powershell
pyinstaller --onefile --name omnicontext-watcher --distpath bin watcher.py
```
