#!/usr/bin/env python3
"""
OmniContext Universal Watcher & MCP Daemon
Standalone Executable (.exe) for Windows
"""

import sys
import os
import time
import json
import urllib.request
import urllib.error
from pathlib import Path

API_BASE = os.environ.get("OMNICONTEXT_API", "http://localhost:8085/api")
IS_MCP_MODE = "--mcp" in sys.argv

def make_request(endpoint, method="GET", data=None):
    url = f"{API_BASE}{endpoint}"
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        
    try:
        with urllib.request.urlopen(req, data=body, timeout=10) as resp:
            resp_data = resp.read().decode("utf-8")
            if resp_data:
                return json.loads(resp_data)
            return None
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        raise RuntimeError(f"HTTP {e.code}: {error_body}")
    except Exception as e:
        raise RuntimeError(str(e))

def handle_mcp():
    sys.stderr.write("[OmniContext MCP] Daemon listening on stdio (JSON-RPC 2.0)...\n")
    sys.stderr.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
            req_id = req.get("id")
            method = req.get("method")
            params = req.get("params", {})

            if method == "initialize":
                res = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {"tools": {}},
                        "serverInfo": {"name": "omnicontext-mcp", "version": "1.0.0"}
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "notifications/initialized":
                continue

            elif method == "tools/list":
                res = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "tools": [
                            {
                                "name": "save_context_to_hub",
                                "description": "Save current project context, decisions, rules, and tasks to OmniContext Hub.",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "title": {"type": "string", "description": "Context title"},
                                        "project": {"type": "string", "description": "Project workspace name"},
                                        "rawContent": {"type": "string", "description": "State, decisions, code signatures, tasks"},
                                        "tags": {"type": "string", "description": "Comma-separated tags"}
                                    },
                                    "required": ["title", "rawContent"]
                                }
                            },
                            {
                                "name": "get_project_context",
                                "description": "Retrieve ground-truth context capsules for a project.",
                                "inputSchema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string", "description": "Project name or search keyword"}
                                    }
                                }
                            }
                        ]
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "tools/call":
                tool_name = params.get("name")
                args = params.get("arguments", {})

                if tool_name == "save_context_to_hub":
                    try:
                        saved = make_request("/contexts", method="POST", data={
                            "title": args.get("title", "Active Task Context"),
                            "project": args.get("project", "General"),
                            "rawContent": args.get("rawContent", ""),
                            "tags": args.get("tags", ""),
                            "compressionStrategy": "SEMANTIC_DENSE"
                        })
                        res = {
                            "jsonrpc": "2.0",
                            "id": req_id,
                            "result": {
                                "content": [{
                                    "type": "text",
                                    "text": f"Successfully synced context to OmniContext!\nID: {saved['id']}\nSlug: {saved['shareSlug']}\nTokens: {saved['originalTokens']} -> {saved['compressedTokens']} ({saved['compressionRatio']}% saved)\nShare Link: http://localhost:4250/c/{saved['shareSlug']}"
                                }]
                            }
                        }
                    except Exception as ex:
                        res = {
                            "jsonrpc": "2.0",
                            "id": req_id,
                            "result": {
                                "isError": True,
                                "content": [{"type": "text", "text": f"Error saving context: {str(ex)}"}]
                            }
                        }
                    sys.stdout.write(json.dumps(res) + "\n")
                    sys.stdout.flush()

                elif tool_name == "get_project_context":
                    try:
                        q = args.get("query", "")
                        endpoint = f"/contexts?search={urllib.parse.quote(q)}" if q else "/contexts"
                        contexts = make_request(endpoint)
                        res = {
                            "jsonrpc": "2.0",
                            "id": req_id,
                            "result": {
                                "content": [{"type": "text", "text": json.dumps(contexts, indent=2)}]
                            }
                        }
                    except Exception as ex:
                        res = {
                            "jsonrpc": "2.0",
                            "id": req_id,
                            "result": {
                                "isError": True,
                                "content": [{"type": "text", "text": f"Error: {str(ex)}"}]
                            }
                        }
                    sys.stdout.write(json.dumps(res) + "\n")
                    sys.stdout.flush()

        except Exception as ex:
            err_res = {"jsonrpc": "2.0", "error": {"code": -32603, "message": str(ex)}, "id": None}
            sys.stdout.write(json.dumps(err_res) + "\n")
            sys.stdout.flush()

