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

  var bridge = {
    _ready: false,
    _loading: null,
    _editors: Object.create(null),       // editorId -> entry
    _diffs: Object.create(null),         // diffId -> entry
    _models: Object.create(null),        // uri -> model created by the app
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
    // in a web view of its own the page is served from the root; in the app's page the root is where it says
    var base = new URL(window.everyuiEmbed.hosted ? './' : (args.base || './'), document.baseURI).href;
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
    var editor = monaco.editor.create(container, options);
    var entry = { editor: editor, disposers: [], commands: Object.create(null), actions: Object.create(null),
                  external: false, eol: null, container: container };
    bridge._editors[editorId] = entry;
    wireEditor(entry, editor, 'editor.', { editorId: editorId });
    entry.disposers.push(editor.onDidChangeModel(function () {
      entry.eol = null;
      var model = editor.getModel();
      bridge.emit('editor.modelChange', { editorId: editorId, uri: model ? model.uri.toString() : null,
        versionId: model ? model.getVersionId() : 0, value: model ? model.getValue() : '' });
    }));
    if (args.width > 0 && args.height > 0 && !window.everyuiEmbed.hosted) editor.layout({ width: args.width, height: args.height });
    else editor.layout();
    var model = editor.getModel();
    return { editorId: editorId, uri: model.uri.toString(), versionId: model.getVersionId(), value: model.getValue() };
  };

  // What every editor (a diff's two included) tells the app, `ids` naming it.
  function wireEditor(entry, editor, prefix, ids) {
    function emit(type, payload) { bridge.emit(prefix + type, Object.assign({}, ids, payload || {})); }
    var d = entry.disposers;
    d.push(editor.onDidChangeModelContent(function (e) {
      var model = editor.getModel();
      var eol = model.getEOL();
      var payload = {
        versionId: e.versionId,
        changes: e.changes.map(function (c) { return { rangeOffset: c.rangeOffset, rangeLength: c.rangeLength, text: c.text }; }),
        flush: !!e.isFlush,
        external: !!entry.external,
        undo: !!e.isUndoing,
        redo: !!e.isRedoing
      };
      // the whole text only when the edits alone cannot be followed: a flush, or line endings changed under them
      if (e.isFlush || e.isEolChange || (entry.eol !== null && entry.eol !== eol)) payload.value = model.getValue();
      entry.eol = eol;
      emit('contentChange', payload);
    }));
    d.push(editor.onDidChangeCursorPosition(function (e) {
      emit('cursorChange', { line: e.position.lineNumber, column: e.position.column });
    }));
    d.push(editor.onDidChangeCursorSelection(function (e) { emit('selectionChange', selection(e.selection)); }));
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

  // The text replaced, as one edit the user can undo, and the change marked as the app's own.
  function replaceText(entry, model, value, keepUndo) {
    entry.external = true;
    try {
      if (keepUndo) {
        if (model.getValue() === value) return;
        model.pushStackElement();
        model.pushEditOperations([], [{ range: model.getFullModelRange(), text: value }], function () { return null; });
        model.pushStackElement();
      } else model.setValue(value);
    } finally { entry.external = false; }
  }

  handlers['editor.getValue'] = function (a) { return editorEntry(a.editorId).editor.getValue(); };
  handlers['editor.setValue'] = function (a) {
    var entry = editorEntry(a.editorId);
    replaceText(entry, entry.editor.getModel(), a.value == null ? '' : String(a.value), a.keepUndo !== false);
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
    var existing = uri ? monaco.editor.getModel(uri) : null;
    var model = existing || monaco.editor.createModel(a.value || '', a.language || 'plaintext', uri);
    if (existing) { model.setValue(a.value || ''); monaco.editor.setModelLanguage(model, a.language || 'plaintext'); }
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
  handlers['editor.setModel'] = function (a) {
    var model = bridge._models[a.uri] || monaco.editor.getModel(monaco.Uri.parse(a.uri));
    if (!model) throw new Error('editor.setModel: unknown uri ' + a.uri);
    editorEntry(a.editorId).editor.setModel(model);
    return null;
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
        providerId: providerId, requestId: requestId, uri: model.uri.toString(), languageId: model.getLanguageId(),
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
          return (Array.isArray(result) ? result : [result]).map(function (l) {
            return { uri: monaco.Uri.parse(l.uri), range: range(l.range) };
          });
        });
      }
    }));
  };

  handlers['languages.registerReferenceProvider'] = function (a) {
    return provider(a, monaco.languages.registerReferenceProvider(a.languageId, {
      provideReferences: function (model, position, context) {
        return ask('language.references.request', a.providerId, model, position, {
          context: { includeDeclaration: !!(context && context.includeDeclaration) }
        }).then(function (result) {
          return (result || []).map(function (l) { return { uri: monaco.Uri.parse(l.uri), range: range(l.range) }; });
        });
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
                  originalSide: { disposers: [], external: false, eol: null, commands: {}, actions: {} },
                  modifiedSide: { disposers: [], external: false, eol: null, commands: {}, actions: {} } };
    wireEditor(entry.originalSide, diff.getOriginalEditor(), 'diff.original.', { diffId: diffId });
    wireEditor(entry.modifiedSide, diff.getModifiedEditor(), 'diff.modified.', { diffId: diffId });
    entry.disposers.push(diff.onDidUpdateDiff(function () {
      bridge.emit('diff.updated', { diffId: diffId, changes: (diff.getLineChanges() || []).length });
    }));
    bridge._diffs[diffId] = entry;
    if (a.width > 0 && a.height > 0 && !window.everyuiEmbed.hosted) diff.layout({ width: a.width, height: a.height });
    else diff.layout();
    return diffId;
  };
  handlers['diff.setOriginal'] = function (a) {
    var e = diffEntry(a.diffId);
    replaceText(e.originalSide, e.original, a.value == null ? '' : String(a.value), a.keepUndo !== false);
    return null;
  };
  handlers['diff.setModified'] = function (a) {
    var e = diffEntry(a.diffId);
    replaceText(e.modifiedSide, e.modified, a.value == null ? '' : String(a.value), a.keepUndo !== false);
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
  handlers['diff.lineChanges'] = function (a) {
    return (diffEntry(a.diffId).diff.getLineChanges() || []).map(function (c) {
      return { originalStartLine: c.originalStartLineNumber, originalEndLine: c.originalEndLineNumber,
               modifiedStartLine: c.modifiedStartLineNumber, modifiedEndLine: c.modifiedEndLineNumber };
    });
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
