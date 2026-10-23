/** Geometry-based Word reconstruction. Kept separate from the Excel table detector. */
export type WordSpan = {
  text: string; x: number; y: number; width: number; size: number; font: string;
  bold?: boolean; italic?: boolean; color?: string; underline?: boolean; ascent?: number;
};
export type WordLine = { y: number; size: number; spans: WordSpan[]; cells: WordSpan[][] };
export type WordPage = { width: number; height: number; lines: WordLine[]; rules?: WordRule[]; warnings?: string[] };
export type WordRule = { x: number; y: number; width: number; height: number; color: string; thickness: number };
export type WordParagraph = {
  kind: 'paragraph'; lines: WordLine[]; x: number; y: number; width: number; leading: number;
  heading?: 'Title' | 'Heading1' | 'Heading2'; alignment: 'left' | 'center' | 'right';
  list?: 'bullet' | 'number'; role?: 'header' | 'footer';
};
export type WordGridCell = { column: number; columnSpan: number; rowSpan: number; lines: WordLine[] };
export type WordGridRow = { y: number; height: number; cells: WordGridCell[] };
export type WordTable = { kind: 'table'; lines: WordLine[]; rows: WordSpan[][][]; x: number; y: number; widths: number[]; borders: boolean; borderColor?: string; borderSize?: number; grid?: WordGridRow[] };
export type WordBlock = WordParagraph | WordTable;
const right = (line: WordLine) => Math.max(...line.spans.map(s => s.x + s.width));
const left = (line: WordLine) => Math.min(...line.spans.map(s => s.x));
export const isBold = (s: WordSpan) => s.bold ?? /bold|black|heavy|semibold|demi/i.test(s.font);
export const isItalic = (s: WordSpan) => s.italic ?? /italic|oblique/i.test(s.font);
export const lineText = (line: WordLine) => line.spans.map((s, i) => {
  const previous = line.spans[i - 1];
  return `${previous && s.x - previous.x - previous.width > s.size * .15 && !/\s$/.test(previous.text) && !/^\s/.test(s.text) ? ' ' : ''}${s.text}`;
}).join('');
const median = (values: number[]) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)] ?? 0;
const listType = (line: WordLine) => /^\s*[\u2022\u25cf\u25a0\u25e6\u2023\u2043\u2219\uf0b7-]\s+/u.test(lineText(line)) ? 'bullet' as const : /^\s*\d{1,3}[.)]\s+/.test(lineText(line)) ? 'number' as const : undefined;

function slicedLine(line: WordLine, spans: WordSpan[]): WordLine {
  return { ...line, spans, cells: [spans], size: Math.max(...spans.map(s => s.size)) };
}

/** Recover an actual ruled grid before guessing a table from text alignment.
 * Missing interior edges join primitive cells; only rectangular merges are accepted.
 * Each source baseline stays in its cell, including blank cells and wrapped text.
 */