def run_watcher():
    os.system("cls" if os.name == "nt" else "clear")
    print("=" * 65)
    print("       ⚡ OMNICONTEXT AUTO-WATCHER & SESSION SYNC DAEMON       ")
    print("=" * 65)
    print(f"[Target Hub]    : {API_BASE}")
    print(f"[Web Dashboard] : http://localhost:4250")
    print("-" * 65 + "\n")

    watch_dirs = []

    # 1. Antigravity IDE Brain
    home = Path.home()
    agy_dir = home / ".gemini" / "antigravity-ide" / "brain"
    if agy_dir.exists():
        watch_dirs.append(("Antigravity IDE Brain", agy_dir, ".jsonl"))

    # 2. Claude Code CLI
    claude_dir = home / ".claude" / "projects"
    if claude_dir.exists():
        watch_dirs.append(("Claude Code CLI", claude_dir, ".jsonl"))

    # 3. Local Workspace drop folder (.omnicontext)
    local_drop = Path.cwd() / ".omnicontext"
    local_drop.mkdir(exist_ok=True)
    watch_dirs.append(("Local Workspace Drop (.omnicontext)", local_drop, "*"))

    print("[OmniContext Watcher] 🟢 Active Watch Locations:")
    for name, p, ext in watch_dirs:
        print(f"  ✓ {name}: {p}")

    print("\n[OmniContext Watcher] 🚀 Watching for changes (Press Ctrl+C to stop)...")
    print("[OmniContext Watcher] Tip: Drop any context notes or chat files into .omnicontext/ to sync instantly.\n")

    seen_mtimes = {}

    while True:
        try:
            for name, base_path, ext_filter in watch_dirs:
                if not base_path.exists():
                    continue

                for root, _, files in os.walk(base_path):
                    for f in files:
                        if ext_filter != "*" and not f.endswith(ext_filter):
                            continue
                        if f.startswith("."):
                            continue

                        file_path = Path(root) / f
                        try:
                            mtime = file_path.stat().st_mtime
                            prev = seen_mtimes.get(str(file_path))

                            if prev is None:
                                seen_mtimes[str(file_path)] = mtime
                                continue

                            if mtime > prev:
                                seen_mtimes[str(file_path)] = mtime
                                sync_file(file_path, name)

                        except Exception:
                            pass

            time.sleep(1.5)

        except KeyboardInterrupt:
            print("\n[OmniContext Watcher] Stopped.")
            break

def sync_file(file_path, source_type):
    try:
        size = file_path.stat().st_size
        if size == 0 or size > 10 * 1024 * 1024:
            return

        with open(file_path, "r", encoding="utf-8", errors="ignore") as fp:
            content = fp.read()

        extracted_text = ""
        title = file_path.stem.replace("_", " ").replace("-", " ").title()

        if file_path.suffix == ".jsonl":
            lines = [l.strip() for l in content.splitlines() if l.strip()]
            msgs = []
            for l in lines[-25:]:
                try:
                    obj = json.loads(l)
                    c = obj.get("content")
                    if isinstance(c, str) and len(c.strip()) > 5:
                        msgs.append(c.strip())
                except Exception:
                    pass
            extracted_text = "\n\n".join(msgs)
            title = f"Session: {file_path.parent.name}"
        else:
            extracted_text = content

        if len(extracted_text.strip()) < 30:
            return

        print(f"[OmniContext Watcher] ⚡ Detected update in {file_path.name}")
        print(f"[OmniContext Watcher] 🔄 Compressing & uploading to MongoDB...")

        saved = make_request("/contexts", method="POST", data={
            "title": title,
            "project": "AutoWatched",
            "rawContent": extracted_text,
            "tags": f"AutoWatched, {source_type}",
            "compressionStrategy": "SEMANTIC_DENSE"
        })

        print(f"[OmniContext Watcher] ✅ Successfully synced capsule!")
        print(f"   • Title: \"{saved['title']}\"")
        print(f"   • Tokens: {saved['originalTokens']} ➔ {saved['compressedTokens']} ({saved['compressionRatio']}% Saved)")
        print(f"   • Share Link: http://localhost:4250/c/{saved['shareSlug']}\n")

    except Exception as e:
        print(f"[OmniContext Watcher] ❌ Sync error: {str(e)}")

if __name__ == "__main__":
    if IS_MCP_MODE:
        handle_mcp()
    else:
        run_watcher()
