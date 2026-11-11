// A check of everyui-monaco's bridge (monaco/assets/bridge/monaco_bridge.js) in Node, with no browser and no Monaco:
// the bridge is loaded into a context of its own with a stand-in window, embed channel and monaco.languages that
// keeps each provider registered, and each provider is then asked as Monaco would ask it. A request the app never
// answers (after providerTimeoutMs) and one it answers with an error must each come back as nothing, as Monaco takes
// it (no hover, no suggestions, no locations), with a warning in the console, never a rejected promise, which Monaco
// throws into the page as an error nothing caught. A request answered comes back as before.
//
//   node tools/monaco-bridge-check.js [bridge.js]   (exits 1, saying what failed, where one does not)
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const source = fs.readFileSync(process.argv[2] ||
  path.join(__dirname, '..', 'monaco', 'assets', 'bridge', 'monaco_bridge.js'), 'utf8');
const sent = [];
const warned = [];
const providers = {};
const unhandled = [];
process.on('unhandledRejection', err => unhandled.push(String(err && err.message || err)));

const register = kind => (languageId, p) => { providers[kind] = p; return { dispose() {} }; };
class Range { constructor(a, b, c, d) { Object.assign(this, { a, b, c, d }); } }
const monaco = {
  Range,
  Uri: { parse: s => ({ toString: () => s }) },
  editor: { getModel: () => null, registerEditorOpener() {} },
  languages: {
    registerCompletionItemProvider: register('completion'), registerHoverProvider: register('hover'),
    registerSignatureHelpProvider: register('signatureHelp'), registerDefinitionProvider: register('definition'),
    registerReferenceProvider: register('references'), registerDocumentSymbolProvider: register('documentSymbols')
  }
};
const window = {
  everyuiEmbed: {
    hosted: false, container: () => null,
    channel: () => ({ send: text => sent.push(JSON.parse(text)), onMessage() {} })
  },
  addEventListener() {}
};
window.monaco = monaco;
const context = vm.createContext({
  window, monaco, document: { currentScript: null }, navigator: {}, setTimeout, clearTimeout, Promise, URL,
  console: { log() {}, error: (...a) => warned.push(a.join(' ')), warn: (...a) => warned.push(a.join(' ')) }
});
vm.runInContext(source, context);
const bridge = window.monacoBridge;
bridge.providerTimeoutMs = 30;

const model = { uri: { toString: () => 'file:///a.scala' }, getLanguageId: () => 'scala', getVersionId: () => 1,
                getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) };
const position = { lineNumber: 1, column: 1 };
const asks = {
  completion: p => p.provideCompletionItems(model, position, { triggerKind: 0 }),
  hover: p => p.provideHover(model, position),
  signatureHelp: p => p.provideSignatureHelp(model, position, null, { triggerKind: 1 }),
  definition: p => p.provideDefinition(model, position),
  references: p => p.provideReferences(model, position, { includeDeclaration: true }),
  documentSymbols: p => p.provideDocumentSymbols(model)
};
// what each takes for nothing
const nothing = {
  completion: r => r && Array.isArray(r.suggestions) && r.suggestions.length === 0,
  hover: r => r === null, signatureHelp: r => r === null, definition: r => r === null,
  references: r => Array.isArray(r) && r.length === 0, documentSymbols: r => Array.isArray(r) && r.length === 0
};
const kinds = { completion: 'CompletionProvider', hover: 'HoverProvider', signatureHelp: 'SignatureHelpProvider',
                definition: 'DefinitionProvider', references: 'ReferenceProvider',
                documentSymbols: 'DocumentSymbolProvider' };

const failures = [];
const check = (what, ok) => { if (!ok) failures.push(what); };

(async () => {
  for (const kind of Object.keys(kinds)) {
    bridge.invoke('languages.register' + kinds[kind], { providerId: 'p-' + kind, languageId: 'scala' });
    check(kind + ' registered', !!providers[kind]);
    // never answered
    warned.length = 0;
    const timedOut = await asks[kind](providers[kind]).then(r => ({ r }), e => ({ e }));
    check(kind + ' timed out as nothing, not rejected (' + (timedOut.e && timedOut.e.message) + ')', !timedOut.e);
    check(kind + ' timed out as nothing (' + JSON.stringify(timedOut.r) + ')',
          !timedOut.e && nothing[kind](timedOut.r));
    check(kind + ' timeout warned', warned.some(w => w.includes('timed out after 30ms') && w.includes('nothing')));
    // answered with an error
    warned.length = 0;
    const failing = asks[kind](providers[kind]).then(r => ({ r }), e => ({ e }));
    bridge.invoke('languages.respond',
                  { requestId: sent[sent.length - 1].payload.requestId, error: 'the server fell over' });
    const failed = await failing;
    check(kind + ' error as nothing, not rejected', !failed.e && nothing[kind](failed.r));
    check(kind + ' error warned', warned.some(w => w.includes('the server fell over')));
  }
  // an answer still comes back as one
  const hovering = asks.hover(providers.hover);
  bridge.invoke('languages.respond', { requestId: sent[sent.length - 1].payload.requestId,
                                       result: { contents: [{ value: 'Int' }] } });
  const hover = await hovering;
  check('hover answered', hover && hover.contents[0].value === 'Int');
  await new Promise(r => setTimeout(r, 10));
  check('no unhandled rejection (' + unhandled.join('; ') + ')', unhandled.length === 0);
  if (failures.length) {
    console.log('FAILED:\n  ' + failures.join('\n  '));
    process.exit(1);
  }
  console.log('monaco bridge: ' + Object.keys(kinds).length + ' providers answer a timeout and an error as nothing, ' +
              'an answer as it was');
})();
