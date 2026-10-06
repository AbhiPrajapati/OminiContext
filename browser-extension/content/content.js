/**
 * OmniContext Content Script
 * Injected on ChatGPT, Claude.ai, and Gemini to allow 1-click context sync.
 * Features:
 * - Dual-pipeline network transport (Background service worker + Direct Fetch fallback)
 * - Failsafe 5s timeout & guaranteed button reset
 * - Handles Extension Context Invalidation (prompts tab reload)
 * - Robust scraping across modern ChatGPT, Claude, and Gemini layouts
 */

(function () {
  const DASHBOARD_URL = 'http://localhost:4250';
  const API_ENDPOINT = 'http://localhost:8085/api/contexts';

  const SYSTEM_DISCLAIMERS = [
    'log in to get answers',
    'log in to use',
    'create images and upload files',
    'terms & privacy policy',
    'terms of use',
    'chats may be reviewed',
    'you’ll get smarter responses',
    'chatgpt is ai',
    'upgrade your plan',
    'sign up to chat',
    'how can i help you today'
  ];

  function isSystemDisclaimer(text) {
    if (!text || text.length < 3) return true;
    const lower = text.toLowerCase().trim();
    if (lower === 'log in' || lower === 'sign up' || lower === 'sign up for free' || lower === 'ask chatgpt') return true;
    if (lower.startsWith('log in for a more personalized')) return true;
    if (lower.startsWith('log in to get answers')) return true;
    if (lower.startsWith('log in to use')) return true;
    if (lower.startsWith('how can i help you today')) return true;
    if (lower.includes('terms & privacy policy') && lower.length < 160) return true;
    if (lower.includes('chatgpt can make mistakes') && lower.length < 120) return true;
    if (lower.includes('chats may be reviewed and used to improve') && lower.length < 200) return true;
    if (lower.includes('create images and upload files') && lower.length < 120) return true;
    if (lower.includes('you’ll get smarter responses') && lower.length < 120) return true;
    return false;
  }

  // Inject Floating Sync Button
  function injectSyncButton() {
    if (document.getElementById('omnicontext-sync-btn')) return;

    const btn = document.createElement('button');
    btn.id = 'omnicontext-sync-btn';
    btn.innerHTML = '<span class="icon">⚡</span><span>Sync to OmniContext</span>';
    btn.title = 'Compress and save this AI conversation to OmniContext Hub';

    btn.addEventListener('click', () => handleSyncClick());
    document.body.appendChild(btn);
  }

  // Robust chat message scraper
  function scrapeConversation() {
    const host = window.location.hostname;
    let title = '';
    let turns = [];
    const root = document.querySelector('main') || document.body;

    if (host.includes('chatgpt.com') || host.includes('openai.com')) {
      // 1. Check for modern ChatGPT articles or conversation-turn containers
      const turnContainers = root.querySelectorAll('article, [data-testid^="conversation-turn"], [class*="conversation-turn"]');
      if (turnContainers.length > 0) {
        turnContainers.forEach(container => {
          if (container.closest('#omnicontext-sync-btn, #omnicontext-toast, header, nav, form')) return;

          const roleEl = container.querySelector('[data-message-author-role]');
          let role = roleEl ? roleEl.getAttribute('data-message-author-role') : null;
          const markdownEl = container.querySelector('.markdown');
          const userBubbleEl = container.querySelector('div[dir="auto"], .whitespace-pre-wrap');

          let text = '';
          if (markdownEl) {
            text = markdownEl.innerText.trim();
            role = 'assistant';
          } else if (userBubbleEl && !userBubbleEl.closest('#prompt-textarea, form, [contenteditable="true"]')) {
            text = userBubbleEl.innerText.trim();
            role = 'user';
          } else {
            const clone = container.cloneNode(true);
            clone.querySelectorAll('button, svg, nav, form, [contenteditable="true"], #prompt-textarea').forEach(n => n.remove());
            text = clone.innerText.trim();
            if (!role) {
              role = (text.includes('ChatGPT') || container.querySelector('button[aria-label*="Copy"]')) ? 'assistant' : 'user';
            }
          }

          if (text && !isSystemDisclaimer(text) && text.length > 2) {
            turns.push(`${(role || 'MESSAGE').toUpperCase()}:\n${text}`);
          }
        });
      }

      // 2. Check for data-message-author-role elements directly
      if (turns.length === 0) {
        const messageEls = root.querySelectorAll('[data-message-author-role]');
        if (messageEls.length > 0) {
          messageEls.forEach(el => {
            const role = el.getAttribute('data-message-author-role') || 'message';
            const text = el.innerText.trim();
            if (text && !isSystemDisclaimer(text) && text.length > 2) {
              turns.push(`${role.toUpperCase()}:\n${text}`);
            }
          });
        }
      }

      // 3. Guest Mode / Anonymous Chat DOM elements (.markdown for assistant, div[dir="auto"] or .whitespace-pre-wrap for user)
      if (turns.length === 0) {
        const candidates = root.querySelectorAll('.markdown, div[dir="auto"], .whitespace-pre-wrap, [class*="userMessage"], [class*="agentMessage"]');
        
        candidates.forEach(el => {
          if (el.id === 'prompt-textarea' || el.closest('#prompt-textarea, form, [contenteditable="true"], header, nav, #omnicontext-sync-btn, #omnicontext-toast')) {
            return;
          }
          // If this element is inside a .markdown container but is not the outer container itself, skip it
          if (el.closest('.markdown') && !el.classList.contains('markdown')) {
            return;
          }

          const isAssistant = el.classList.contains('markdown') || !!el.closest('.markdown');
          const role = isAssistant ? 'ASSISTANT' : 'USER';
          const text = el.innerText.trim();

          if (text && !isSystemDisclaimer(text) && text.length > 2) {
            const already = turns.some(t => t.includes(text) || text.includes(t.replace(/^(USER|ASSISTANT|MESSAGE):\s*\n?/i, '')));
            if (!already) {
              turns.push(`${role}:\n${text}`);
            }
          }
        });
      }

      // 4. Fallback: Parse paragraphs & blockquotes in main
      if (turns.length === 0) {
        const blocks = root.querySelectorAll('blockquote, p, pre, div[class*="bubble"]');
        let currentRole = 'USER';
        blocks.forEach(b => {
          if (b.closest('#prompt-textarea, form, [contenteditable="true"], header, nav, button, #omnicontext-sync-btn, #omnicontext-toast')) return;
          const text = b.innerText.trim();
          if (text && !isSystemDisclaimer(text) && text.length > 8) {
            const already = turns.some(t => t.includes(text));
            if (!already) {
              turns.push(`${currentRole}:\n${text}`);
              currentRole = currentRole === 'USER' ? 'ASSISTANT' : 'USER';
            }
          }
        });
      }

      // Active title
      const activeNavTitle = document.querySelector('nav a[aria-current="page"], nav [class*="active"]');
      if (activeNavTitle && activeNavTitle.innerText.trim()) {
        title = activeNavTitle.innerText.trim();
      }

    } else if (host.includes('claude.ai')) {
      const allElements = document.querySelectorAll(
        '.font-user-message, .font-claude-message, [data-is-streaming="false"], [data-testid$="-message"], [data-testid="user-message"], div[class*="UserMessage"], div[class*="AssistantMessage"], div.prose'
      );
      allElements.forEach(el => {
        if (el.closest('header, nav, #omnicontext-sync-btn, #omnicontext-toast')) return;
        const text = el.innerText.trim();
        if (text && !isSystemDisclaimer(text) && text.length > 2) {
          const isUser = el.classList.contains('font-user-message') || el.closest('[data-testid="user-message"]') || el.classList.contains('UserMessage');
          turns.push(`${isUser ? 'USER' : 'ASSISTANT'}:\n${text}`);
        }
      });

    } else if (host.includes('gemini.google.com')) {
      const messageEls = document.querySelectorAll(
        '.query-text, .response-text, message-content, .model-response-text, [data-test-id="user-query"], [data-test-id="model-response"], user-query, model-response'
      );
      messageEls.forEach(el => {
        if (el.closest('header, nav, #omnicontext-sync-btn, #omnicontext-toast')) return;
        const text = el.innerText.trim();
        if (text && !isSystemDisclaimer(text) && text.length > 2) {
          const isUser = el.classList.contains('query-text') || el.tagName.toLowerCase() === 'user-query' || el.getAttribute('data-test-id') === 'user-query';
          turns.push(`${isUser ? 'USER' : 'ASSISTANT'}:\n${text}`);
        }
      });
    }

    // Determine smart title
    if (!title) {
      if (turns.length > 0) {
        const firstTurn = turns[0].replace(/^(USER|ASSISTANT|MESSAGE):\s*/i, '').trim();
        title = firstTurn.length > 55 ? firstTurn.substring(0, 55) + '...' : firstTurn;
      } else {
        title = document.title
          .replace(/(ChatGPT|Claude|Gemini|Google AI)/gi, '')
          .replace(/^[:\s|•\-_]+/, '')
          .trim();
      }
    }

    if (!title || title.length < 3) {
      title = 'AI Session Context';
    }

    console.log('[OmniContext v1.2] Scraped turns:', turns.length, turns);

    return {
      title,
      turns,
      rawContent: turns.join('\n\n---\n\n'),
      source: host
    };
  }

  async function handleSyncClick() {
    const btn = document.getElementById('omnicontext-sync-btn');
    if (btn) {
      btn.innerHTML = '<span class="icon">⏳</span><span>Compressing...</span>';
      btn.style.opacity = '0.7';
      btn.disabled = true;
    }

    try {
      const data = scrapeConversation();

      // Desperation Fallback: If 0 turns returned, extract all meaningful dialogue text from main
      if (!data.turns || data.turns.length === 0) {
        console.warn('[OmniContext v1.2] No turns from standard scraper, executing visual text extraction...');
        const main = document.querySelector('main') || document.body;
        const fullText = main.innerText || '';
        const lines = fullText.split(/\n{2,}/)
          .map(l => l.trim())
          .filter(l => l.length > 15 && !isSystemDisclaimer(l));

        if (lines.length > 0) {
          data.turns = lines.map((l, i) => `${i % 2 === 0 ? 'USER' : 'ASSISTANT'}:\n${l}`);
          data.rawContent = data.turns.join('\n\n---\n\n');
          data.title = lines[0].substring(0, 50);
        }
      }

      // Guard: Must have at least one message turn
      if (!data.turns || data.turns.length === 0) {
        showToast('⚠️ No chat messages found (OmniContext v1.2). Please reload this tab with F5!', false);
        return { ok: false, error: 'No messages found' };
      }

      let project = 'WebChat';
      try {
        if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
          const stored = await chrome.storage.local.get(['activeProject']);
          if (stored.activeProject) project = stored.activeProject;
        }
      } catch (_) {}

      const payload = {
        title: data.title,
        project: project,
        rawContent: data.rawContent,
        tags: `WebChat, ${data.source.split('.')[0]}`,
        compressionStrategy: 'SEMANTIC_DENSE'
      };

      // Send payload with background service worker + direct fetch fallback
      const result = await sendWithFailsafe(payload);

      if (result && result.ok && result.data) {
        const saved = result.data;
        showToast(
          `✅ Synced to OmniContext! (${saved.compressedTokens} tokens | -${saved.compressionRatio}% saved)`,
          true,
          saved.shareSlug
        );
        return { ok: true, data: saved };
      } else {
        const errMsg = result?.error || 'Could not reach backend on port 8085';
        showToast(`❌ Sync error: ${errMsg}`, false);
        return { ok: false, error: errMsg };
      }

    } catch (err) {
      if (err.message && err.message.toLowerCase().includes('context invalidated')) {
        showToast('⚠️ Extension was updated! Please refresh (F5) this ChatGPT tab.', false);
      } else {
        showToast(`❌ Sync error: ${err.message}`, false);
      }
      return { ok: false, error: err.message };
    } finally {
      resetButton();
    }
  }

  // Failsafe transport: tries background worker with 3s timeout, falls back to direct fetch
  function sendWithFailsafe(payload) {
    return new Promise((resolve) => {
      let resolved = false;

      const timer = setTimeout(() => {
        if (!resolved) {
          resolved = true;
          directFetch(payload).then(resolve);
        }
      }, 3500);

      // Try Chrome extension messaging
      try {
        if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
          chrome.runtime.sendMessage({ action: 'SAVE_CONTEXT_API', payload }, (response) => {
            if (chrome.runtime.lastError) {
              if (!resolved) {
                resolved = true;
                clearTimeout(timer);
                directFetch(payload).then(resolve);
              }
              return;
            }

            if (!resolved) {
              resolved = true;
              clearTimeout(timer);
              resolve(response || { ok: false, error: 'Empty response' });
            }
          });
          return;
        }
      } catch (_) {}

      // Fallback if chrome.runtime fails
      if (!resolved) {
        resolved = true;
        clearTimeout(timer);
        directFetch(payload).then(resolve);
      }
    });
  }

  // Direct HTTP Fetch to backend
  async function directFetch(payload) {
    try {
      const res = await fetch(API_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const txt = await res.text();
        throw new Error(`HTTP ${res.status}: ${txt}`);
      }
      const data = await res.json();
      return { ok: true, data };
    } catch (e) {
      return { ok: false, error: e.message || 'Cannot reach http://localhost:8085' };
    }
  }

  function resetButton() {
    const btn = document.getElementById('omnicontext-sync-btn');
    if (btn) {
      btn.innerHTML = '<span class="icon">⚡</span><span>Sync to OmniContext</span>';
      btn.style.opacity = '1';
      btn.disabled = false;
    }
  }

  function showToast(message, isSuccess = true, slug = null) {
    const old = document.getElementById('omnicontext-toast');
    if (old) old.remove();

    const toast = document.createElement('div');
    toast.id = 'omnicontext-toast';
    let html = `<div class="toast-title" style="color: ${isSuccess ? '#34d399' : '#f87171'}">${message}</div>`;
    if (slug) {
      html += `<a href="${DASHBOARD_URL}/c/${slug}" target="_blank" class="toast-link">View in OmniContext Hub ➔</a>`;
    }
    toast.innerHTML = html;
    document.body.appendChild(toast);

    setTimeout(() => {
      if (toast) toast.remove();
    }, 6000);
  }

  // Listen for sync trigger messages from popup
  if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.onMessage) {
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
      if (request.action === 'SYNC_CONTEXT') {
        handleSyncClick().then(res => sendResponse(res));
        return true;
      }
    });
  }

  // Run on load and observe dynamically loaded single-page chats
  injectSyncButton();
  const observer = new MutationObserver(() => injectSyncButton());
  observer.observe(document.body, { childList: true, subtree: true });
})();
