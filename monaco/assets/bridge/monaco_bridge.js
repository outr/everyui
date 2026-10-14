// everyui-monaco: the guest's side of the Monaco bridge.
//
// Loaded next to Monaco on every platform: into the app's own page on the web, and into host.html in a web view of
// its own on Android and the desktop. It talks to the app only through the embed channel (window.everyuiEmbed,
// EveryUI's EmbedGuest): the app sends {callId, method, args} as JSON text, and hears back {type, payload}, a call's
// reply being the event `_return` {callId, value | error}. Every editor, diff editor, model and provider lives here,
// keyed by the ids the app is given, so one page (the web's) can serve any number of editors.
//
// Ported from flutter_monaco_editor's bridge, with its faults fixed: no global patch of Object.getOwnPropertyDescriptor
// (nothing on the Scala.js side needs it); editors made on the element they are given, with no polling; content
// changes sent as the edits themselves, not the whole buffer on every key; a provider request sends the model's
// version, not its text, and its timer is cleared on a reply; removeCommand works; setValue can keep the undo stack
// and says the change was the app's own.
(function () {
  'use strict';
  if (window.monacoBridge) return;

  var channel = window.everyuiEmbed.channel('monaco');
  // where this script was loaded from (the root is the directory above bridge/), which the page's own files follow
  var script = document.currentScript;

  var bridge = {
    _ready: false,
    _loading: null,
    _editors: Object.create(null),       // editorId -> entry
    _diffs: Object.create(null),         // diffId -> entry
    _models: Object.create(null),        // uri -> model created by the app
    _shared: Object.create(null),        // the app's uri -> {model, listener}: models opened through MonacoModels
    _providers: Object.create(null),     // providerId -> IDisposable
    _pending: Object.create(null),       // requestId -> {resolve, reject, timer}
    _nextEditor: 1,
    _nextDiff: 1,
    _nextRequest: 1,
    providerTimeoutMs: 8000,

    emit: function (type, payload) {
      try { channel.send(JSON.stringify({ type: type, payload: payload || {} })); }
      catch (e) { console.error('[monacoBridge] emit failed', e); }
    },

    invoke: function (method, args) {
      var handler = handlers[method];
      if (!handler) throw new Error('monacoBridge: unknown method "' + method + '"');
      return handler(args || {});
    },

    invokeAsync: function (callId, method, args) {
      var self = this;
      try {
        Promise.resolve(this.invoke(method, args))
          .then(function (value) { self.emit('_return', { callId: callId, value: value === undefined ? null : value }); })
          .catch(function (err) { self.emit('_return', { callId: callId, error: String(err && err.message || err) }); });
      } catch (err) {
        self.emit('_return', { callId: callId, error: String(err && err.message || err) });
      }
    }
  };

  var handlers = Object.create(null);

  channel.onMessage(function (text) {
    var call;
    try { call = JSON.parse(text); } catch (e) { console.error('[monacoBridge] unreadable call', e); return; }
    bridge.invokeAsync(call.callId, call.method, call.args || {});
  });

  // Where the editors are laid out by the page itself (a web view of their own), the window's size is theirs.
  if (window.everyuiEmbed.hosted) {
    window.addEventListener('resize', function () {
      var id;
      for (id in bridge._editors) bridge._editors[id].editor.layout();
      for (id in bridge._diffs) bridge._diffs[id].diff.layout();
    });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Loading Monaco: its stylesheet, an AMD loader (require.js, unless the page has one), then editor.main
  // ------------------------------------------------------------------------------------------------------------

  handlers['bridge.init'] = function (args) {
    if (bridge._ready) return { version: window.monaco && monaco.editor && args.version || null };
    if (bridge._loading) return bridge._loading;
    // in a web view of its own the page is served from the root; in the app's page the root is where this script came
    // from (a sealed bundle's base, WebAssets), else where the app says
    var base = window.everyuiEmbed.hosted ? new URL('./', document.baseURI).href :
      script && script.src ? new URL('../', script.src).href : new URL(args.base || './', document.baseURI).href;
    if (base.charAt(base.length - 1) !== '/') base += '/';
    bridge._loading = new Promise(function (resolve, reject) {
      var css = document.createElement('link');
      css.rel = 'stylesheet';
      css.href = base + 'vs/editor/editor.main.css';
      document.head.appendChild(css);
      var own = document.createElement('link');
      own.rel = 'stylesheet';
      own.href = base + 'bridge/everyui-monaco.css';
      document.head.appendChild(own);

      function start() {
        try {
          window.require.config({ paths: { vs: base + 'vs' } });
          window.require(['vs/editor/editor.main'], function (m) {
            if (!window.monaco) window.monaco = m;
            installOpener();
            bridge._ready = true;
            resolve({ version: args.version || null });
            bridge.emit('bridge.ready', {});
          }, function (err) {
            reject(new Error('monaco: editor.main did not load: ' + (err && err.message || err)));
          });
        } catch (e) { reject(e); }
      }

      if (typeof window.require === 'function' && window.require.config) start();
      else {
        var loader = document.createElement('script');
        loader.src = base + 'require.js';
        loader.onload = start;
        loader.onerror = function () { reject(new Error('monaco: could not load ' + loader.src)); };
        document.head.appendChild(loader);
      }
    });
    return bridge._loading;
  };

  // ------------------------------------------------------------------------------------------------------------
  // Editors
  // ------------------------------------------------------------------------------------------------------------

  handlers['editor.create'] = function (args) {
    var container = window.everyuiEmbed.container(args.containerId);
    if (!container) throw new Error('editor.create: no element "' + args.containerId + '"');
    var editorId = String(bridge._nextEditor++);
    var options = Object.assign({}, args.options || {}, { automaticLayout: false });
    // made on a model the app opened (models.open), shared with every other editor showing it, rather than its own
    var shown = args.uri ? sharedModel(args.uri) : null;
    if (shown) { delete options.value; delete options.language; options.model = shown; }
    var editor = monaco.editor.create(container, options);
    var entry = { editor: editor, disposers: [], commands: Object.create(null), actions: Object.create(null),
                  eol: null, container: container, views: Object.create(null) };
    bridge._editors[editorId] = entry;
    wireEditor(entry, editor, 'editor.', { editorId: editorId });
    entry.disposers.push(editor.onDidChangeModel(function () {
      entry.eol = null;
      var model = editor.getModel();
      bridge.emit('editor.modelChange', { editorId: editorId, uri: model ? uriOf(model) : null,
        versionId: model ? model.getVersionId() : 0, value: model ? model.getValue() : '' });
    }));
    if (args.width > 0 && args.height > 0 && !window.everyuiEmbed.hosted) editor.layout({ width: args.width, height: args.height });
    else editor.layout();
    var model = editor.getModel();
    return { editorId: editorId, uri: uriOf(model), versionId: model.getVersionId(), value: model.getValue() };
  };

  // Monaco puts its context views (a button's tooltip from its hover service; the context menu, in a shadow root under
  // a host of its own) in the editor's elements and places them by the window's coordinates, as in VS Code, whose
  // workbench root holds them. Here those elements are in the app's box for the editor: it clips them to the box, its
  // right edge wraps them (the tooltip above the find widget's close button grew a line down over the button, so the
  // pointer left the button, which flickered and could not be clicked), and under the app's UiScale (a CSS scale) the
  // coordinates are not the box's own, so they land off by the scale. So they float in one layer over the page
  // instead, unclipped and at the page's own size, where the window's coordinates are the layer's; a Monaco
  // component, for the theme's colours.
  var floating = null;
  function floatOut(element) {
    if (!element || element.__everyuiFloats) return;
    element.__everyuiFloats = true;
    element.appendChild = function (node) {
      var list = node.classList;
      if (!list || !(list.contains('context-view') || list.contains('shadow-root-host'))) return Node.prototype.appendChild.call(this, node);
      if (!floating) {
        floating = document.createElement('div');
        floating.className = 'everyui-monaco-floating monaco-component';
        document.body.appendChild(floating);
      }
      return floating.appendChild(node);
    };
  }

  // The editor's own fields (its hidden textarea, or the element EditContext types into, and the find widget's) are
  // never a sign-in: they carry the markers that keep the browser's autofill and the password managers out (1Password,
  // LastPass, Bitwarden, Dashlane), as an EveryUI field held `Autofill.Off` does, and so does any Monaco makes later.
  var editable = 'textarea, input, [contenteditable], .native-edit-context';
  function keepAutofillOut(root) {
    function mark(el) {
      if (el.getAttribute('data-1p-ignore') === 'true') return;
      el.setAttribute('autocomplete', 'off');
      ['data-1p-ignore', 'data-lpignore', 'data-bwignore'].forEach(function (a) { el.setAttribute(a, 'true'); });
      el.setAttribute('data-form-type', 'other');
    }
    function sweep(node) {
      if (node.nodeType !== 1) return;
      if (node.matches(editable)) mark(node);
      if (node.firstElementChild) node.querySelectorAll(editable).forEach(mark);
    }
    sweep(root);
    var observer = new MutationObserver(function (changes) {
      for (var i = 0; i < changes.length; i++) changes[i].addedNodes.forEach(sweep);
    });
    observer.observe(root, { childList: true, subtree: true });
    return { dispose: function () { observer.disconnect(); } };
  }

  // What every editor (a diff's two included) tells the app, `ids` naming it.
  function wireEditor(entry, editor, prefix, ids) {
    function emit(type, payload) { bridge.emit(prefix + type, Object.assign({}, ids, payload || {})); }
    floatOut(editor.getContainerDomNode());
    var d = entry.disposers;
    d.push(keepAutofillOut(editor.getContainerDomNode()));
    // the menu's host goes in the editor's view, made anew with each model
    floatOut(editor.getDomNode());
    d.push(editor.onDidChangeModel(function () { floatOut(editor.getDomNode()); }));
    d.push(editor.onDidChangeModelContent(function (e) {
      var model = editor.getModel();
      var eol = model.getEOL();
      var payload = {
        versionId: e.versionId,
        changes: e.changes.map(function (c) { return { rangeOffset: c.rangeOffset, rangeLength: c.rangeLength, text: c.text }; }),
        flush: !!e.isFlush,
        external: !!model.__everyuiExternal,
        undo: !!e.isUndoing,
        redo: !!e.isRedoing
      };
      // the whole text only when the edits alone cannot be followed: a flush, or line endings changed under them
      if (e.isFlush || e.isEolChange || (entry.eol !== null && entry.eol !== eol)) payload.value = model.getValue();
      entry.eol = eol;
      emit('contentChange', payload);
    }));
    // each with what moved it (Monaco's source: "code.jump" for a jump within the file, which the opener makes) and why
    // (its CursorChangeReason)
    function moved(source) { return source || 'api'; }
    d.push(editor.onDidChangeCursorPosition(function (e) {
      emit('cursorChange', { line: e.position.lineNumber, column: e.position.column,
        secondaryPositions: (e.secondaryPositions || []).map(function (p) { return { line: p.lineNumber, column: p.column }; }),
        source: moved(e.source), reason: e.reason || 0 });
    }));
    d.push(editor.onDidChangeCursorSelection(function (e) {
      emit('selectionChange', Object.assign(selection(e.selection), {
        secondarySelections: (e.secondarySelections || []).map(selection), source: moved(e.source), reason: e.reason || 0 }));
    }));
    d.push(editor.onDidScrollChange(function (e) {
      emit('scrollChange', {
        scrollTop: e.scrollTop, scrollLeft: e.scrollLeft, scrollHeight: e.scrollHeight, scrollWidth: e.scrollWidth,
        scrollTopChanged: !!e.scrollTopChanged, scrollLeftChanged: !!e.scrollLeftChanged,
        scrollHeightChanged: !!e.scrollHeightChanged, scrollWidthChanged: !!e.scrollWidthChanged
      });
    }));
    d.push(editor.onDidFocusEditorText(function () { emit('focus'); }));
    d.push(editor.onDidBlurEditorText(function () { emit('blur'); }));
    d.push(editor.onKeyDown(function (e) { emit('keyDown', key(e)); }));
    d.push(editor.onKeyUp(function (e) { emit('keyUp', key(e)); }));
    // Escape with nothing of the editor's own to close is handed back to the app; so are Tab and Shift+Tab where the
    // text cannot be edited, since they would insert nothing
    var idle = 'editorTextFocus && !suggestWidgetVisible && !findWidgetVisible && !inSnippetMode && ' +
      '!parameterHintsVisible && !renameInputVisible && !referenceSearchVisible && !inReferenceSearchEditor && ' +
      '!accessibilityHelpWidgetVisible && !markersNavigationVisible && !editorHasMultipleSelections && !hasNextTabstop && ' +
      '!editorHasSelection';
    editor.addCommand(monaco.KeyCode.Escape, function () { emit('handBack', { key: 'Escape', shift: false }); }, idle);
    editor.addCommand(monaco.KeyCode.Tab, function () { emit('handBack', { key: 'Tab', shift: false }); },
      'editorTextFocus && editorReadonly');
    editor.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.Tab, function () { emit('handBack', { key: 'Tab', shift: true }); },
      'editorTextFocus && editorReadonly');
    // where the engine has no asynchronous clipboard (JavaFX's WebKit), Monaco's copy and cut reach nothing: they go to
    // the app, which puts the text on the system's clipboard itself (Platform.copyText)
    if (window.everyuiEmbed.hosted && !(navigator.clipboard && navigator.clipboard.writeText)) {
      var copy = function (cut) {
        var model = editor.getModel();
        var sel = editor.getSelections() || [];
        var text = sel.map(function (s) { return s.isEmpty() ? model.getLineContent(s.startLineNumber) + model.getEOL() : model.getValueInRange(s); }).join(model.getEOL());
        bridge.emit('clipboard.copy', { text: text });
        if (cut && !editor.getOption(monaco.editor.EditorOption.readOnly))
          editor.executeEdits('cut', sel.map(function (s) {
            return { range: s.isEmpty() ? new monaco.Range(s.startLineNumber, 1, s.startLineNumber + 1, 1) : s, text: '' };
          }));
      };
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyC, function () { copy(false); }, 'editorTextFocus');
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyX, function () { copy(true); }, 'editorTextFocus');
    }
    entry.eol = editor.getModel() ? editor.getModel().getEOL() : null;
  }

  function editorEntry(id) {
    var entry = bridge._editors[id];
    if (!entry) throw new Error('unknown editorId: ' + id);
    return entry;
  }

  function diffEntry(id) {
    var entry = bridge._diffs[id];
    if (!entry) throw new Error('unknown diffId: ' + id);
    return entry;
  }

  // The text replaced, as one edit the user can undo, and the change marked as the app's own: on the model, so every
  // editor showing it says so.
  function replaceText(model, value, keepUndo, relayed) {
    marked(model, true, relayed, function () {
      if (keepUndo) {
        if (model.getValue() === value) return;
        model.pushStackElement();
        model.pushEditOperations([], [{ range: model.getFullModelRange(), text: value }], function () { return null; });
        model.pushStackElement();
      } else model.setValue(value);
    });
  }

  // `f` run with the model's changes marked: the app's own (external), and one copied from another copy (relayed).
  function marked(model, external, relayed, f) {
    model.__everyuiExternal = !!external;
    model.__everyuiRelayed = !!relayed;
    try { f(); } finally { model.__everyuiExternal = false; model.__everyuiRelayed = false; }
  }

  // The app's name for a model: the uri it was opened with (models.open), else Monaco's.
  function uriOf(model) { return model.__everyuiUri || model.uri.toString(); }

  handlers['editor.getValue'] = function (a) { return editorEntry(a.editorId).editor.getValue(); };
  handlers['editor.setValue'] = function (a) {
    var entry = editorEntry(a.editorId);
    replaceText(entry.editor.getModel(), a.value == null ? '' : String(a.value), a.keepUndo !== false);
    return null;
  };
  handlers['editor.setLanguage'] = function (a) {
    var model = editorEntry(a.editorId).editor.getModel();
    if (model) monaco.editor.setModelLanguage(model, a.language);
    return null;
  };
  handlers['editor.setTheme'] = function (a) { monaco.editor.setTheme(a.theme); return null; };
  handlers['editor.focus'] = function (a) { editorEntry(a.editorId).editor.focus(); return null; };
  handlers['editor.blur'] = function (a) {
    var entry = editorEntry(a.editorId);
    var active = document.activeElement;
    if (active && entry.container.contains(active)) active.blur();
    return null;
  };
  handlers['editor.layout'] = function (a) {
    var editor = editorEntry(a.editorId).editor;
    if (window.everyuiEmbed.hosted || !(a.width > 0)) editor.layout(); else editor.layout({ width: a.width, height: a.height });
    return null;
  };
  handlers['editor.getPosition'] = function (a) {
    var p = editorEntry(a.editorId).editor.getPosition();
    return p ? { line: p.lineNumber, column: p.column } : null;
  };
  handlers['editor.setPosition'] = function (a) {
    editorEntry(a.editorId).editor.setPosition({ lineNumber: a.line, column: a.column });
    return null;
  };
  handlers['editor.getSelection'] = function (a) {
    var s = editorEntry(a.editorId).editor.getSelection();
    return s ? selection(s) : null;
  };
  handlers['editor.getSelections'] = function (a) {
    return (editorEntry(a.editorId).editor.getSelections() || []).map(selection);
  };
  handlers['editor.setSelection'] = function (a) {
    editorEntry(a.editorId).editor.setSelection({
      selectionStartLineNumber: a.selectionStartLine, selectionStartColumn: a.selectionStartColumn,
      positionLineNumber: a.positionLine, positionColumn: a.positionColumn
    });
    return null;
  };
  handlers['editor.revealLine'] = function (a) { editorEntry(a.editorId).editor.revealLine(a.line); return null; };
  handlers['editor.revealLineInCenter'] = function (a) { editorEntry(a.editorId).editor.revealLineInCenter(a.line); return null; };
  handlers['editor.revealRange'] = function (a) { editorEntry(a.editorId).editor.revealRange(range(a)); return null; };
  handlers['editor.revealRangeInCenter'] = function (a) { editorEntry(a.editorId).editor.revealRangeInCenter(range(a)); return null; };
  handlers['editor.updateOptions'] = function (a) {
    var options = Object.assign({}, a.options || {});
    delete options.automaticLayout;
    editorEntry(a.editorId).editor.updateOptions(options);
    return null;
  };
  handlers['editor.executeEdits'] = function (a) {
    var editor = editorEntry(a.editorId).editor;
    return editor.executeEdits(a.source || 'everyui', (a.edits || []).map(function (e) {
      return { range: range(e.range), text: e.text, forceMoveMarkers: !!e.forceMoveMarkers };
    }));
  };
  handlers['editor.dispose'] = function (a) {
    var entry = bridge._editors[a.editorId];
    if (!entry) return null;
    dispose(entry.disposers);
    try { entry.editor.dispose(); } catch (e) { console.error(e); }
    delete bridge._editors[a.editorId];
    return null;
  };

  handlers['editor.deltaDecorations'] = function (a) {
    var editor = editorEntry(a.editorId).editor;
    var model = editor.getModel();
    // clamped to the text, so a range to the end of a line (a blame note after it) can say so with a large column
    var decorations = (a.newDecorations || []).map(function (d) {
      return { range: model ? model.validateRange(range(d.range)) : range(d.range), options: injected(d.options || {}) };
    });
    return editor.deltaDecorations(a.oldIds || [], decorations);
  };

  handlers['markers.set'] = function (a) {
    var model = a.uri ? monaco.editor.getModel(monaco.Uri.parse(a.uri)) : editorEntry(a.editorId).editor.getModel();
    if (model) monaco.editor.setModelMarkers(model, a.owner || 'default', (a.markers || []).map(marker));
    return null;
  };
  handlers['markers.clear'] = function (a) {
    var model = a.uri ? monaco.editor.getModel(monaco.Uri.parse(a.uri)) : editorEntry(a.editorId).editor.getModel();
    if (model) monaco.editor.setModelMarkers(model, a.owner || 'default', []);
    return null;
  };

  handlers['editor.addAction'] = function (a) {
    var entry = editorEntry(a.editorId);
    var editorId = a.editorId, actionId = a.id;
    if (entry.actions[actionId]) entry.actions[actionId].dispose();
    var descriptor = {
      id: actionId,
      label: a.label,
      keybindings: a.keybindings || [],
      run: function () { bridge.emit('editor.actionInvoked', { editorId: editorId, actionId: actionId }); }
    };
    if (a.contextMenuGroupId) descriptor.contextMenuGroupId = a.contextMenuGroupId;
    if (typeof a.contextMenuOrder === 'number') descriptor.contextMenuOrder = a.contextMenuOrder;
    if (a.precondition) descriptor.precondition = a.precondition;
    if (a.keybindingContext) descriptor.keybindingContext = a.keybindingContext;
    entry.actions[actionId] = entry.editor.addAction(descriptor);
    return null;
  };
  handlers['editor.removeAction'] = function (a) {
    var entry = editorEntry(a.editorId);
    if (entry.actions[a.id]) { entry.actions[a.id].dispose(); delete entry.actions[a.id]; }
    return null;
  };

  // Monaco's editor.addCommand hands back an id, not something to dispose of, so a command could never be taken
  // away: each is bound under a context key of its own, which removeCommand turns off.
  handlers['editor.addCommand'] = function (a) {
    var entry = editorEntry(a.editorId);
    var editorId = a.editorId, commandId = a.commandId;
    var keyName = 'everyui.command.' + editorId + '.' + commandId.replace(/[^A-Za-z0-9_]/g, '_');
    var on = entry.editor.createContextKey(keyName, true);
    var when = keyName + (a.context ? ' && (' + a.context + ')' : '');
    entry.editor.addCommand(a.keybinding, function () {
      bridge.emit('editor.commandInvoked', { editorId: editorId, commandId: commandId });
    }, when);
    entry.commands[commandId] = on;
    return null;
  };
  handlers['editor.removeCommand'] = function (a) {
    var entry = editorEntry(a.editorId);
    var on = entry.commands[a.commandId];
    if (on) { on.set(false); delete entry.commands[a.commandId]; }
    return null;
  };

  handlers['editor.trigger'] = function (a) {
    editorEntry(a.editorId).editor.trigger(a.source || 'everyui', a.handlerId, a.payload);
    return null;
  };

  // ------------------------------------------------------------------------------------------------------------
  // Themes (global to the page)
  // ------------------------------------------------------------------------------------------------------------

  handlers['editor.defineTheme'] = function (a) { monaco.editor.defineTheme(a.name, a.theme || {}); return null; };

  // Styles for the app's decorations (the classes they name), by id: given again, replaced. A constructed sheet where
  // the engine has them, which a strict Content-Security-Policy allows where it would refuse a <style>.
  var sheets = Object.create(null);
  handlers['bridge.addStyles'] = function (a) {
    if (typeof CSSStyleSheet === 'function' && 'adoptedStyleSheets' in document && CSSStyleSheet.prototype.replaceSync) {
      var sheet = sheets[a.id];
      if (!sheet) {
        sheet = sheets[a.id] = new CSSStyleSheet();
        document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
      }
      sheet.replaceSync(a.css || '');
    } else {
      var style = sheets[a.id];
      if (!style) { style = sheets[a.id] = document.createElement('style'); document.head.appendChild(style); }
      style.textContent = a.css || '';
    }
    return null;
  };

  // ------------------------------------------------------------------------------------------------------------
  // Models
  // ------------------------------------------------------------------------------------------------------------

  handlers['models.create'] = function (a) {
    var uri = a.uri ? monaco.Uri.parse(a.uri) : undefined;
    // a model that already has the uri (made before, or for a peek by the resolver) is the one: kept as it is, its
    // edits and undo with it, so opening a file twice never loses what was typed in it
    var existing = uri ? monaco.editor.getModel(uri) : null;
    var model = existing || monaco.editor.createModel(a.value || '', a.language || 'plaintext', uri);
    var key = model.uri.toString();
    bridge._models[key] = model;
    return { uri: key };
  };
  handlers['models.dispose'] = function (a) {
    var model = bridge._models[a.uri];
    if (model) { try { model.dispose(); } catch (e) { console.error(e); } delete bridge._models[a.uri]; }
    return null;
  };
  handlers['models.getValue'] = function (a) {
    var model = monaco.editor.getModel(monaco.Uri.parse(a.uri));
    return model ? { value: model.getValue(), versionId: model.getVersionId() } : null;
  };
  // Each editor keeps its own view of each model it has shown (the cursor, the selection, the scroll, what is folded),
  // put back as it shows that model again: two editors on one model each keep theirs.
  handlers['editor.setModel'] = function (a) {
    var model = (bridge._shared[a.uri] && bridge._shared[a.uri].model) || bridge._models[a.uri] ||
      monaco.editor.getModel(monaco.Uri.parse(a.uri));
    if (!model) throw new Error('editor.setModel: unknown uri ' + a.uri);
    var entry = editorEntry(a.editorId), editor = entry.editor, was = editor.getModel();
    if (was === model) return null;
    if (was) entry.views[uriOf(was)] = editor.saveViewState();
    editor.setModel(model);
    var view = entry.views[uriOf(model)];
    if (view) editor.restoreViewState(view);
    return null;
  };

  // Models the app opened through its registry (MonacoModels), by the app's uri: each says every change to it once,
  // whichever editor (or none) made it, so the app keeps one copy of its text, whether it is dirty, and, where each
  // editor has a Monaco of its own (a web view each), copies the change into the other copies (models.applyEdits).
  function sharedModel(uri) {
    var s = bridge._shared[uri];
    if (!s) throw new Error('no model open for ' + uri);
    return s.model;
  }

  handlers['models.open'] = function (a) {
    var s = bridge._shared[a.uri];
    if (!s) {
      var parsed = monaco.Uri.parse(a.uri);
      var model = monaco.editor.getModel(parsed) || monaco.editor.createModel(a.value || '', a.language || 'plaintext', parsed);
      model.__everyuiUri = a.uri;
      var eol = model.getEOL();
      s = bridge._shared[a.uri] = { model: model, listener: model.onDidChangeContent(function (e) {
        var payload = {
          uri: a.uri, versionId: e.versionId, flush: !!e.isFlush, undo: !!e.isUndoing, redo: !!e.isRedoing,
          external: !!model.__everyuiExternal, relayed: !!model.__everyuiRelayed,
          changes: e.changes.map(function (c) { return { rangeOffset: c.rangeOffset, rangeLength: c.rangeLength, text: c.text }; })
        };
        if (e.isFlush || e.isEolChange || model.getEOL() !== eol) payload.value = model.getValue();
        eol = model.getEOL();
        bridge.emit('model.contentChange', payload);
      }) };
    }
    return { uri: a.uri, versionId: s.model.getVersionId(), value: s.model.getValue() };
  };
  handlers['models.close'] = function (a) {
    var s = bridge._shared[a.uri];
    if (!s) return null;
    delete bridge._shared[a.uri];
    for (var id in bridge._editors) delete bridge._editors[id].views[a.uri];
    try { s.listener.dispose(); s.model.dispose(); } catch (e) { console.error(e); }
    return null;
  };
  handlers['models.setValue'] = function (a) {
    replaceText(sharedModel(a.uri), a.value == null ? '' : String(a.value), a.keepUndo !== false, a.relayed);
    return null;
  };
  handlers['models.setLanguage'] = function (a) { monaco.editor.setModelLanguage(sharedModel(a.uri), a.language); return null; };
  // Edits made in another copy of the model, made here too as one step that can be undone. They refer to the text
  // before them, which must be as long as `length`: otherwise this copy is out of step, and says so.
  handlers['models.applyEdits'] = function (a) {
    var model = sharedModel(a.uri);
    if (model.getValueLength() !== a.length) return { applied: false };
    var edits = (a.changes || []).map(function (c) {
      return { range: monaco.Range.fromPositions(model.getPositionAt(c.rangeOffset), model.getPositionAt(c.rangeOffset + c.rangeLength)),
               text: c.text };
    });
    marked(model, a.external, true, function () {
      model.pushStackElement();
      model.pushEditOperations([], edits, function () { return null; });
      model.pushStackElement();
    });
    return { applied: true };
  };

  // ------------------------------------------------------------------------------------------------------------
  // Language providers: Monaco asks, the app answers (languages.respond)
  // ------------------------------------------------------------------------------------------------------------

  function ask(type, providerId, model, position, extra) {
    var requestId = 'req-' + (bridge._nextRequest++);
    return new Promise(function (resolve, reject) {
      var timer = setTimeout(function () {
        if (bridge._pending[requestId]) {
          delete bridge._pending[requestId];
          reject(new Error(type + ' timed out after ' + bridge.providerTimeoutMs + 'ms'));
        }
      }, bridge.providerTimeoutMs);
      bridge._pending[requestId] = { resolve: resolve, reject: reject, timer: timer };
      bridge.emit(type, Object.assign({
        providerId: providerId, requestId: requestId, uri: uriOf(model), languageId: model.getLanguageId(),
        versionId: model.getVersionId(), line: position.lineNumber, column: position.column
      }, extra || {}));
    });
  }

  handlers['languages.respond'] = function (a) {
    var pending = bridge._pending[a.requestId];
    if (!pending) return null;
    delete bridge._pending[a.requestId];
    clearTimeout(pending.timer);
    if (a.error) pending.reject(new Error(a.error)); else pending.resolve(a.result);
    return null;
  };

  handlers['languages.unregisterProvider'] = function (a) {
    var d = bridge._providers[a.providerId];
    if (d) { try { d.dispose(); } catch (e) { console.error(e); } delete bridge._providers[a.providerId]; }
    return null;
  };

  function provider(a, d) {
    if (bridge._providers[a.providerId]) bridge._providers[a.providerId].dispose();
    bridge._providers[a.providerId] = d;
    return null;
  }

  handlers['languages.registerCompletionProvider'] = function (a) {
    return provider(a, monaco.languages.registerCompletionItemProvider(a.languageId, {
      triggerCharacters: a.triggerCharacters || [],
      provideCompletionItems: function (model, position, context) {
        return ask('language.completion.request', a.providerId, model, position, {
          context: { triggerKind: context.triggerKind, triggerCharacter: context.triggerCharacter || null }
        }).then(function (result) {
          if (!result) return { suggestions: [] };
          var word = model.getWordUntilPosition(position);
          var here = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
          return {
            suggestions: (result.suggestions || []).map(function (item) { return completionItem(item, here); }),
            incomplete: !!result.incomplete
          };
        });
      }
    }));
  };

  handlers['languages.registerHoverProvider'] = function (a) {
    return provider(a, monaco.languages.registerHoverProvider(a.languageId, {
      provideHover: function (model, position) {
        return ask('language.hover.request', a.providerId, model, position).then(function (result) {
          if (!result) return null;
          return { contents: result.contents || [], range: result.range ? range(result.range) : undefined };
        });
      }
    }));
  };

  handlers['languages.registerSignatureHelpProvider'] = function (a) {
    return provider(a, monaco.languages.registerSignatureHelpProvider(a.languageId, {
      signatureHelpTriggerCharacters: a.triggerCharacters || [],
      signatureHelpRetriggerCharacters: a.retriggerCharacters || [],
      provideSignatureHelp: function (model, position, token, context) {
        return ask('language.signatureHelp.request', a.providerId, model, position, {
          context: { triggerKind: context.triggerKind, triggerCharacter: context.triggerCharacter || null,
                     isRetrigger: !!context.isRetrigger }
        }).then(function (result) {
          if (!result) return null;
          return {
            value: { signatures: result.signatures || [], activeSignature: result.activeSignature || 0,
                     activeParameter: result.activeParameter || 0 },
            dispose: function () {}
          };
        });
      }
    }));
  };

  handlers['languages.registerDefinitionProvider'] = function (a) {
    return provider(a, monaco.languages.registerDefinitionProvider(a.languageId, {
      provideDefinition: function (model, position) {
        return ask('language.definition.request', a.providerId, model, position).then(function (result) {
          if (!result) return null;
          return located(Array.isArray(result) ? result : [result]);
        });
      }
    }));
  };

  handlers['languages.registerReferenceProvider'] = function (a) {
    return provider(a, monaco.languages.registerReferenceProvider(a.languageId, {
      provideReferences: function (model, position, context) {
        return ask('language.references.request', a.providerId, model, position, {
          context: { includeDeclaration: !!(context && context.includeDeclaration) }
        }).then(function (result) { return located(result || []); });
      }
    }));
  };

  handlers['languages.registerDocumentSymbolProvider'] = function (a) {
    function symbol(s) {
      return { name: s.name, detail: s.detail || '', kind: s.kind, tags: [], range: range(s.range),
               selectionRange: range(s.selectionRange || s.range), children: (s.children || []).map(symbol) };
    }
    return provider(a, monaco.languages.registerDocumentSymbolProvider(a.languageId, {
      displayName: a.displayName || undefined,
      provideDocumentSymbols: function (model) {
        return ask('language.documentSymbols.request', a.providerId, model, { lineNumber: 1, column: 1 })
          .then(function (result) { return (result || []).map(symbol); });
      }
    }));
  };

  // ------------------------------------------------------------------------------------------------------------
  // Other files: opening them (the app's tabs) and showing their lines (the peek widget)
  // ------------------------------------------------------------------------------------------------------------

  // Locations for Monaco, each file's model there first where the app can give it (models.resolve): the peek widget
  // shows only lines it has a model for, and go to definition goes nowhere without one.
  function located(list) {
    var uris = [];
    list.forEach(function (l) { if (uris.indexOf(l.uri) < 0) uris.push(l.uri); });
    return Promise.all(uris.map(resolveModel)).then(function () {
      return list.map(function (l) { return { uri: monaco.Uri.parse(l.uri), range: range(l.range) }; });
    });
  }

  // Models made for the app's other files, oldest first: kept for the peek widget, the oldest no editor shows let go
  // once there are more than bridge.resolvedLimit.
  var resolved = [];
  bridge.resolvedLimit = 20;
  bridge._resolving = false;

  handlers['models.setResolver'] = function (a) { bridge._resolving = !!a.enabled; return null; };

  function resolveModel(uri) {
    var parsed = monaco.Uri.parse(uri);
    var existing = monaco.editor.getModel(parsed);
    if (existing || !bridge._resolving) return Promise.resolve(existing);
    return askApp('models.resolve.request', { uri: uri }).then(function (source) {
      if (!source || monaco.editor.getModel(parsed)) return monaco.editor.getModel(parsed);
      var model = monaco.editor.createModel(source.text || '', source.language || undefined, parsed);
      resolved.push(model);
      trimResolved();
      return model;
    }, function () { return null; });
  }

  function trimResolved() {
    var shown = [];
    for (var id in bridge._editors) { var m = bridge._editors[id].editor.getModel(); if (m) shown.push(m); }
    for (var i = 0; resolved.length > bridge.resolvedLimit && i < resolved.length; ) {
      var m = resolved[i];
      if (m.isDisposed()) { resolved.splice(i, 1); continue; }
      if (shown.indexOf(m) < 0 && !bridge._models[m.uri.toString()] && !m.__everyuiUri) { resolved.splice(i, 1); m.dispose(); } else i++;
    }
  }

  // A request of the app's that is not a provider's: answered through languages.respond like one.
  function askApp(type, payload) {
    var requestId = 'req-' + (bridge._nextRequest++);
    return new Promise(function (resolve, reject) {
      var timer = setTimeout(function () {
        if (bridge._pending[requestId]) { delete bridge._pending[requestId]; reject(new Error(type + ' timed out')); }
      }, bridge.providerTimeoutMs);
      bridge._pending[requestId] = { resolve: resolve, reject: reject, timer: timer };
      bridge.emit(type, Object.assign({ requestId: requestId }, payload));
    });
  }

  // Go to definition (and a peek's "open") into a file the editor does not show: asked of the app, which opens it
  // its own way (a tab) and says whether it did; otherwise Monaco does what it does by itself. editor.setOpener turns
  // the asking off. One within the file the editor shows is never asked of the app: it is made here, at once (`jump`).
  bridge._opening = true;
  handlers['editor.setOpener'] = function (a) { bridge._opening = !!a.enabled; return null; };
  var openerInstalled = false;
  function installOpener() {
    if (openerInstalled || typeof monaco.editor.registerEditorOpener !== 'function') return;
    openerInstalled = true;
    monaco.editor.registerEditorOpener({
      openCodeEditor: function (source, resource, where) {
        var editorId = null;
        for (var id in bridge._editors) if (bridge._editors[id].editor === source) editorId = id;
        if (shows(source, resource)) {
          try { return jump(source, where); }
          catch (e) { console.error('[monacoBridge] a jump within the file failed; asking the app', e); }
        }
        if (!bridge._opening) return false;
        var at = null;
        if (where && typeof where.startLineNumber === 'number') at = { startLine: where.startLineNumber, startColumn: where.startColumn, endLine: where.endLineNumber, endColumn: where.endColumn };
        else if (where && typeof where.lineNumber === 'number') at = { startLine: where.lineNumber, startColumn: where.column, endLine: where.lineNumber, endColumn: where.column };
        var target = monaco.editor.getModel(resource);
        return askApp('editor.open.request', { editorId: editorId, uri: target ? uriOf(target) : resource.toString(), range: at })
          .then(function (handled) { return !!handled; }, function () { return false; });
      }
    });
  }

  // Whether `editor` shows `resource`: the model Monaco itself finds for it, so a uri written two ways (an "@" kept or
  // written %40) is one; or, for a model Monaco cannot find, the same uri as Monaco writes it.
  function shows(editor, resource) {
    var shown = editor.getModel();
    if (!shown) return false;
    var found = monaco.editor.getModel(resource);
    return found ? found === shown : shown.uri.toString() === monaco.Uri.parse(resource.toString()).toString();
  }

  // The jump made here, as Monaco's own code editor service makes one (the place selected and in the middle), under
  // VS Code's name for it: taken, so nothing after it is asked, and never left undone.
  function jump(editor, where) {
    if (where && typeof where.endLineNumber === 'number') {
      editor.setSelection(where, 'code.jump');
      editor.revealRangeInCenter(where, monaco.editor.ScrollType.Immediate);
    } else if (where && typeof where.lineNumber === 'number') {
      editor.setPosition(where, 'code.jump');
      editor.revealPositionInCenter(where, monaco.editor.ScrollType.Immediate);
    }
    return true;
  }

  // ------------------------------------------------------------------------------------------------------------
  // Diff editors
  // ------------------------------------------------------------------------------------------------------------

  handlers['diff.create'] = function (a) {
    var container = window.everyuiEmbed.container(a.containerId);
    if (!container) throw new Error('diff.create: no element "' + a.containerId + '"');
    var diffId = 'diff-' + (bridge._nextDiff++);
    var options = Object.assign({}, a.options || {}, { automaticLayout: false });
    var diff = monaco.editor.createDiffEditor(container, options);
    var original = monaco.editor.createModel(a.original || '', a.language || 'plaintext');
    var modified = monaco.editor.createModel(a.modified || '', a.language || 'plaintext');
    diff.setModel({ original: original, modified: modified });
    var entry = { diff: diff, original: original, modified: modified, disposers: [], container: container,
                  originalSide: { disposers: [], eol: null, commands: {}, actions: {} },
                  modifiedSide: { disposers: [], eol: null, commands: {}, actions: {} } };
    wireEditor(entry.originalSide, diff.getOriginalEditor(), 'diff.original.', { diffId: diffId });
    wireEditor(entry.modifiedSide, diff.getModifiedEditor(), 'diff.modified.', { diffId: diffId });
    // a click in the modified side's gutter, on a checkbox the app put there (diff.setChecks)
    entry.disposers.push(diff.getModifiedEditor().onMouseDown(function (m) {
      var t = m.target, types = monaco.editor.MouseTargetType;
      if (!t.position || (t.type !== types.GUTTER_GLYPH_MARGIN && t.type !== types.GUTTER_LINE_DECORATIONS)) return;
      bridge.emit('diff.gutterClick', { diffId: diffId, line: t.position.lineNumber,
        kind: t.type === types.GUTTER_GLYPH_MARGIN ? 'change' : 'line' });
    }));
    entry.disposers.push(diff.onDidUpdateDiff(function () {
      bridge.emit('diff.updated', { diffId: diffId, changes: lineChanges(diff) });
    }));
    bridge._diffs[diffId] = entry;
    if (a.width > 0 && a.height > 0 && !window.everyuiEmbed.hosted) diff.layout({ width: a.width, height: a.height });
    else diff.layout();
    return diffId;
  };
  handlers['diff.setOriginal'] = function (a) {
    var e = diffEntry(a.diffId);
    replaceText(e.original, a.value == null ? '' : String(a.value), a.keepUndo !== false);
    return null;
  };
  handlers['diff.setModified'] = function (a) {
    var e = diffEntry(a.diffId);
    replaceText(e.modified, a.value == null ? '' : String(a.value), a.keepUndo !== false);
    return null;
  };
  handlers['diff.getOriginal'] = function (a) { return diffEntry(a.diffId).original.getValue(); };
  handlers['diff.getModified'] = function (a) { return diffEntry(a.diffId).modified.getValue(); };
  handlers['diff.setLanguage'] = function (a) {
    var e = diffEntry(a.diffId);
    monaco.editor.setModelLanguage(e.original, a.language);
    monaco.editor.setModelLanguage(e.modified, a.language);
    return null;
  };
  handlers['diff.updateOptions'] = function (a) {
    var options = Object.assign({}, a.options || {});
    delete options.automaticLayout;
    diffEntry(a.diffId).diff.updateOptions(options);
    return null;
  };
  handlers['diff.layout'] = function (a) {
    var diff = diffEntry(a.diffId).diff;
    if (window.everyuiEmbed.hosted || !(a.width > 0)) diff.layout(); else diff.layout({ width: a.width, height: a.height });
    return null;
  };
  handlers['diff.focus'] = function (a) { diffEntry(a.diffId).diff.getModifiedEditor().focus(); return null; };
  handlers['diff.blur'] = function (a) {
    var e = diffEntry(a.diffId);
    var active = document.activeElement;
    if (active && e.container.contains(active)) active.blur();
    return null;
  };
  handlers['diff.revealLine'] = function (a) { diffEntry(a.diffId).diff.getModifiedEditor().revealLineInCenter(a.line); return null; };
  handlers['diff.goToDiff'] = function (a) {
    var diff = diffEntry(a.diffId).diff;
    if (typeof diff.goToDiff === 'function') diff.goToDiff(a.target === 'previous' ? 'previous' : 'next');
    return null;
  };
  handlers['diff.lineChanges'] = function (a) { return lineChanges(diffEntry(a.diffId).diff); };

  // The changed regions, each with its character changes where Monaco worked them out. A region added has no original
  // lines (originalEndLine 0, placed after originalStartLine); one deleted, no modified lines (likewise).
  function lineChanges(diff) {
    return (diff.getLineChanges() || []).map(function (c) {
      return {
        originalStartLine: c.originalStartLineNumber, originalEndLine: c.originalEndLineNumber,
        modifiedStartLine: c.modifiedStartLineNumber, modifiedEndLine: c.modifiedEndLineNumber,
        charChanges: (c.charChanges || []).map(function (x) {
          return {
            originalRange: { startLine: x.originalStartLineNumber, startColumn: x.originalStartColumn,
                             endLine: x.originalEndLineNumber, endColumn: x.originalEndColumn },
            modifiedRange: { startLine: x.modifiedStartLineNumber, startColumn: x.modifiedStartColumn,
                             endLine: x.modifiedEndLineNumber, endColumn: x.modifiedEndColumn }
          };
        })
      };
    });
  }

  // One side's lines of a change put in place of the other side's, as one edit that can be undone: `from` "original"
  // reverts the modified side's hunk, "modified" accepts it into the original.
  handlers['diff.applyChange'] = function (a) {
    var e = diffEntry(a.diffId), c = a.change;
    var fromOriginal = a.from !== 'modified';
    var source = fromOriginal ? e.original : e.modified, target = fromOriginal ? e.modified : e.original;
    var sStart = fromOriginal ? c.originalStartLine : c.modifiedStartLine, sEnd = fromOriginal ? c.originalEndLine : c.modifiedEndLine;
    var tStart = fromOriginal ? c.modifiedStartLine : c.originalStartLine, tEnd = fromOriginal ? c.modifiedEndLine : c.originalEndLine;
    var eol = target.getEOL();
    var text = sEnd > 0 ? source.getValueInRange(new monaco.Range(sStart, 1, sEnd, source.getLineMaxColumn(sEnd))) : null;
    var range, insert;
    if (tEnd > 0 && text !== null) {
      // lines replaced by lines
      range = new monaco.Range(tStart, 1, tEnd, target.getLineMaxColumn(tEnd));
      insert = text;
    } else if (tEnd > 0) {
      // lines the other side does not have: taken out with their line ends
      range = tEnd < target.getLineCount() ? new monaco.Range(tStart, 1, tEnd + 1, 1)
        : (tStart > 1 ? new monaco.Range(tStart - 1, target.getLineMaxColumn(tStart - 1), tEnd, target.getLineMaxColumn(tEnd))
                      : new monaco.Range(1, 1, tEnd, target.getLineMaxColumn(tEnd)));
      insert = '';
    } else {
      // lines this side does not have: put in after line tStart (0 for the top)
      if (tStart === 0) { range = new monaco.Range(1, 1, 1, 1); insert = text + eol; }
      else { var end = target.getLineMaxColumn(tStart); range = new monaco.Range(tStart, end, tStart, end); insert = eol + text; }
    }
    target.pushStackElement();
    target.pushEditOperations([], [{ range: range, text: insert }], function () { return null; });
    target.pushStackElement();
    return null;
  };

  // Checkboxes in the modified side's gutter: a change's in the glyph margin, a line's in the line decorations
  // column, each lit or not as the app says. A click on either is told to the app, which decides what it means.
  handlers['diff.setChecks'] = function (a) {
    var e = diffEntry(a.diffId), editor = e.diff.getModifiedEditor();
    // the line boxes stand where the + and - marks would: those give way to them
    if ((a.checks || []).some(function (c) { return c.kind === 'line'; })) e.diff.updateOptions({ renderIndicators: false });
    editor.updateOptions({ glyphMargin: true, lineDecorationsWidth: (a.checks || []).some(function (c) { return c.kind === 'line'; }) ? 18 : 10 });
    var decorations = (a.checks || []).map(function (c) {
      var options = { isWholeLine: true };
      var cls = 'everyui-check ' + (c.checked ? 'everyui-check-on' : 'everyui-check-off');
      if (c.kind === 'line') options.linesDecorationsClassName = cls; else options.glyphMarginClassName = cls;
      if (c.hover) { if (c.kind === 'line') options.hoverMessage = { value: c.hover }; else options.glyphMarginHoverMessage = { value: c.hover }; }
      var line = Math.max(1, Math.min(c.line, e.modified.getLineCount()));
      return { range: new monaco.Range(line, 1, line, 1), options: options };
    });
    e.checks = editor.deltaDecorations(e.checks || [], decorations);
    return null;
  };

  handlers['diff.dispose'] = function (a) {
    var e = bridge._diffs[a.diffId];
    if (!e) return null;
    dispose(e.disposers);
    dispose(e.originalSide.disposers);
    dispose(e.modifiedSide.disposers);
    try { e.diff.dispose(); } catch (x) { console.error(x); }
    try { e.original.dispose(); } catch (x) { console.error(x); }
    try { e.modified.dispose(); } catch (x) { console.error(x); }
    delete bridge._diffs[a.diffId];
    return null;
  };

  // ------------------------------------------------------------------------------------------------------------
  // Conversions
  // ------------------------------------------------------------------------------------------------------------

  function dispose(list) {
    for (var i = 0; i < list.length; i++) { try { list[i].dispose(); } catch (e) { console.error(e); } }
    list.length = 0;
  }

  function selection(s) {
    return {
      startLine: s.startLineNumber, startColumn: s.startColumn, endLine: s.endLineNumber, endColumn: s.endColumn,
      selectionStartLine: s.selectionStartLineNumber, selectionStartColumn: s.selectionStartColumn,
      positionLine: s.positionLineNumber, positionColumn: s.positionColumn
    };
  }

  function range(r) {
    if (!r) return r;
    return { startLineNumber: r.startLine, startColumn: r.startColumn, endLineNumber: r.endLine, endColumn: r.endColumn };
  }

  function completionItem(item, here) {
    var out = { label: item.label, kind: item.kind, insertText: item.insertText, insertTextRules: item.insertTextRules || 0,
                range: item.range ? range(item.range) : here };
    if (item.detail != null) out.detail = item.detail;
    if (item.documentation != null) out.documentation = item.documentation;
    if (item.sortText != null) out.sortText = item.sortText;
    if (item.filterText != null) out.filterText = item.filterText;
    if (item.preselect != null) out.preselect = item.preselect;
    if (item.commitCharacters != null) out.commitCharacters = item.commitCharacters;
    return out;
  }

  // Words before or after a range (`before`, `after`): the standalone editor draws no injected text, so they become
  // generated content on a class of their own, the one given styling them too.
  var injectedRules = Object.create(null), injectedCount = 0;
  function injected(options) {
    var out = Object.assign({}, options);
    [['after', 'afterContentClassName'], ['before', 'beforeContentClassName']].forEach(function (p) {
      var words = out[p[0]];
      if (!words || typeof words.content !== 'string') return;
      var rule = JSON.stringify(words.content);
      var cls = injectedRules[rule];
      if (!cls) {
        cls = injectedRules[rule] = 'everyui-injected-' + (++injectedCount);
        handlers['bridge.addStyles']({ id: cls, css: '.' + cls + '::' + p[0] + ' { content: ' + rule + '; }' });
      }
      out[p[1]] = cls + (words.inlineClassName ? ' ' + words.inlineClassName : '');
      delete out[p[0]];
    });
    return out;
  }

  function marker(m) {
    var out = { startLineNumber: m.startLine, startColumn: m.startColumn, endLineNumber: m.endLine, endColumn: m.endColumn,
                severity: m.severity, message: m.message };
    if (m.source) out.source = m.source;
    if (m.code !== undefined && m.code !== null) out.code = m.code;
    if (m.tags) out.tags = m.tags;
    if (m.relatedInformation) {
      out.relatedInformation = m.relatedInformation.map(function (r) {
        return { resource: monaco.Uri.parse(r.resource), message: r.message, startLineNumber: r.startLine,
                 startColumn: r.startColumn, endLineNumber: r.endLine, endColumn: r.endColumn };
      });
    }
    return out;
  }

  function key(e) {
    var be = e && e.browserEvent || {};
    return { key: be.key || '', code: be.code || '', keyCode: typeof e.keyCode === 'number' ? e.keyCode : 0,
             ctrl: !!e.ctrlKey, shift: !!e.shiftKey, alt: !!e.altKey, meta: !!e.metaKey };
  }

  window.monacoBridge = bridge;
})();
