document.addEventListener('DOMContentLoaded', async () => {
  const statusPill = document.getElementById('status-pill');
  const statusText = document.getElementById('status-text');
  const projectInput = document.getElementById('project-input');
  const syncBtn = document.getElementById('sync-current-btn');
  const resultMsg = document.getElementById('result-message');

  // Load saved project preference
  if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
    chrome.storage.local.get(['activeProject'], (data) => {
      if (data.activeProject) projectInput.value = data.activeProject;
    });

    projectInput.addEventListener('input', () => {
      chrome.storage.local.set({ activeProject: projectInput.value.trim() });
    });
  }

  // Check Spring Boot & MongoDB connection via background service worker
  chrome.runtime.sendMessage({ action: 'CHECK_BACKEND_HEALTH' }, (res) => {
    if (res && res.ok) {
      statusPill.className = 'status-pill online';
      statusText.innerText = 'Connected (8085)';
    } else {
      statusPill.className = 'status-pill offline';
      statusText.innerText = 'Backend Offline';
    }
  });

  // Trigger sync on active tab
  syncBtn.addEventListener('click', async () => {
    syncBtn.innerText = '⏳ Syncing Context...';
    syncBtn.disabled = true;
    resultMsg.style.display = 'none';

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab || !tab.id) {
        throw new Error('No active browser tab found.');
      }

      const url = tab.url || '';
      const isSupportedAiSite = 
        url.includes('chatgpt.com') ||
        url.includes('openai.com') ||
        url.includes('claude.ai') ||
        url.includes('gemini.google.com');

      if (!isSupportedAiSite) {
        throw new Error('Please switch to an active ChatGPT, Claude, or Gemini tab to sync.');
      }

      // Save project input before triggering sync
      if (chrome.storage && chrome.storage.local) {
        await chrome.storage.local.set({ activeProject: projectInput.value.trim() || 'WebChat' });
      }

      // Send message to content script and await the result
      chrome.tabs.sendMessage(tab.id, { action: 'SYNC_CONTEXT' }, (response) => {
        syncBtn.innerText = '⚡ Sync Current AI Chat Tab';
        syncBtn.disabled = false;

        if (chrome.runtime.lastError) {
          resultMsg.className = 'result-message error';
          resultMsg.innerText = 'Please refresh the AI chat page once so the extension script is active.';
          resultMsg.style.display = 'block';
          return;
        }

        if (response && response.ok && response.data) {
          const saved = response.data;
          resultMsg.className = 'result-message success';
          resultMsg.innerHTML = `
            <div><strong>✅ Synced to OmniContext!</strong></div>
            <div style="margin-top: 4px; font-size: 11px;">Saved ${saved.compressionRatio}% tokens (${saved.compressedTokens} tokens)</div>
            <div style="margin-top: 6px;">
              <a href="http://localhost:4250/c/${saved.shareSlug}" target="_blank" style="color: #38bdf8; text-decoration: underline; font-weight: 600;">
                Open Capsule in Dashboard ➔
              </a>
            </div>
          `;
          resultMsg.style.display = 'block';
        } else {
          resultMsg.className = 'result-message error';
          resultMsg.innerText = response?.error || 'Failed to sync conversation.';
          resultMsg.style.display = 'block';
        }
      });

    } catch (err) {
      syncBtn.innerText = '⚡ Sync Current AI Chat Tab';
      syncBtn.disabled = false;
      resultMsg.className = 'result-message error';
      resultMsg.innerText = err.message || 'Make sure you are on chatgpt.com, claude.ai, or gemini.google.com.';
      resultMsg.style.display = 'block';
    }
  });
});