function ruledTableAt(page: WordPage, start: number): WordTable | undefined {
  const first = page.lines[start]; const rules = page.rules ?? []; const tolerance = 1.5;
  const horizontal = rules.filter(r => r.height < .3 && r.width > 20);
  const vertical = rules.filter(r => r.width < .3 && r.height > 5);
  const unique = (values: number[]) => values.sort((a, b) => a - b).filter((v, i, a) => !i || v - a[i - 1] > tolerance);
  const tops = horizontal.filter(r => r.y < first.y && first.y - r.y <= first.size * 3 && r.x <= left(first) + tolerance && r.x + r.width >= right(first) - tolerance).sort((a, b) => b.y - a.y);
  for (const top of tops) {
    const x = top.x, edge = x + top.width;
    const sides = [x, edge].map(at => vertical.find(r => Math.abs(r.x - at) <= tolerance && r.y <= top.y + tolerance && r.y + r.height > first.y));
    if (!sides[0] || !sides[1]) continue;
    const bottom = Math.min(...sides.map(r => r!.y + r!.height));
    if (!horizontal.some(r => Math.abs(r.y - bottom) <= tolerance && r.x <= x + tolerance && r.x + r.width >= edge - tolerance)) continue;
    const hs = horizontal.filter(r => r.y >= top.y - tolerance && r.y <= bottom + tolerance && r.x >= x - tolerance && r.x + r.width <= edge + tolerance);
    const vs = vertical.filter(r => r.x >= x - tolerance && r.x <= edge + tolerance && r.y >= top.y - tolerance && r.y + r.height <= bottom + tolerance);
    const xs = unique([x, edge, ...vs.map(r => r.x)]), ys = unique([top.y, bottom, ...hs.map(r => r.y)]);
    const columns = xs.length - 1, rows = ys.length - 1;
    if (columns < 2 || columns > 12 || rows < 2 || rows > 250 || rows * columns > 1000) continue;
    let end = start;
    while (end < page.lines.length && page.lines[end].y < bottom - tolerance) end++;
    const lines = page.lines.slice(start, end);
    if (lines.some(l => l.spans.some(s => s.x < x - tolerance || s.x + s.width > edge + tolerance))) continue;
    const parents = Array.from({ length: rows * columns }, (_, i) => i);
    const root = (i: number): number => parents[i] === i ? i : (parents[i] = root(parents[i]));
    const join = (a: number, b: number) => { parents[root(b)] = root(a); };
    const covers = (segments: number[][], lo: number, hi: number) => {
      let reach = lo;
      for (const [a, b] of segments.sort((a, b) => a[0] - b[0])) { if (a > reach + tolerance) break; reach = Math.max(reach, b); }
      return reach >= hi - tolerance;
    };
    for (let r = 0; r < rows; r++) for (let c = 0; c < columns; c++) {
      const id = r * columns + c;
      if (c + 1 < columns && !covers(vs.filter(v => Math.abs(v.x - xs[c + 1]) <= tolerance).map(v => [v.y, v.y + v.height]), ys[r], ys[r + 1])) join(id, id + 1);
      if (r + 1 < rows && !covers(hs.filter(h => Math.abs(h.y - ys[r + 1]) <= tolerance).map(h => [h.x, h.x + h.width]), xs[c], xs[c + 1])) join(id, id + columns);
    }
    const groups = new Map<number, number[]>();
    parents.forEach((_, i) => { const id = root(i); groups.set(id, [...groups.get(id) ?? [], i]); });
    const grid: WordGridRow[] = ys.slice(0, -1).map((y, i) => ({ y, height: ys[i + 1] - y, cells: [] }));
    let valid = true;
    for (const ids of groups.values()) {
      const r0 = Math.min(...ids.map(i => Math.floor(i / columns))), r1 = Math.max(...ids.map(i => Math.floor(i / columns)));
      const c0 = Math.min(...ids.map(i => i % columns)), c1 = Math.max(...ids.map(i => i % columns));
      if (ids.length !== (r1 - r0 + 1) * (c1 - c0 + 1)) { valid = false; break; }
      const cellLines = lines.map(l => slicedLine(l, l.spans.filter(s => s.y > ys[r0] && s.y <= ys[r1 + 1] && s.x + s.width / 2 >= xs[c0] && s.x + s.width / 2 < xs[c1 + 1]))).filter(l => l.spans.length);
      grid[r0].cells.push({ column: c0, columnSpan: c1 - c0 + 1, rowSpan: r1 - r0 + 1, lines: cellLines });
    }
    if (!valid) continue;
    grid.forEach(r => r.cells.sort((a, b) => a.column - b.column));
    return { kind: 'table', lines, rows: [], x, y: top.y, widths: xs.slice(1).map((v, i) => v - xs[i]), borders: true, borderColor: top.color, borderSize: top.thickness, grid };
  }
}

function tableAt(page: WordPage, start: number): WordTable | undefined {
  const first = page.lines[start];
  if (first.cells.length < 2 || first.cells.length > 8) return;
  const anchors = first.cells.map(cell => cell[0].x);
  let end = start + 1;
  while (end < page.lines.length) {
    const line = page.lines[end];
    if (line.cells.length !== anchors.length || line.y - page.lines[end - 1].y > Math.max(45, first.size * 3) ||
      line.cells.some((cell, i) => Math.abs(cell[0].x - anchors[i]) > Math.max(5, first.size * .6))) break;
    end++;
  }
  const lines = page.lines.slice(start, end);
  const top = first.y - first.size; const bottom = lines.at(-1)!.y + first.size * .3;
  const rules = (page.rules ?? []).filter(r => r.y >= top - first.size && r.y <= bottom + first.size);
  const borders = rules.filter(r => r.width > right(first) - left(first) - first.size * 2 && r.height < 2).length >= 2;
  // Prose columns have long cells. Numeric/short aligned rows are stronger table evidence.
  const short = lines.every(l => l.cells.every(c => c.map(s => s.text).join('').trim().length <= 48));
  const numericRows = lines.filter(l => l.cells.some(c => /^[\s$\u20ac\u00a3=+\-\d.,/%:()]+$/.test(c.map(s => s.text).join('').trim()))).length;
  if (lines.length < 2 || (!borders && (lines.length < 3 || !short || numericRows < Math.ceil((lines.length - 1) / 2)))) return;
  const x = borders ? Math.min(left(first), ...rules.filter(r => r.width > 30).map(r => r.x)) : left(first);
  const tableRight = borders ? Math.max(right(first), ...rules.filter(r => r.width > 30).map(r => r.x + r.width)) : Math.max(...lines.map(right)) + 5;
  const edges = [x, ...anchors.slice(1).map(a => a - (anchors[0] - x || 4)), tableRight];
  return { kind: 'table', lines, rows: lines.map(l => l.cells), x, y: borders ? Math.min(...rules.filter(r => r.width > 30).map(r => r.y)) : top,
    widths: edges.slice(1).map((edge, i) => Math.max(1, edge - edges[i])), borders, borderColor: rules[0]?.color, borderSize: rules[0]?.thickness };
}

