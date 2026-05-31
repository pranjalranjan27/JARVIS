/**
 * Jarvis Frontend — Chat Controller
 * Handles: input, API calls, chat history, UI state
 */
(function () {
  "use strict";

  var SLOW_TIMEOUT_MS = 5000;

  // ── DOM References ──
  var chatInput       = document.getElementById("chat-input");
  var btnSend         = document.getElementById("btn-send");
  var chatMessages    = document.getElementById("chat-messages");
  var chatScroll      = document.getElementById("chat-scroll");
  var welcomeScreen   = document.getElementById("welcome-screen");
  var thinkingEl      = document.getElementById("thinking-indicator");
  var slowEl          = document.getElementById("slow-indicator");
  var btnNewChat      = document.getElementById("btn-new-chat");
  var btnHistory      = document.getElementById("btn-history-toggle");
  var historyList     = document.getElementById("history-list");
  var connectionStatus = document.getElementById("connection-status");
  var modeSelectorBtn  = document.getElementById("mode-selector-btn");
  var modeSelectorLabel = document.getElementById("mode-selector-label");
  var modeSelectorChevron = document.getElementById("mode-selector-chevron");
  var modeDropdown     = document.getElementById("mode-dropdown");
  var modeSelectorWrap = document.getElementById("mode-selector-wrap");

  // ── State ──
  var isProcessing = false;
  var slowTimer    = null;
  var historyOpen  = true;
  var activeRequestToken = 0;
  var currentChatId = null;
  var chatList = [];
  var currentMode = "sarcastic";

  // ── Init ──
  renderHistoryList();
  btnHistory.classList.toggle("sidebar-btn-active", historyOpen);
  updateConnectionStatus(false);
  checkConnection();
  loadChatList();
  injectDeleteModal();

  // ── Event Listeners ──
  chatInput.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  btnSend.addEventListener("click", function (e) {
    e.preventDefault(); // guard against any accidental form submission
    sendMessage();
  });

  btnNewChat.addEventListener("click", function () {
    startNewChat();
  });

  btnHistory.addEventListener("click", function () {
    historyOpen = !historyOpen;
    btnHistory.classList.toggle("sidebar-btn-active", historyOpen);
    renderHistoryList();
  });

  // Custom mode dropdown
  if (modeSelectorBtn && modeDropdown) {
    modeSelectorBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      var isOpen = modeDropdown.classList.toggle("open");
      modeSelectorChevron.classList.toggle("rotated", isOpen);
      modeSelectorBtn.classList.toggle("open", isOpen);
    });

    document.querySelectorAll(".mode-dropdown-item").forEach(function (item) {
      item.addEventListener("click", function () {
        var mode = item.getAttribute("data-mode");
        selectMode(mode);
        modeDropdown.classList.remove("open");
        modeSelectorChevron.classList.remove("rotated");
        modeSelectorBtn.classList.remove("open");
      });
    });

    // Close dropdown when clicking outside
    document.addEventListener("click", function (e) {
      if (!modeSelectorWrap.contains(e.target)) {
        modeDropdown.classList.remove("open");
        modeSelectorChevron.classList.remove("rotated");
        modeSelectorBtn.classList.remove("open");
      }
    });
  }

  function selectMode(mode) {
    currentMode = mode;
    // Update button label
    var labels = { sarcastic: "Sarcastic", companion: "Companion", focus: "Focus" };
    modeSelectorLabel.textContent = labels[mode] || mode;
    // Update active state in dropdown
    document.querySelectorAll(".mode-dropdown-item").forEach(function (el) {
      el.classList.toggle("active", el.getAttribute("data-mode") === mode);
    });
  }

  // Suggestion chips
  document.querySelectorAll(".chip[data-message]").forEach(function (chip) {
    chip.addEventListener("click", function () {
      chatInput.value = chip.getAttribute("data-message");
      sendMessage();
    });
  });

  // ════════════════════════════════════════════
  //  Core Functions
  // ════════════════════════════════════════════

  function sendMessage() {
    if (isProcessing) return;

    var userInput = chatInput.value.trim();
    if (!userInput) return;

    // Lock UI
    isProcessing = true;
    btnSend.disabled = true;
    chatInput.value = "";

    // Hide welcome, show chat
    hideWelcome();

    // Add user message
    addMessage("user", userInput);
    showThinking();
    startSlowTimer();

    // API call
    activeRequestToken += 1;
    var requestToken = activeRequestToken;



    ensureChatForMessage(userInput)
      .then(function (chatId) {
        return postToJarvis(userInput, chatId);
      })
      .then(function (responseText) {
        if (requestToken !== activeRequestToken) return;
        clearSlowTimer();
        hideThinking();
        addMessage("jarvis", responseText);
        finalizeRequest(requestToken);
        updateConnectionStatus(true);
        // ✅ DO NOT call loadChatList() here — it re-renders the sidebar
        //    and was causing the UI flicker / reset bug.
      })
      .catch(function (err) {
        if (requestToken !== activeRequestToken) return;
        console.error("[Jarvis] Fetch error:", err);
        clearSlowTimer();
        hideThinking();

        var errorMessage;
        if (err && err.message === "Server not responding") {
          errorMessage = "Server not responding";
        } else if (err && (err.message === "Invalid response format" || err.name === "SyntaxError")) {
          errorMessage = "Invalid response format";
        } else {
          errorMessage = "Failed to reach Jarvis";
        }

        addMessage("jarvis", errorMessage);
        finalizeRequest(requestToken);
        updateConnectionStatus(false);
      });
  }

  function finalizeRequest(requestToken) {
    if (requestToken !== activeRequestToken) return;
    isProcessing = false;
    btnSend.disabled = false;
    chatInput.disabled = false;
    chatInput.focus();
  }

  function resetPendingState() {
    activeRequestToken += 1;
    isProcessing = false;
    btnSend.disabled = false;
    chatInput.disabled = false;
    hideThinking();
    clearSlowTimer();
  }

  async function postToJarvis(userInput, chatId) {
    var payload = {
      message: userInput,
      chatId: chatId,
      mode: currentMode
    };



    const res = await fetch("/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      throw new Error("Server not responding");
    }

    const data = await res.json();

    if (!data.response) {
      throw new Error("Invalid response format");
    }

    return data.response;
  }

  async function createChat(title) {
    const res = await fetch("/chat/start", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ title: title })
    });

    if (!res.ok) {
      throw new Error("Server not responding");
    }

    const data = await res.json();
    if (!data.chatId) {
      throw new Error("Invalid response format");
    }

    return data.chatId;
  }

  async function fetchChatList() {
    const res = await fetch("/chat/list");
    if (!res.ok) {
      throw new Error("Server not responding");
    }

    const data = await res.json();
    if (!Array.isArray(data)) {
      throw new Error("Invalid response format");
    }

    return data;
  }

  async function fetchChatHistory(chatId) {
    const res = await fetch("/chat/history?chatId=" + encodeURIComponent(chatId));
    if (!res.ok) {
      throw new Error("Server not responding");
    }

    const data = await res.json();
    if (!Array.isArray(data)) {
      throw new Error("Invalid response format");
    }

    return data;
  }

  function ensureChatForMessage(userInput) {
    if (currentChatId) {
      return Promise.resolve(currentChatId);
    }

    // No active chat yet — create one lazily from the first message
    var title = buildChatTitle(userInput);
    return createChat(title).then(function (chatId) {
      currentChatId = chatId;
      // Update sidebar once after chat creation (not after every message)
      loadChatList();
      return chatId;
    });
  }

  function checkConnection() {
    // Use a direct fetch for the ping so chatId logic doesn't interfere
    fetch("/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message: "ping", chatId: -1 })
    })
      .then(function (res) {
        // Backend returns 200 for ping regardless of chatId
        updateConnectionStatus(res.ok || res.status === 400);
      })
      .catch(function () {
        updateConnectionStatus(false);
      });
  }

  function loadChatList() {
    fetchChatList()
      .then(function (chats) {
        chatList = chats;
        renderHistoryList();
      })
      .catch(function (err) {
        console.error("[Jarvis] Failed to load chats:", err);
      });
  }

  function buildChatTitle(text) {
    var title = (text || "").trim();
    if (title.length > 30) {
      title = title.substring(0, 30);
    }
    return title || "New chat";
  }

  // ════════════════════════════════════════════
  //  Connection Status
  // ════════════════════════════════════════════

  function updateConnectionStatus(connected) {
    if (!connectionStatus) return;
    if (connected) {
      connectionStatus.classList.remove("disconnected");
      connectionStatus.lastElementChild.textContent = "Connected";
    } else {
      connectionStatus.classList.add("disconnected");
      connectionStatus.lastElementChild.textContent = "Disconnected";
    }
  }

  // ════════════════════════════════════════════
  //  Message Rendering
  // ════════════════════════════════════════════

  function addMessage(role, text) {
    // Render
    if (role === "user") {
      renderUserMessage(text);
    } else {
      renderJarvisMessage(text);
    }

    scrollToBottom();
  }

  function renderUserMessage(text) {
    var wrap = document.createElement("div");
    wrap.className = "msg-user";

    var bubble = document.createElement("div");
    bubble.className = "msg-user-bubble";
    bubble.textContent = text;

    var time = document.createElement("span");
    time.className = "msg-user-time";
    time.textContent = formatTime();

    wrap.appendChild(bubble);
    wrap.appendChild(time);
    chatMessages.appendChild(wrap);
  }

  function renderJarvisMessage(text) {
    var wrap = document.createElement("div");
    wrap.className = "msg-jarvis";

    // Header
    var header = document.createElement("div");
    header.className = "msg-jarvis-header";

    var avatar = document.createElement("div");
    avatar.className = "msg-jarvis-avatar";
    avatar.innerHTML = '<span class="material-symbols-outlined">psychology</span>';

    var label = document.createElement("span");
    label.className = "msg-jarvis-label";
    label.textContent = "Jarvis";

    header.appendChild(avatar);
    header.appendChild(label);

    // Bubble — render with code block formatting
    var bubble = document.createElement("div");
    bubble.className = "msg-jarvis-bubble";
    bubble.innerHTML = formatMessageContent(text);

    wrap.appendChild(header);
    wrap.appendChild(bubble);
    chatMessages.appendChild(wrap);
  }

  /**
   * Safely formats a message string into HTML.
   * Handles fenced code blocks (```lang\n...\n```) and inline code (`...`).
   * All text is HTML-escaped first to prevent XSS.
   */
  function formatMessageContent(raw) {
    // Split by fenced code blocks: ```lang\ncode\n```
    var parts = [];
    var codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
    var lastIndex = 0;
    var match;

    while ((match = codeBlockRegex.exec(raw)) !== null) {
      // Text before this code block
      if (match.index > lastIndex) {
        parts.push({ type: "text", content: raw.substring(lastIndex, match.index) });
      }
      parts.push({ type: "code", lang: match[1] || "", content: match[2] });
      lastIndex = match.index + match[0].length;
    }

    // Remaining text after last code block
    if (lastIndex < raw.length) {
      parts.push({ type: "text", content: raw.substring(lastIndex) });
    }

    // If no code blocks found, just format as plain text
    if (parts.length === 0) {
      parts.push({ type: "text", content: raw });
    }

    var html = "";
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (part.type === "code") {
        var langLabel = part.lang ? '<span class="code-block-lang">' + escapeHtml(part.lang) + '</span>' : '';
        html += '<div class="code-block-wrap">' +
                  langLabel +
                  '<pre class="code-block"><code>' + escapeHtml(part.content) + '</code></pre>' +
                '</div>';
      } else {
        // Format normal text with markdown
        var textHtml = escapeHtml(part.content);

        // Inline code first (before bold/italic so backtick content is protected)
        textHtml = textHtml.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');

        // Bold: **text**
        textHtml = textHtml.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');

        // Italic: *text* (but not inside <strong> tags or ** pairs)
        textHtml = textHtml.replace(/(?<!\*)\*([^*]+?)\*(?!\*)/g, '<em>$1</em>');

        // Markdown headings: ### text, ## text, # text (at start of line)
        textHtml = textHtml.replace(/(^|<br>)#{3}\s+(.+?)(?=<br>|$)/g, '$1<span class="md-heading md-h3">$2</span>');
        textHtml = textHtml.replace(/(^|<br>)#{2}\s+(.+?)(?=<br>|$)/g, '$1<span class="md-heading md-h2">$2</span>');
        textHtml = textHtml.replace(/(^|<br>)#{1}\s+(.+?)(?=<br>|$)/g, '$1<span class="md-heading md-h1">$2</span>');

        // Numbered lists: "1. text" at start of line
        textHtml = textHtml.replace(/(^|\n)(\d+)\.\s+/g, '$1<span class="md-list-num">$2.</span> ');

        // Convert newlines to <br>
        textHtml = textHtml.replace(/\n/g, "<br>");

        html += textHtml;
      }
    }

    return html;
  }

  function escapeHtml(text) {
    return text
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function renderAllMessages(messages) {
    chatMessages.innerHTML = "";
    messages.forEach(function (m) {
      if (m.role === "user") {
        renderUserMessage(m.content);
      } else {
        renderJarvisMessage(m.content);
      }
    });
    scrollToBottom();
  }

  // ════════════════════════════════════════════
  //  Chat History
  // ════════════════════════════════════════════

  function startNewChat() {
    // Reset UI state — chat is created lazily on first message
    chatMessages.innerHTML = "";
    chatInput.value = "";
    currentChatId = null;
    // Always reset to sarcastic mode for new chats
    selectMode("sarcastic");
    showWelcome();
    resetPendingState();
    chatInput.focus();
    // Refresh history list to reflect current state
    loadChatList();
  }

  function loadChat(chatId) {
    currentChatId = chatId;
    hideWelcome();
    resetPendingState();
    fetchChatHistory(chatId)
      .then(function (messages) {
        renderAllMessages(messages);
        renderHistoryList();
        // Restore mode selector to the mode used in this chat
        if (messages.length > 0) {
          var lastMode = messages[messages.length - 1].mode || "sarcastic";
          selectMode(lastMode);
        }
        chatInput.focus();
      })
      .catch(function (err) {
        console.error("[Jarvis] Failed to load history:", err);
      });
  }

  function renderHistoryList() {
    historyList.innerHTML = "";

    if (!historyOpen) {
      historyList.style.display = "none";
      return;
    }

    historyList.style.display = "flex";

    if (chatList.length === 0) {
      var empty = document.createElement("div");
      empty.className = "history-empty";
      empty.textContent = "No conversations yet.";
      historyList.appendChild(empty);
      return;
    }

    chatList.forEach(function (chat) {
      var btn = document.createElement("button");
      btn.className = "history-item";
      if (currentChatId && currentChatId === chat.id) {
        btn.classList.add("active");
      }

      // Chat icon
      var icon = document.createElement("span");
      icon.className = "material-symbols-outlined";
      icon.textContent = "chat_bubble_outline";
      btn.appendChild(icon);

      // Chat title
      var titleSpan = document.createElement("span");
      titleSpan.className = "history-title";
      titleSpan.textContent = chat.title || "New chat";
      btn.appendChild(titleSpan);

      // Delete button
      var deleteBtn = document.createElement("button");
      deleteBtn.className = "history-delete";
      deleteBtn.title = "Delete chat";
      deleteBtn.innerHTML = '<span class="material-symbols-outlined">close</span>';
      deleteBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        showDeleteModal(chat.id);
      });
      btn.appendChild(deleteBtn);

      btn.addEventListener("click", function () {
        loadChat(chat.id);
      });

      historyList.appendChild(btn);
    });
  }

  // ════════════════════════════════════════════
  //  UI Helpers
  // ════════════════════════════════════════════

  function showWelcome() {
    if (welcomeScreen) welcomeScreen.style.display = "flex";
  }

  function hideWelcome() {
    if (welcomeScreen) welcomeScreen.style.display = "none";
  }

  function showThinking() {
    if (thinkingEl) thinkingEl.style.display = "flex";
    scrollToBottom();
  }

  function hideThinking() {
    if (thinkingEl) thinkingEl.style.display = "none";
  }

  function startSlowTimer() {
    slowTimer = setTimeout(function () {
      if (slowEl) slowEl.style.display = "block";
    }, SLOW_TIMEOUT_MS);
  }

  function clearSlowTimer() {
    if (slowTimer) {
      clearTimeout(slowTimer);
      slowTimer = null;
    }
    if (slowEl) slowEl.style.display = "none";
  }

  function scrollToBottom() {
    if (chatScroll) {
      setTimeout(function () {
        chatScroll.scrollTop = chatScroll.scrollHeight;
      }, 40);
    }
  }

  function formatTime() {
    var now = new Date();
    var h = now.getHours();
    var m = now.getMinutes();
    var ampm = h >= 12 ? "PM" : "AM";
    h = h % 12 || 12;
    m = m < 10 ? "0" + m : m;
    return h + ":" + m + " " + ampm;
  }

  // ════════════════════════════════════════════
  //  Chat Deletion
  // ════════════════════════════════════════════

  function injectDeleteModal() {
    var overlay = document.createElement("div");
    overlay.id = "delete-modal-overlay";
    overlay.className = "delete-modal-overlay";
    overlay.innerHTML =
      '<div class="delete-modal">' +
        '<p class="delete-modal-title">Delete this chat permanently?</p>' +
        '<p class="delete-modal-subtitle">This action cannot be undone.</p>' +
        '<div class="delete-modal-actions">' +
          '<button id="delete-modal-cancel" class="delete-modal-btn cancel">Cancel</button>' +
          '<button id="delete-modal-confirm" class="delete-modal-btn confirm">Delete</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(overlay);

    document.getElementById("delete-modal-cancel").addEventListener("click", function () {
      hideDeleteModal();
    });

    overlay.addEventListener("click", function (e) {
      if (e.target === overlay) hideDeleteModal();
    });
  }

  var pendingDeleteChatId = null;

  function showDeleteModal(chatId) {
    pendingDeleteChatId = chatId;
    var overlay = document.getElementById("delete-modal-overlay");
    if (overlay) overlay.classList.add("visible");

    // Re-bind confirm button each time (clone removes old listeners)
    var confirmBtn = document.getElementById("delete-modal-confirm");
    var newConfirm = confirmBtn.cloneNode(true);
    confirmBtn.parentNode.replaceChild(newConfirm, confirmBtn);
    newConfirm.addEventListener("click", function () {
      var chatIdToDelete = pendingDeleteChatId;
      hideDeleteModal();
      if (chatIdToDelete !== null) {
        deleteChat(chatIdToDelete);
      }
    });
  }

  function hideDeleteModal() {
    var overlay = document.getElementById("delete-modal-overlay");
    if (overlay) overlay.classList.remove("visible");
    pendingDeleteChatId = null;
  }

  async function deleteChat(chatId) {
    try {
      var res = await fetch("/chat/delete?chatId=" + encodeURIComponent(chatId), {
        method: "DELETE"
      });

      var result = await res.json();

      if (result.success) {
        // Remove from local list immediately
        chatList = chatList.filter(function (c) { return c.id !== chatId; });
        renderHistoryList();

        // If we just deleted the active chat, reset to welcome
        if (currentChatId === chatId) {
          currentChatId = null;
          chatMessages.innerHTML = "";
          showWelcome();
          resetPendingState();
          chatInput.focus();
        }
      } else {
        console.error("[Jarvis] Failed to delete chat. Server returned:", result);
      }
    } catch (err) {
      console.error("[Jarvis] DELETE FAILED:", err);
    }
  }

})();
