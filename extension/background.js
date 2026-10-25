const DEFAULTS = {
  bridge: "http://192.168.1.25:8765",
  token: "change-this-dev-token",
  urlMatch: "docs.google.com/spreadsheets"
};
let running = false;
let busy = false;
let lastProcessedAt = 0;

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === "START") {
    running = true;
    chrome.alarms.create("poll-id", {periodInMinutes: 0.5});
    poll();
    sendResponse({ok:true});
  } else if (msg.type === "STOP") {
    running = false;
    chrome.alarms.clear("poll-id");
    sendResponse({ok:true});
  } else if (msg.type === "STATUS") {
    sendResponse({running});
  }
  return true;
});
chrome.alarms.onAlarm.addListener(a => { if (a.name === "poll-id" && running) poll(); });

async function settings() {
  return {...DEFAULTS, ...(await chrome.storage.local.get(Object.keys(DEFAULTS)))};
}
async function poll() {
  if (!running || busy) return;
  busy = true;
  try {
    const s = await settings();
    const res = await fetch(s.bridge.replace(/\/$/,"") + "/latest", {
      headers: {Authorization: "Bearer " + s.token}
    });
    if (!res.ok) throw new Error("Bridge returned HTTP " + res.status);
    const item = await res.json();
    if (item.id && item.created_at > lastProcessedAt) {
      lastProcessedAt = item.created_at;
      const result = await searchInSheets(item.id, s.urlMatch);
      await fetch(s.bridge.replace(/\/$/,"") + "/ack", {
        method: "POST", headers: {Authorization: "Bearer " + s.token}
      }).catch(()=>{});
      chrome.notifications.create({
        type: "basic", iconUrl: "icon.png", title: "Screen to Ctrl+F",
        message: result
      });
    }
  } catch (e) {
    console.error(e);
  } finally {
    busy = false;
  }
}
async function searchInSheets(id, urlMatch) {
  const tabs = await chrome.tabs.query({});
  const tab = tabs.find(t => t.url && t.url.includes(urlMatch));
  if (!tab || !tab.id) return "ID detected: " + id + ". No matching Google Sheets tab is open.";
  await chrome.tabs.update(tab.id, {active:true});
  if (tab.windowId !== undefined) await chrome.windows.update(tab.windowId, {focused:true});

  let attached = false;
  currentTabId = tab.id;
  try {
    await chrome.debugger.attach({tabId: tab.id}, "1.3");
    attached = true;
    await delay(350);
    await pressCtrlF();
    await delay(150);
    await typeText(id);
    await key("Enter", "Enter");
    await delay(250);
    return "Searched Google Sheets for: " + id;
  } catch (e) {
    return "ID detected (" + id + "), but Ctrl+F failed: " + e.message +
      ". Check the extension's debugger permission and Chrome's permission prompt.";
  } finally {
    if (attached) {
      try { await chrome.debugger.detach({tabId:tab.id}); } catch (_) {}
    }
  }
}
function delay(ms) { return new Promise(r => setTimeout(r, ms)); }
async function pressCtrlF() {
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyDown", key:"Control", code:"ControlLeft", modifiers:2
  });
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyDown", key:"f", code:"KeyF", modifiers:2
  });
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyUp", key:"f", code:"KeyF", modifiers:2
  });
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyUp", key:"Control", code:"ControlLeft"
  });
}
async function key(key, code) {
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyDown", key, code
  });
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.dispatchKeyEvent", {
    type:"keyUp", key, code
  });
}
let currentTabId = null;
async function typeText(text) {
  await chrome.debugger.sendCommand({tabId: currentTabId}, "Input.insertText", {text});
}