/** Split persistent gutters; full-width lines divide horizontal bands, preserving column reading order. */
function regions(lines: WordLine[], depth = 0): WordLine[][] {
  if (depth >= 3 || lines.length < 3) return [lines];
  const candidates: number[] = [];
  for (const line of lines) for (let c = 1; c < line.cells.length; c++) {
    const a = line.cells[c - 1]; const b = line.cells[c];
    const end = Math.max(...a.map(s => s.x + s.width));
    if (b[0].x - end >= Math.max(24, line.size * 2)) candidates.push((end + b[0].x) / 2);
  }
  // Columns need not share baselines. Compare nearby fragments as well as same-line cells.
  for (let i = 0; i < lines.length; i++) for (let j = i + 1; j < Math.min(lines.length, i + 8); j++) {
    const a = lines[i], b = lines[j];
    if (Math.abs(a.y - b.y) > Math.max(a.size, b.size) * 3) break;
    if (left(b) - right(a) >= 24) candidates.push((right(a) + left(b)) / 2);
    if (left(a) - right(b) >= 24) candidates.push((right(b) + left(a)) / 2);
  }
  let cut = 0; let best = 1;
  for (const candidate of candidates) {
    const leftY: number[] = []; const rightY: number[] = [];
    for (const line of lines) {
      if (line.spans.some(s => s.x < candidate && s.x + s.width > candidate)) continue;
      if (line.spans.some(s => s.x + s.width < candidate - 8)) leftY.push(line.y);
      if (line.spans.some(s => s.x > candidate + 8)) rightY.push(line.y);
    }
    const overlap = Math.min(Math.max(...leftY), Math.max(...rightY)) - Math.max(Math.min(...leftY), Math.min(...rightY));
    const count = Math.min(leftY.length, rightY.length);
    if (count > best && overlap >= lines[0].size) { best = count; cut = candidate; }
  }
  if (!cut) return [lines];
  const result: WordLine[][] = []; let a: WordLine[] = []; let b: WordLine[] = [];
  const flush = () => { if (a.length) result.push(...regions(a, depth + 1)); if (b.length) result.push(...regions(b, depth + 1)); a = []; b = []; };
  for (const line of lines) {
    if (line.spans.some(s => s.x < cut && s.x + s.width > cut)) { flush(); result.push([line]); }
    else {
      const l = line.spans.filter(s => s.x < cut); const r = line.spans.filter(s => s.x >= cut);
      if (l.length) a.push(slicedLine(line, l));
      if (r.length) b.push(slicedLine(line, r));
    }
  }
  flush(); return result;
}

