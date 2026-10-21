// everyui-terminal: the guest's side of the terminal bridge.
//
// Loaded after xterm.js and its addons on every platform: into the app's own page on the web, and into host.html in a
// web view of its own on Android and the desktop. It talks to the app only through the embed channel
// (window.everyuiEmbed, EveryUI's EmbedGuest), in text: `name<US>id<US>payload`, US being the unit separator (\u001f),
// the payload last and taken whole, so it may hold anything. `id` is the terminal's (its embed's id, the element it
// draws into), so one page (the web's) can serve any number of terminals.
//
// From the app: create (the options as JSON), write, options (JSON), fit, focus, blur, paste, clear, reset, selectAll,
// clearSelection, scrollToBottom, scrollLines (a count), copy, keep (the app's chords, as JSON), dispose.
// To the app: ready and resize ("cols,rows"), input and binary (what the terminal sends), title, bell, selection (its
// text), link (an address clicked), copy (the text to put on the clipboard), error.
(function () {
  'use strict';
  if (window.terminalBridge) return;

  var US = '\u001f';
  var channel = window.everyuiEmbed.channel('terminal');
  // where this script was loaded from: the root is the directory above bridge/
  var script = document.currentScript;
  var base = script && script.src ? new URL('../', script.src).href : './';
  var mac = /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent);

  var bridge = {
    terminals: Object.create(null),   // id -> entry
    emit: function (name, id, payload) {
      try { channel.send(name + US + id + US + (payload == null ? '' : String(payload))); }
      catch (e) { console.error('[terminalBridge] emit failed', e); }
    }
  };
  window.terminalBridge = bridge;

  // xterm's stylesheet, once for the page
  (function () {
    var link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = base + 'xterm.css';
    document.head.appendChild(link);
    var own = document.createElement('link');
    own.rel = 'stylesheet';
    own.href = base + 'bridge/everyui-terminal.css';
    document.head.appendChild(own);
  })();

  channel.onMessage(function (text) {
    var first = text.indexOf(US);
    var second = first < 0 ? -1 : text.indexOf(US, first + 1);
    if (second < 0) { console.error('[terminalBridge] unreadable message'); return; }
    var name = text.substring(0, first);
    var id = text.substring(first + 1, second);
    var payload = text.substring(second + 1);
    var handler = handlers[name];
    if (!handler) { bridge.emit('error', id, 'unknown message "' + name + '"'); return; }
    try { handler(id, payload); }
    catch (e) { bridge.emit('error', id, name + ': ' + String(e && e.message || e)); }
  });

  var handlers = Object.create(null);

  // What is said to a terminal still being made waits for it.
  function withTerminal(id, f) {
    var entry = bridge.terminals[id];
    if (!entry) return;
    if (entry.term) f(entry);
    else entry.waiting.push(f);
  }

  // Whether `e` is one of the chords the app keeps while the keys are the terminal's (`Keymap.embed`, a command made to
  // win inside embeds): those are left to go on to the app, and never reach the shell.
  function kept(entry, e) {
    var key = e.key === ' ' ? 'space' : String(e.key).toLowerCase();
    for (var i = 0; i < entry.keep.length; i++) {
      var c = entry.keep[i];
      if (c.key === key && !!c.ctrl === e.ctrlKey && !!c.meta === e.metaKey && !!c.alt === e.altKey && !!c.shift === e.shiftKey)
        return true;
    }
    return false;
  }

  handlers['create'] = function (id, payload) {
    if (bridge.terminals[id]) return;
    var options = JSON.parse(payload || '{}');
    var entry = { term: null, fit: null, waiting: [], keep: options.keep || [], observer: null, disposed: false,
                  fontSize: options.fontSize, scale: uiScale(), fitFrame: 0, resume: false, id: id };
    bridge.terminals[id] = entry;
    var container = window.everyuiEmbed.container(id);
    if (!container) throw new Error('no element "' + id + '"');
    container.classList.add('everyui-terminal');

    function open() {
      if (entry.disposed) return;
      var term = new window.Terminal({
        fontFamily: options.fontFamily,
        fontSize: options.fontSize * entry.scale,
        lineHeight: options.lineHeight || 1.0,
        scrollback: options.scrollback,
        cursorBlink: !!options.cursorBlink,
        cursorStyle: options.cursorStyle || 'block',
        macOptionIsMeta: !!options.optionIsMeta,
        screenReaderMode: !!options.screenReader,
        allowProposedApi: false,
        // a translucent ground kept translucent: read only by a GPU renderer, not the DOM renderer this bundle draws
        // with, so it costs nothing, opaque or not, and never needs the terminal made again
        allowTransparency: true,
        theme: options.theme || {}
      });
      var fit = new window.FitAddon.FitAddon();
      term.loadAddon(fit);
      if (options.links && window.WebLinksAddon)
        term.loadAddon(new window.WebLinksAddon.WebLinksAddon(function (event, uri) {
          event.preventDefault();
          bridge.emit('link', id, uri);
        }));
      term.onData(function (data) { bridge.emit('input', id, data); });
      term.onBinary(function (data) { bridge.emit('binary', id, data); });
      term.onResize(function (size) { bridge.emit('resize', id, size.cols + ',' + size.rows); });
      term.onTitleChange(function (title) { bridge.emit('title', id, title); });
      term.onBell(function () { bridge.emit('bell', id, ''); });
      term.onSelectionChange(function () { bridge.emit('selection', id, term.getSelection()); });
      term.attachCustomKeyEventHandler(function (e) {
        if (e.type !== 'keydown') return !kept(entry, e);
        if (kept(entry, e)) return false;
        var letter = String(e.key).toLowerCase();
        // copying: Ctrl+Shift+C (Cmd+C on a Mac), since Ctrl+C is the shell's; the text goes through the app, which
        // reaches the system's clipboard on every platform
        var copy = mac ? (e.metaKey && !e.ctrlKey && !e.altKey && letter === 'c')
                       : (e.ctrlKey && e.shiftKey && !e.altKey && !e.metaKey && letter === 'c');
        if (copy) {
          if (term.hasSelection()) bridge.emit('copy', id, term.getSelection());
          e.preventDefault();
          return false;
        }
        // pasting: Ctrl+Shift+V (Cmd+V on a Mac) is left to the engine, whose paste the terminal hears
        var paste = mac ? (e.metaKey && !e.ctrlKey && !e.altKey && letter === 'v')
                        : (e.ctrlKey && e.shiftKey && !e.altKey && !e.metaKey && letter === 'v');
        return !paste;
      });
      // drawn at the window's pixels inside the app's scale (everyui-terminal.css), where a pointer is read
      var box = document.createElement('div');
      box.className = 'everyui-terminal-unscaled';
      container.appendChild(box);
      term.open(box);
      installTouchScroll(entry, term);
      // the textarea xterm types through is never a sign-in: the markers that keep the browser's autofill and the
      // password managers out of it, as an EveryUI field held `Autofill.Off` has
      if (term.textarea) {
        term.textarea.setAttribute('autocomplete', 'off');
        ['data-1p-ignore', 'data-lpignore', 'data-bwignore'].forEach(function (a) { term.textarea.setAttribute(a, 'true'); });
        term.textarea.setAttribute('data-form-type', 'other');
      }
      entry.term = term;
      entry.fit = fit;
      fitNow(entry);
      if (window.ResizeObserver) {
        entry.observer = new ResizeObserver(function () { scheduleFit(entry); });
        entry.observer.observe(box);
      }
      bridge.emit('ready', id, term.cols + ',' + term.rows);
      var waiting = entry.waiting;
      entry.waiting = [];
      for (var i = 0; i < waiting.length; i++) waiting[i](entry);
    }

    // the face measured before the grid is laid out: a web font not yet loaded would give the wrong cells
    if (document.fonts && document.fonts.load)
      document.fonts.load(options.fontSize + 'px ' + options.fontFamily).then(open, open);
    else open();
  };

  // xterm's touch viewport scrolls its local buffer. With mouse reporting enabled (tmux), history belongs to
  // the remote program instead. Translate a vertical finger drag to the same wheel reports as a touchpad.
  // Taps, long presses, multiple fingers and ordinary shell scrollback keep their existing handling.
  function installTouchScroll(entry, term) {
    var gesture = null;
    var element = term.element;
    function start(e) {
      gesture = e.touches.length === 1 && term.modes.mouseTrackingMode !== 'none' ? {
        id: e.touches[0].identifier, x: e.touches[0].clientX, y: e.touches[0].clientY,
        lastY: e.touches[0].clientY, pending: 0, dragging: false, time: Date.now()
      } : null;
    }
    function move(e) {
      if (!gesture) return;
      if (e.touches.length !== 1 || term.modes.mouseTrackingMode === 'none' || term.hasSelection()) {
        gesture = null;
        return;
      }
      var touch = e.touches[0];
      if (touch.identifier !== gesture.id) { gesture = null; return; }
      if (!gesture.dragging) {
        var dx = Math.abs(touch.clientX - gesture.x), dy = Math.abs(touch.clientY - gesture.y);
        if (Date.now() - gesture.time > 500 || (dx > 8 && dx > dy)) { gesture = null; return; }
        if (dy < 8) return;
        gesture.dragging = true;
      }
      e.preventDefault();
      e.stopPropagation();
      var screen = element.querySelector('.xterm-screen');
      var height = screen && screen.getBoundingClientRect().height / term.rows;
      if (!(height > 0)) return;
      gesture.pending += gesture.lastY - touch.clientY;
      gesture.lastY = touch.clientY;
      var lines = Math.trunc(gesture.pending / height);
      if (!lines) return;
      gesture.pending -= lines * height;
      // Dispatch on xterm itself so its protocol/coordinates and mouse encoding remain authoritative,
      // without also scrolling the inner viewport or synthesizing keyboard input into the shell.
      element.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true,
        clientX: touch.clientX, clientY: touch.clientY, deltaY: lines, deltaMode: 1 }));
    }
    function end() { gesture = null; }
    element.addEventListener('touchstart', start, { capture: true, passive: true });
    element.addEventListener('touchmove', move, { capture: true, passive: false });
    element.addEventListener('touchend', end, true);
    element.addEventListener('touchcancel', end, true);
    entry.disposeTouchScroll = function () {
      element.removeEventListener('touchstart', start, true);
      element.removeEventListener('touchmove', move, true);
      element.removeEventListener('touchend', end, true);
      element.removeEventListener('touchcancel', end, true);
    };
  }

  // The size the app is drawn at (everyui.theme.UiScale), 1 in a page of its own.
  function uiScale() {
    var factor = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--everyui-ui-scale'));
    return factor > 0 ? factor : 1;
  }

  // The text sized by the size the app is drawn at, as the box it is in is drawn back at the window's pixels.
  function fitNow(entry) {
    if (entry.disposed || !entry.term || !entry.fit) return;
    var scale = uiScale();
    if (scale !== entry.scale) {
      entry.scale = scale;
      entry.term.options.fontSize = entry.fontSize * scale;
    }
    var el = entry.term.element && entry.term.element.parentElement;
    if (el && (el.clientWidth === 0 || el.clientHeight === 0)) return;
    try { entry.fit.fit(); } catch (e) { console.error('[terminalBridge] fit failed', e); }
  }

  // Width and height can change on separate layout turns. Measure once at the next paint rather than
  // sending both intermediate grids to a remote full-screen program, which clears and repaints on each resize.
  function scheduleFit(entry) {
    if (entry.disposed || entry.fitFrame) return;
    entry.fitFrame = window.requestAnimationFrame(function () {
      entry.fitFrame = 0;
      fitNow(entry);
      if (entry.resume) {
        entry.resume = false;
        var el = entry.term && entry.term.element;
        if (el && el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0) {
          entry.term.refresh(0, entry.term.rows - 1);
          // Even an unchanged grid must be reasserted after sleep/reconnect: the remote PTY may have
          // been resized by another view while this browser was suspended.
          bridge.emit('resume', entry.id, entry.term.cols + ',' + entry.term.rows);
        }
      }
    });
  }

  function resumed() {
    if (document.hidden) return;
    for (var id in bridge.terminals) {
      var entry = bridge.terminals[id];
      if (entry.term && entry.term.element.getBoundingClientRect().width > 0) {
        entry.resume = true;
        scheduleFit(entry);
      }
    }
  }
  document.addEventListener('visibilitychange', resumed);
  window.addEventListener('focus', resumed);
  window.addEventListener('pageshow', resumed);
  // OS sleep can preserve both focus and visibility. A delayed heartbeat catches that path too.
  var lastHeartbeat = Date.now();
  window.setInterval(function () {
    var now = Date.now();
    if (now - lastHeartbeat > 30000) resumed();
    lastHeartbeat = now;
  }, 10000);

  // A change of the size the app is drawn at, which Platform.setUiScale sets on the page's root: the box need not change
  // size with it (a terminal filling a screen that grows as the app is drawn smaller), so the observer may not hear it.
  if (window.MutationObserver) {
    new MutationObserver(function () {
      for (var id in bridge.terminals) scheduleFit(bridge.terminals[id]);
    }).observe(document.documentElement, { attributes: true, attributeFilter: ['style'] });
  }

  // Where the terminal is laid out by the page itself (a web view of its own), the window's size is its.
  if (window.everyuiEmbed.hosted) {
    window.addEventListener('resize', function () {
      for (var id in bridge.terminals) scheduleFit(bridge.terminals[id]);
    });
  }

  handlers['write'] = function (id, data) { withTerminal(id, function (e) { e.term.write(data); }); };
  handlers['paste'] = function (id, text) { withTerminal(id, function (e) { e.term.paste(text); }); };
  handlers['arrow'] = function (id, key) {
    var final = { Up: 'A', Down: 'B', Left: 'D', Right: 'C' }[key];
    if (!final) return;
    withTerminal(id, function (e) {
      e.term.input('\x1b' + (e.term.modes.applicationCursorKeysMode ? 'O' : '[') + final, true);
    });
  };
  handlers['fit'] = function (id) { withTerminal(id, scheduleFit); };
  handlers['focus'] = function (id) { withTerminal(id, function (e) { scheduleFit(e); e.term.focus(); }); };
  handlers['blur'] = function (id) { withTerminal(id, function (e) { e.term.blur(); }); };
  handlers['clear'] = function (id) { withTerminal(id, function (e) { e.term.clear(); }); };
  handlers['reset'] = function (id) { withTerminal(id, function (e) { e.term.reset(); }); };
  handlers['selectAll'] = function (id) { withTerminal(id, function (e) { e.term.selectAll(); }); };
  handlers['clearSelection'] = function (id) { withTerminal(id, function (e) { e.term.clearSelection(); }); };
  handlers['scrollToBottom'] = function (id) { withTerminal(id, function (e) { e.term.scrollToBottom(); }); };
  handlers['scrollLines'] = function (id, n) { withTerminal(id, function (e) { e.term.scrollLines(parseInt(n, 10) || 0); }); };
  handlers['copy'] = function (id) {
    withTerminal(id, function (e) { if (e.term.hasSelection()) bridge.emit('copy', id, e.term.getSelection()); });
  };
  handlers['keep'] = function (id, payload) {
    var entry = bridge.terminals[id];
    if (entry) entry.keep = JSON.parse(payload || '[]');
  };
  handlers['options'] = function (id, payload) {
    var options = JSON.parse(payload || '{}');
    withTerminal(id, function (e) {
      if (options.theme) e.term.options.theme = options.theme;
      if (options.fontSize) {
        e.fontSize = options.fontSize;
        e.term.options.fontSize = options.fontSize * e.scale;
      }
      if (options.fontFamily) e.term.options.fontFamily = options.fontFamily;
      scheduleFit(e);
    });
  };
  handlers['dispose'] = function (id) {
    var entry = bridge.terminals[id];
    if (!entry) return;
    entry.disposed = true;
    delete bridge.terminals[id];
    if (entry.observer) entry.observer.disconnect();
    if (entry.fitFrame) window.cancelAnimationFrame(entry.fitFrame);
    if (entry.disposeTouchScroll) entry.disposeTouchScroll();
    if (entry.term) entry.term.dispose();
  };
})();
