const $ = id => document.getElementById(id);
const keys = ["bridge", "token", "urlMatch"];

chrome.storage.local.get(keys, data => {
  keys.forEach(k => {
    if (data[k]) $(k).value = data[k];
  });
});

function getSettings() {
  return Object.fromEntries(keys.map(k => [k, $(k).value.trim()]));
}

$("save").addEventListener("click", async () => {
  await chrome.storage.local.set(getSettings());
  $("status").textContent = "Settings saved.";
});

$("start").addEventListener("click", async () => {
  await chrome.storage.local.set(getSettings());
  await chrome.runtime.sendMessage({ type: "START" });
  $("status").textContent =
    "Polling started (1-second interval). Only the active sheet or exact URL target will be used; there is no fallback.";
});

$("stop").addEventListener("click", async () => {
  await chrome.runtime.sendMessage({ type: "STOP" });
  $("status").textContent = "Polling stopped.";
});

chrome.runtime.sendMessage({ type: "STATUS" }).then(s => {
  if (s?.running) $("status").textContent = "Polling is running (1-second interval). No fallback: only the active sheet or exact URL target is used.";
}).catch(() => {});
