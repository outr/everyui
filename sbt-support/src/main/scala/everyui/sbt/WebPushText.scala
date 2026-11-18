package everyui.sbt

/** The service worker's reading of a Markdown push (`everyui.push.PushText.plain`, which the app's Scala reads with
  * EveryUI's Markdown parser) as plain words, a browser's notification showing no styles: `pushText(markdown)`, a
  * JavaScript function `WebApp.worker` writes into sw.js. Its rules are the shared ones, kept small, and PushTextJsSpec
  * checks it gives the Scala's words on the same cases: the marks of bold, slant and strikethrough gone, code without
  * its backticks, a link its words, a picture its alt text, an escape its letter, the common entities their letters; a
  * heading its words, a quote's lines without `>`, a fenced code block its lines, a list's items "• " or their
  * number, a task's box after the bullet, a nested list stood in under its item's words, a table's rows a line each
  * with their cells apart by tabs; paragraphs and other blocks a blank line apart, a line break a line break. Indented
  * code and what CommonMark's finer rules decide are read more simply than the parser reads them. */
object WebPushText {
  val function: String =
    """const pushText = markdown => {
      |  const entities = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: '\u00a0' };
      |  // a run of inline Markdown as its words: code spans and escapes first, then pictures, links and marks
      |  const inline = s => {
      |    const kept = [];
      |    const keep = t => { kept.push(t); return '\u0000' + (kept.length - 1) + '\u0001'; };
      |    let t = '';
      |    for (let i = 0; i < s.length;) {
      |      const c = s[i];
      |      if (c === '\\' && i + 1 < s.length && /[!-\/:-@\[-`{-~]/.test(s[i + 1])) { t += keep(s[i + 1]); i += 2; continue; }
      |      if (c === '`') {
      |        let n = 0;
      |        while (s[i + n] === '`') n++;
      |        const fence = '`'.repeat(n);
      |        let end = s.indexOf(fence, i + n);
      |        while (end >= 0 && s[end + n] === '`') { let k = end; while (s[k] === '`') k++; end = s.indexOf(fence, k); }
      |        if (end >= 0) {
      |          let code = s.slice(i + n, end);
      |          if (code.length > 1 && code[0] === ' ' && code[code.length - 1] === ' ' && code.trim()) code = code.slice(1, -1);
      |          t += keep(code); i = end + n; continue;
      |        }
      |        t += fence; i += n; continue;
      |      }
      |      t += c; i++;
      |    }
      |    const target = '\\(\\s*<?([^\\s)>]*)>?(?:\\s+(?:"[^"]*"|\'[^\']*\'))?\\s*\\)';
      |    t = t.replace(new RegExp('!\\[([^\\]]*)\\]' + target, 'g'), (m, alt, src) => alt || src);
      |    t = t.replace(new RegExp('\\[([^\\]]*)\\]' + target, 'g'),
      |      (m, words, href) => !words.trim() || words === href ? href.replace(/^mailto:/, '') : words);
      |    t = t.replace(/<((?:https?|ftp):[^\s<>]*|mailto:([^\s<>]*)|([^\s<>@]+@[^\s<>@]+))>/gi, (m, u, mail, at) => mail || at || u);
      |    for (let before = ''; before !== t;) {
      |      before = t;
      |      t = t.replace(/\*\*(?![\s*])([\s\S]*?[^\s])\*\*/g, '$1')
      |        .replace(/(^|[^\w])__(?![\s_])([\s\S]*?[^\s])__(?!\w)/g, '$1$2')
      |        .replace(/\*(?![\s*])([\s\S]*?[^\s*]|[^\s*])\*/g, '$1')
      |        .replace(/(^|[^\w])_(?![\s_])([\s\S]*?[^\s_]|[^\s_])_(?!\w)/g, '$1$2')
      |        .replace(/~~(?![\s~])([\s\S]*?[^\s~])~~/g, '$1')
      |        .replace(/(^|[^~])~(?![\s~])([^~]*?[^\s~]|[^\s~])~(?!~)/g, '$1$2');
      |    }
      |    t = t.replace(/&(#x[0-9a-f]+|#[0-9]+|[a-z]+);/gi, (m, e) => {
      |      if (e[0] !== '#') return entities[e] || m;
      |      const hex = e[1] === 'x' || e[1] === 'X';
      |      try { return String.fromCodePoint(parseInt(e.slice(hex ? 2 : 1), hex ? 16 : 10) || 0xfffd); } catch (x) { return '\ufffd'; }
      |    });
      |    return t.replace(/\u0000(\d+)\u0001/g, (m, k) => kept[+k]);
      |  };
      |  const cells = line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => inline(c.trim()));
      |  const rule = /^ {0,3}([-*_])( *\1){2,} *$/;
      |  const lines = String(markdown).replace(/\r\n?/g, '\n').split('\n');
      |  const out = [];
      |  // the open items of lists, the innermost last: where their words start in the text and in what is written
      |  let items = [];
      |  let last = 'none', blank = false, fence = null;
      |  const start = () => { if (out.length) out.push(''); blank = false; };
      |  for (let i = 0; i < lines.length; i++) {
      |    const raw = lines[i];
      |    if (fence) {
      |      if (new RegExp('^ {0,3}' + fence[0] + '{' + fence.length + ',} *$').test(raw)) { fence = null; last = 'code'; }
      |      else out.push(raw);
      |      continue;
      |    }
      |    const line = raw.replace(/^( {0,3}> ?)+/, '').replace(/\s+$/, '').replace(/\\$/, '');
      |    if (!line.trim()) { blank = last !== 'none'; continue; }
      |    const opened = /^ {0,3}(`{3,}|~{3,})/.exec(line);
      |    if (opened) { start(); items = []; fence = opened[1]; last = 'code'; continue; }
      |    const heading = /^ {0,3}#{1,6}(?: +(.*?))?(?: +#+)? *$/.exec(line);
      |    if (heading) { start(); items = []; out.push(inline(heading[1] || '')); last = 'heading'; continue; }
      |    if (last === 'text' && !blank && /^ {0,3}(=+|-+) *$/.test(line)) { last = 'heading'; continue; }
      |    if (rule.test(line)) { items = []; last = 'rule'; blank = true; continue; }
      |    if (line.includes('|') && (last !== 'text' || blank) && i + 1 < lines.length &&
      |        /^ *\|? *:?-+:? *(\| *:?-+:? *)*\|? *$/.test(lines[i + 1]) && lines[i + 1].includes('-')) {
      |      start(); items = [];
      |      out.push(cells(line).join('\t'));
      |      i++;
      |      while (i + 1 < lines.length && lines[i + 1].includes('|') && lines[i + 1].trim()) out.push(cells(lines[++i]).join('\t'));
      |      last = 'table'; continue;
      |    }
      |    const item = /^( *)([-*+]|(\d{1,9})[.)])( +|$)(.*)$/.exec(line);
      |    // (a numbered list breaks into a paragraph only from 1)
      |    if (item && !(last === 'text' && !blank && item[3] !== undefined && item[3] !== '1')) {
      |      const at = item[1].length;
      |      let sibling = null;
      |      while (items.length && items[items.length - 1].words > at) sibling = items.pop();
      |      const ordered = item[3] !== undefined;
      |      const marker = ordered ? item[2].slice(-1) : item[2];
      |      // (another bullet or delimiter than the item before starts another list)
      |      if (sibling && sibling.marker !== marker) sibling = null;
      |      if (last !== 'item' || (!sibling && !items.length)) start();
      |      else if (blank || sibling && sibling.loose) { out.push(''); blank = false; }
      |      const pad = items.length ? items[items.length - 1].pad : 0;
      |      const n = ordered ? (sibling ? sibling.n + 1 : +item[3]) : 0;
      |      let words = item[5];
      |      const task = /^\[([ xX])\] +(.*)$/.exec(words);
      |      const mark = (ordered ? n + '. ' : '\u2022 ') + (task ? (task[1] === ' ' ? '\u2610 ' : '\u2611 ') : '');
      |      if (task) words = task[2];
      |      out.push(' '.repeat(pad) + mark + inline(words));
      |      items.push({ words: at + item[2].length + Math.max(1, item[4].length), pad: pad + mark.length, marker, n,
      |        loose: !!sibling && (sibling.loose || out[out.length - 2] === '') });
      |      last = 'item'; continue;
      |    }
      |    const indent = line.length - line.trimStart().length;
      |    while (blank && items.length && indent < items[items.length - 1].words) items.pop();
      |    if (items.length) {
      |      if (blank) { out.push(''); blank = false; items[items.length - 1].loose = true; }
      |      out.push(' '.repeat(items[items.length - 1].pad) + inline(line.trim()));
      |      last = 'item'; continue;
      |    }
      |    items = [];
      |    if (last !== 'text' || blank) start();
      |    out.push(inline(line.trim()));
      |    last = 'text';
      |  }
      |  while (out.length && !out[out.length - 1].trim()) out.pop();
      |  return out.join('\n');
      |};
      |""".stripMargin
}
