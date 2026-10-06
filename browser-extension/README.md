# 🌐 OmniContext Browser Extension (Chrome / Edge / Brave)

> **1-Click AI Context Sync directly from ChatGPT, Claude.ai, and Google Gemini into OmniContext Hub with up to 85% token compression.**

---

## 🚀 How to Install (Load Unpacked in 30 Seconds)

1. Open your browser and navigate to:
   - **Chrome / Brave**: `chrome://extensions/`
   - **Edge**: `edge://extensions/`
2. Toggle on **"Developer mode"** in the top right corner.
3. Click the **"Load unpacked"** button in the top left.
4. Select this directory:
   ```
   D:\PersonalProject\browser-extension
   ```
5. Done! The **OmniContext** extension icon will appear in your browser toolbar.

---

## ⚡ How to Use

1. Open any active chat on:
   - [ChatGPT](https://chatgpt.com)
   - [Claude](https://claude.ai)
   - [Google Gemini](https://gemini.google.com)
2. You will see a glowing violet floating button in the bottom-right corner:  
   **`⚡ Sync to OmniContext`**
3. Click it anytime! The extension:
   - Scrapes the conversation history and decisions.
   - Posts directly to your local Spring Boot backend (`http://localhost:8085`).
   - Automatically compresses the context and stores it in MongoDB.
   - Shows an in-page toast notification with a link directly to your Web Dashboard capsule: `http://localhost:4250/c/:slug`.

---

## ⚙️ Extension Settings & Popup

Click the OmniContext extension icon in your toolbar to:
- Check connection status to your local Spring Boot & MongoDB backend.
- Change the target project workspace name (default: `WebChat`).
- Trigger instant sync on the current tab.
- Jump directly to your Web Dashboard (`http://localhost:4250`).
