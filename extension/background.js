const DEFAULTS = {
  bridge: "http://192.168.1.25:8765",
  token: "change-this-dev-token",
  // Enter a full Google Sheets URL to target that sheet, or leave the default
  // to use the active Google Sheets tab first.
  urlMatch: "docs.google.com/spreadsheets"
};

let running = false;
let busy = false;
let lastProcessedAt = 0;
let currentTabId = null;
let pollTimer = null;
const POLL_INTERVAL_MS = 1000;

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === "START") {
    running = true;
    schedulePoll(0);
    sendResponse({ ok: true });
  } else if (msg.type === "STOP") {
    running = false;
    if (pollTimer !== null) clearTimeout(pollTimer);
    pollTimer = null;
    sendResponse({ ok: true });
  } else if (msg.type === "STATUS") {
    sendResponse({ running });
  }
  return true;
});


async function settings() {
  return { ...DEFAULTS, ...(await chrome.storage.local.get(Object.keys(DEFAULTS))) };
}

function schedulePoll(delayMs = POLL_INTERVAL_MS) {
  if (!running) return;
  if (pollTimer !== null) clearTimeout(pollTimer);
  pollTimer = setTimeout(() => {
    pollTimer = null;
    poll();
  }, delayMs);
}

async function poll() {
  if (!running || busy) { schedulePoll(); return; }
  busy = true;

  try {
    const s = await settings();
    const res = await fetch(s.bridge.replace(/\/$/, "") + "/latest", {
      headers: { Authorization: "Bearer " + s.token },
      cache: "no-store"
    });

    if (!res.ok) throw new Error("Bridge returned HTTP " + res.status);

    const item = await res.json();

    if (item.id && item.created_at > lastProcessedAt) {
      lastProcessedAt = item.created_at;

      const result = await searchInSheets(item.id, s.urlMatch);

      await fetch(s.bridge.replace(/\/$/, "") + "/ack", {
        method: "POST",
        headers: { Authorization: "Bearer " + s.token },
        cache: "no-store"
      }).catch(() => {});

      chrome.notifications.create({
        type: "basic",
        // Use a built-in extension icon path if icon.png is added to the folder.
        iconUrl: chrome.runtime.getURL("icon.png"),
        title: "Screen to Ctrl+F",
        message: result
      });
    }
  } catch (e) {
    console.error("Screen to Ctrl+F polling error:", e);
  } finally {
    busy = false;
    schedulePoll(POLL_INTERVAL_MS);
  }
}

function isSheetsTab(tab) {
  return Boolean(tab?.url && /^https:\/\/docs\.google\.com\/spreadsheets\//i.test(tab.url));
}

function normalizeUrl(url) {
  try {
    const parsed = new URL(url);
    parsed.hash = "";
    return parsed.href.replace(/\/$/, "");
  } catch (_) {
    return url.trim().replace(/\/$/, "");
  }
}

async function chooseSheetsTab(urlMatch) {
  const matchText = (urlMatch || "").trim();
  const looksLikeFullUrl = /^https?:\/\//i.test(matchText);

  if (looksLikeFullUrl) {
    const target = normalizeUrl(matchText);
    const tabs = await chrome.tabs.query({ url: "https://docs.google.com/spreadsheets/*" });
    return tabs.find(t => isSheetsTab(t) && normalizeUrl(t.url) === target) || null;
  }

  // No fallback: only use the active Google Sheets tab in the focused window.
  const activeTabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  const active = activeTabs.find(isSheetsTab);
  if (!active) return null;
  return !matchText || active.url.includes(matchText) ? active : null;
}

async function searchInSheets(id, urlMatch) {
  const tab = await chooseSheetsTab(urlMatch);

  if (!tab?.id) {
    return "ID detected: " + id +
      ". No target sheet matched. Activate the intended sheet, or enter its exact URL in the extension settings.";
  }

  try {
    await chrome.tabs.update(tab.id, { active: true });
    if (tab.windowId !== undefined) {
      await chrome.windows.update(tab.windowId, { focused: true });
    }
  } catch (e) {
    return "ID detected (" + id + "), but Chrome could not focus the target sheet: " + e.message;
  }

  let attached = false;
  currentTabId = tab.id;

  try {
    await chrome.debugger.attach({ tabId: tab.id }, "1.3");
    attached = true;
    await delay(200);
    await pressCtrlF();
    await delay(100);
    await typeText(id);
    await key("Enter", "Enter");
    await delay(150);
    return "Searched sheet for " + id + " in: " + tab.url;
  } catch (e) {
    return "ID detected (" + id + "), but Ctrl+F failed: " + e.message +
      ". Check the extension debugger permission and Chrome's permission prompt.";
  } finally {
    if (attached) {
      try {
        await chrome.debugger.detach({ tabId: tab.id });
      } catch (_) {}
    }
    currentTabId = null;
  }
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function pressCtrlF() {
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyDown", key: "Control", code: "ControlLeft", modifiers: 2
  });
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyDown", key: "f", code: "KeyF", modifiers: 2
  });
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyUp", key: "f", code: "KeyF", modifiers: 2
  });
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyUp", key: "Control", code: "ControlLeft"
  });
}

async function key(keyName, code) {
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyDown", key: keyName, code
  });
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.dispatchKeyEvent", {
    type: "keyUp", key: keyName, code
  });
}

async function typeText(text) {
  await chrome.debugger.sendCommand({ tabId: currentTabId }, "Input.insertText", { text });
}
