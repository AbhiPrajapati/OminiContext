#!/usr/bin/env node

/**
 * OmniContext Universal Watcher & MCP Daemon
 * 
 * Capabilities:
 * 1. Background Watcher: Monitors CLI and IDE chat session logs (Claude Code, Antigravity, Cursor)
 *    and automatically syncs distilled context to OmniContext (MongoDB via Spring Boot).
 * 2. Model Context Protocol (MCP): Runs as an MCP server over stdio for Cursor, Claude Desktop,
 *    and Antigravity IDE so LLMs can automatically invoke save/get tools.
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const http = require('http');
const readline = require('readline');

const API_BASE = process.env.OMNICONTEXT_API || 'http://localhost:8085/api';
const isMcpMode = process.argv.includes('--mcp');

// -------------------------------------------------------------
// HTTP Client Helper
// -------------------------------------------------------------
function makeRequest(endpoint, method = 'GET', data = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(`${API_BASE}${endpoint}`);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
      }
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(body ? JSON.parse(body) : null);
          } else {
            reject(new Error(`HTTP ${res.statusCode}: ${body}`));
          }
        } catch (e) {
          resolve(body);
        }
      });
    });

    req.on('error', reject);

    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

// -------------------------------------------------------------
// MCP Server Mode (JSON-RPC 2.0 over Stdio)
// -------------------------------------------------------------
function runMcpServer() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
  });

  rl.on('line', async (line) => {
    if (!line.trim()) return;

    try {
      const request = JSON.parse(line);
      const response = await handleMcpRequest(request);
      if (response) {
        process.stdout.write(JSON.stringify(response) + '\n');
      }
    } catch (err) {
      const errResponse = {
        jsonrpc: '2.0',
        error: { code: -32603, message: err.message },
        id: null
      };
      process.stdout.write(JSON.stringify(errResponse) + '\n');
    }
  });

  process.stderr.write('[OmniContext MCP] Server running on stdio.\n');
}

async function handleMcpRequest(req) {
  const { id, method, params } = req;

  if (method === 'initialize') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: { name: 'omnicontext-mcp', version: '1.0.0' }
      }
    };
  }

  if (method === 'notifications/initialized') {
    return null;
  }

  if (method === 'tools/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools: [
          {
            name: 'save_context_to_hub',
            description: 'Save or update the current project context, decisions, architecture, and tasks to OmniContext Hub.',
            inputSchema: {
              type: 'object',
              properties: {
                title: { type: 'string', description: 'Brief title for this context capsule' },
                project: { type: 'string', description: 'Project workspace name' },
                rawContent: { type: 'string', description: 'Decisions, state, rules, code signatures, and active tasks' },
                tags: { type: 'string', description: 'Comma-separated tags (e.g. "Spring Boot, Angular, MongoDB")' }
              },
              required: ['title', 'rawContent']
            }
          },
          {
            name: 'get_project_context',
            description: 'Retrieve stored context capsules and ground-truth memory for a project or keyword.',
            inputSchema: {
              type: 'object',
              properties: {
                query: { type: 'string', description: 'Project name or search keyword' }
              }
            }
          }
        ]
      }
    };
  }

  if (method === 'tools/call') {
    const { name, arguments: args } = params;

    if (name === 'save_context_to_hub') {
      try {
        const saved = await makeRequest('/contexts', 'POST', {
          title: args.title,
          project: args.project || 'General',
          rawContent: args.rawContent,
          tags: args.tags || '',
          compressionStrategy: 'SEMANTIC_DENSE'
        });

        return {
          jsonrpc: '2.0',
          id,
          result: {
            content: [
              {
                type: 'text',
                text: `Successfully synced context to OmniContext Hub!\nID: ${saved.id}\nSlug: ${saved.shareSlug}\nTokens: ${saved.originalTokens} -> ${saved.compressedTokens} (${saved.compressionRatio}% saved)\nShare URL: http://localhost:4250/c/${saved.shareSlug}`
              }
            ]
          }
        };
      } catch (e) {
        return {
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: `Failed to save context: ${e.message}` }]
          }
        };
      }
    }

    if (name === 'get_project_context') {
      try {
        const queryParam = args?.query ? `?search=${encodeURIComponent(args.query)}` : '';
        const list = await makeRequest(`/contexts${queryParam}`);
        return {
          jsonrpc: '2.0',
          id,
          result: {
            content: [{ type: 'text', text: JSON.stringify(list, null, 2) }]
          }
        };
      } catch (e) {
        return {
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: `Failed to fetch context: ${e.message}` }]
          }
        };
      }
    }
  }

  return {
    jsonrpc: '2.0',
    id,
    error: { code: -32601, message: `Method not found: ${method}` }
  };
}

// -------------------------------------------------------------
// Auto-Watcher Daemon Mode
// -------------------------------------------------------------
function runWatcherDaemon() {
  console.clear();
  console.log('===============================================================');
  console.log('       ⚡ OMNICONTEXT AUTO-WATCHER & SESSION SYNC DAEMON       ');
  console.log('===============================================================');
  console.log(`[Target Hub]    : ${API_BASE}`);
  console.log(`[Web Dashboard] : http://localhost:4250`);
  console.log('---------------------------------------------------------------\n');

  // Paths to monitor
  const watchPaths = [];

  // 1. Antigravity IDE transcripts in AppData
  const agyBase = path.join(os.homedir(), '.gemini', 'antigravity-ide', 'brain');
  if (fs.existsSync(agyBase)) {
    watchPaths.push({ name: 'Antigravity IDE Brain', dir: agyBase, recursive: true, type: 'AGY' });
  }

  // 2. Claude Code CLI projects
  const claudeBase = path.join(os.homedir(), '.claude', 'projects');
  if (fs.existsSync(claudeBase)) {
    watchPaths.push({ name: 'Claude Code CLI', dir: claudeBase, recursive: true, type: 'CLAUDE' });
  }

  // 3. Local Workspace drop files
  const localDrop = path.join(process.cwd(), '.omnicontext');
  if (!fs.existsSync(localDrop)) {
    try { fs.mkdirSync(localDrop, { recursive: true }); } catch (_) {}
  }
  watchPaths.push({ name: 'Local Workspace Drop (.omnicontext)', dir: localDrop, recursive: false, type: 'DROP' });

  // Debounce map for file updates
  const debounceMap = new Map();

  console.log('[OmniContext Watcher] 🟢 Active Watch Locations:');
  for (const wp of watchPaths) {
    console.log(`  ✓ ${wp.name}: ${wp.dir}`);
    try {
      fs.watch(wp.dir, { recursive: wp.recursive }, (eventType, filename) => {
        if (!filename) return;

        // Filter relevant transcript/context files
        if (filename.endsWith('.jsonl') || filename.endsWith('.md') || filename.endsWith('.txt')) {
          const key = path.join(wp.dir, filename);
          clearTimeout(debounceMap.get(key));
          debounceMap.set(key, setTimeout(() => {
            handleFileChange(key, wp.type);
          }, 1500));
        }
      });
    } catch (e) {
      console.warn(`  ⚠ Could not attach watcher to ${wp.dir}: ${e.message}`);
    }
  }

  console.log('\n[OmniContext Watcher] 🚀 Watching for AI conversation turns and decisions...');
  console.log('[OmniContext Watcher] Tip: Drop any notes or session exports into .omnicontext/ to sync instantly.\n');
}

async function handleFileChange(filePath, type) {
  if (!fs.existsSync(filePath)) return;

  try {
    const stats = fs.statSync(filePath);
    if (stats.size === 0 || stats.size > 10 * 1024 * 1024) return; // ignore empty or >10MB files

    const content = fs.readFileSync(filePath, 'utf8');
    let title = path.basename(filePath);
    let extractedText = '';

    if (filePath.endsWith('.jsonl')) {
      // Parse JSON Lines transcript
      const lines = content.split('\n').filter(l => l.trim());
      const messages = [];
      for (const line of lines.slice(-20)) { // take last 20 events
        try {
          const obj = JSON.parse(line);
          if (obj.content && typeof obj.content === 'string') {
            messages.push(obj.content);
          }
        } catch (_) {}
      }
      extractedText = messages.join('\n\n');
      title = `Session: ${path.basename(path.dirname(filePath))}`;
    } else {
      extractedText = content;
    }

    if (!extractedText.trim() || extractedText.length < 30) return;

    console.log(`[OmniContext Watcher] ⚡ Detected update in ${path.basename(filePath)}`);
    console.log(`[OmniContext Watcher] 🔄 Compressing & uploading to MongoDB...`);

    const saved = await makeRequest('/contexts', 'POST', {
      title: title.replace(/[-_]/g, ' '),
      project: 'AutoWatched',
      rawContent: extractedText,
      tags: `AutoWatched, ${type}`,
      compressionStrategy: 'SEMANTIC_DENSE'
    });

    console.log(`[OmniContext Watcher] ✅ Successfully synced capsule!`);
    console.log(`   • Title: "${saved.title}"`);
    console.log(`   • Tokens: ${saved.originalTokens} ➔ ${saved.compressedTokens} (${saved.compressionRatio}% Saved)`);
    console.log(`   • Share Link: http://localhost:4250/c/${saved.shareSlug}\n`);

  } catch (err) {
    console.error(`[OmniContext Watcher] ❌ Sync error: ${err.message}`);
  }
}

// -------------------------------------------------------------
// Entry Point Dispatcher
// -------------------------------------------------------------
if (isMcpMode) {
  runMcpServer();
} else {
  runWatcherDaemon();
}