export function reconstructWordPage(page: WordPage): { blocks: WordBlock[]; bodySize: number; regions: number } {
  const sizes = new Map<number, number>();
  for (const line of page.lines) for (const s of line.spans) sizes.set(Math.round(s.size * 2) / 2, (sizes.get(Math.round(s.size * 2) / 2) ?? 0) + s.text.length);
  const bodySize = [...sizes].sort((a, b) => b[1] - a[1])[0]?.[0] ?? 11;
  const blocks: WordBlock[] = []; let pending: WordLine[] = []; let regionCount = 0;
  const flush = () => {
    for (const region of regions(pending)) {
      regionCount++;
      const gaps = region.slice(1).map((line, i) => line.y - region[i].y).filter(gap => gap >= bodySize * .8 && gap <= bodySize * 1.8);
      const leading = median(gaps) || bodySize * 1.2;
      const regionLeft = Math.min(...region.map(left)); const regionRight = Math.max(...region.map(right));
      let current: WordParagraph | undefined;
      for (const [i, line] of region.entries()) {
        const previous = region[i - 1]; const gap = previous ? line.y - previous.y : Infinity;
        const value = lineText(line).trim(); const bold = line.spans.every(s => isBold(s));
        const heading = !listType(line) && value.length <= 120 && value.split(/\s+/).length <= 14 &&
          (line.size >= bodySize * 1.18 || (bold && line.size >= bodySize * 1.12 && gap >= leading * 1.25) || (bold && line.size >= bodySize && gap >= leading * 1.35 && value.length < 65 && /^[^a-z]*[A-Z][^a-z]*$/.test(value)))
          ? (line.size >= bodySize * 1.8 ? 'Title' : line.size >= bodySize * 1.18 ? 'Heading1' : 'Heading2') : undefined;
        const center = (left(line) + right(line)) / 2;
        const alignment = Math.abs(center - page.width / 2) <= 5 && left(line) > regionLeft + 12 ? 'center' :
          Math.abs(right(line) - regionRight) <= 3 && left(line) > regionLeft + 20 ? 'right' : 'left';
        const list = listType(line);
        const indent = previous ? left(line) - left(previous) : 0;
        const hangingContinuation = current?.list && indent >= -2 && indent <= bodySize * 3;
        // Bound ambiguous, uniformly styled extraction streams without making every line a paragraph.
        const longBlock = !!current && (current.lines.length >= 12 || (current.lines.length >= 6 && /[.!?]\s*$/.test(lineText(previous))));
        const split = !current || longBlock || !!heading || !!current.heading || !!list || gap > leading * 1.35 ||
          Math.abs(line.size - (previous?.size ?? line.size)) > 1 || (Math.abs(indent) > Math.max(4, bodySize * .5) && !hangingContinuation) ||
          (alignment !== current.alignment && !hangingContinuation);
        if (split) {
          current = { kind: 'paragraph', lines: [], x: left(line), y: line.y - line.size * (line.spans[0].ascent ?? .8),
            width: Math.max(1, right(line) - left(line)), leading, heading, alignment, list };
          blocks.push(current);
        }
        current!.lines.push(line); current!.x = Math.min(current!.x, left(line));
        current!.width = Math.max(current!.width, right(line) - current!.x);
      }
    }
    pending = [];
  };
  for (let index = 0; index < page.lines.length;) {
    const table = ruledTableAt(page, index) ?? tableAt(page, index);
    if (table) { flush(); blocks.push(table); index += table.lines.length; }
    else pending.push(page.lines[index++]);
  }
  flush();
  return { blocks, bodySize, regions: regionCount };
}

/** Only repeated edge content on multiple pages becomes a real Word header/footer. */
export function markRunningMatter(pages: { height: number; blocks: WordBlock[] }[]) {
  const key = (p: WordParagraph) => p.lines.map(lineText).join(' ').trim().replace(/\d+/g, '#');
  const counts = new Map<string, Set<number>>();
  pages.forEach((page, index) => page.blocks.forEach(block => {
    if (block.kind !== 'paragraph' || block.heading || (block.y > page.height * .07 && block.y < page.height * .93)) return;
    const value = `${block.y < page.height / 2 ? 'header' : 'footer'}:${key(block)}`;
    if (!counts.has(value)) counts.set(value, new Set()); counts.get(value)!.add(index);
  }));
  for (const page of pages) for (const block of page.blocks) {
    if (block.kind !== 'paragraph' || block.heading) continue;
    const role = block.y < page.height * .07 ? 'header' : block.y > page.height * .93 ? 'footer' : undefined;
    if (role && (counts.get(`${role}:${key(block)}`)?.size ?? 0) >= 2) block.role = role;
  }
}

export function wordFont(name: string): { family: string; substitution?: string } {
  const clean = name.replace(/^[A-Z]{6}\+/, '').replace(/[,\-]?(BoldItalic|BoldOblique|Bold|Italic|Oblique|Regular|Semibold|Light|Medium)(PSMT|MT)?$/i, '').replace(/(PSMT|MT)$/i, '');
  const compact = clean.replace(/[\s_-]/g, '').toLowerCase();
  const known: Record<string, string> = { arial: 'Arial', arialmt: 'Arial', calibri: 'Calibri', cambria: 'Cambria', verdana: 'Verdana',
    timesroman: 'Times New Roman', timesnewroman: 'Times New Roman', timesnewromanps: 'Times New Roman', couriernew: 'Courier New', couriernewps: 'Courier New', georgia: 'Georgia', tahoma: 'Tahoma',
    helvetica: 'Arial', helveticaneue: 'Arial', times: 'Times New Roman', courier: 'Courier New', 'sansserif': 'Arial', serif: 'Times New Roman', monospace: 'Courier New' };
  const family = known[compact] ?? (clean || 'Arial');
  return { family, substitution: /^(helvetica|times|courier|sansserif|serif|monospace)$/.test(compact) ? `${name} → ${family}` : undefined };
}
