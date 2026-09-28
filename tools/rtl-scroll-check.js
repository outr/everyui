// The web's right-to-left rail check (tools/check.sh, run by webcheck's WebDrive on ?demo=rtlscroll): a rail of cards in
// a right-to-left page is the browser's own scroll, which starts at its right end. Where the first (red) card sits
// against the rail's right edge, and whether the rail scrolls, as it opens, once it is narrowed (a click on the green
// card) and once it has grown (a click on the red one); then the blue card, revealed (a click on it), at the rail's
// right edge. The page logs how far along the reveal put it ("[ui] rtlscroll revealed at 500"). WebDrive runs it as
// the body of a function.
const rail = [...document.querySelectorAll('div')].find(d => d.style.overflowX === 'auto');
const card = color => [...rail.querySelectorAll('div')].find(d => d.style.backgroundColor === color);
const red = card('rgb(255, 0, 0)'), green = card('rgb(0, 128, 0)'), blue = card('rgb(0, 0, 255)');
const gap = c => Math.round(rail.getBoundingClientRect().right - c.getBoundingClientRect().right);
const scrolls = () => { const was = rail.scrollLeft; rail.scrollLeft = -100; const moved = rail.scrollLeft !== was; rail.scrollLeft = was; return moved; };
const state = name => name + ' red=' + gap(red) + ' scrolls=' + scrolls();
const seen = [state('open')];
green.click();
seen.push(state('narrowed'));
red.click();
seen.push(state('grown'));
blue.click();
seen.push('revealed blue=' + gap(blue));
return seen.join(', ');
