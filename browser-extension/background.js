/**
 * OmniContext Background Service Worker (Manifest V3)
 * Handles privileged HTTP requests to localhost:8085, bypassing webpage CSP and Mixed Content blocks.
 */

const API_ENDPOINT = 'http://localhost:8085/api/contexts';
const API_STATS = 'http://localhost:8085/api/stats';

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'SAVE_CONTEXT_API') {
    saveContextToBackend(request.payload)
      .then(data => sendResponse({ ok: true, data }))
      .catch(err => sendResponse({ ok: false, error: err.message }));
    return true; // Keep message port open for async response
  }

  if (request.action === 'CHECK_BACKEND_HEALTH') {
    fetch(API_STATS)
      .then(res => res.json())
      .then(data => sendResponse({ ok: true, data }))
      .catch(err => sendResponse({ ok: false, error: err.message }));
    return true;
  }
});

async function saveContextToBackend(payload) {
  const response = await fetch(API_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`HTTP ${response.status}: ${errorText || 'Server error'}`);
  }

  return await response.json();
}
