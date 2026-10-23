import * as d from 'docx';
import { isBold, isItalic, lineText, markRunningMatter, reconstructWordPage, wordFont } from './pdfWordLayout';
import type { WordPage, WordParagraph, WordSpan } from './pdfWordLayout';

export type WordImage = { data: Uint8Array; x: number; y: number; width: number; height: number };
export type WordSourcePage = WordPage & { images: WordImage[] };
const twips = (points: number) => Math.round(points * 20);

export async function exportEditableWord(pages: WordSourcePage[]) {
  const reconstructed = pages.map(page => ({ ...page, ...reconstructWordPage(page) }));
  markRunningMatter(reconstructed);
  const warnings = new Set<string>(['Font binaries are not embedded; the opening office application must supply them.',
    'Paragraphs, headings and tables are inferred from geometry. Long uniform text uses bounded blocks when paragraph boundaries are uncertain. Ruled rectangular merged cells are supported; complex vectors, clipping, unruled merges and bidirectional layouts may differ.']);
  const fontChoices = new Map<string, string>();
  pages.forEach((page, index) => page.warnings?.forEach(warning => warnings.add(`Page ${index + 1}: ${warning}`)));
  const fontCache = new Map<string, string>();
  const canvas = document.createElement('canvas'); const measure = canvas.getContext('2d')!;
  const familyFor = (span: WordSpan) => {
    const known = fontCache.get(span.font); if (known) return known;
    const choice = wordFont(span.font);
    let family = choice.family;
    // Check actual installed glyph metrics. FontFaceSet.check alone returns true for nonexistent families.
    const probe = 'mmmmmmmmwwwwWWWWiiii1234567890';
    const available = ['monospace', 'serif', 'sans-serif'].some(fallback => {
      measure.font = `24px ${fallback}`; const base = measure.measureText(probe).width;
      measure.font = `24px "${family.replace(/["\\]/g, '')}",${fallback}`;
      return Math.abs(measure.measureText(probe).width - base) > .01;
    });
    if (!available) { const fallback = /mono|courier/i.test(family) ? 'Courier New' : /times|serif|cambria|georgia/i.test(family) ? 'Times New Roman' : 'Arial'; warnings.add(`Unavailable font ${family}; substituted ${fallback}.`); family = fallback; }
    if (choice.substitution) warnings.add(`Font substitution: ${choice.substitution}.`);
    fontChoices.set(span.font, family); fontCache.set(span.font, family); return family;
  };
  const runs = (spans: WordSpan[], strip = 0) => {
    const children: d.TextRun[] = [];
    for (const [index, span] of spans.entries()) {
      const previous = spans[index - 1];
      const space = previous && span.x - previous.x - previous.width > span.size * .15 && !/\s$/.test(previous.text) && !/^\s/.test(span.text) ? ' ' : '';
      const value = `${space}${span.text}`; const text = value.slice(strip); strip = Math.max(0, strip - value.length);
      if (!text) continue;
      const family = familyFor(span);
      measure.font = `${isItalic(span) ? 'italic ' : ''}${isBold(span) ? 'bold ' : ''}${span.size}px "${family}"`;
      const measured = measure.measureText(span.text).width;
      const ratio = measured > 0 ? Math.round(span.width / measured * 100) : 100;
      children.push(new d.TextRun({ text, font: { ascii: family, hAnsi: family, eastAsia: family, cs: family },
        size: Math.max(2, Math.round(span.size * 2)), bold: isBold(span), italics: isItalic(span),
        color: span.color ?? '000000', underline: span.underline ? { type: d.UnderlineType.SINGLE } : undefined,
        scale: ratio >= 60 && ratio <= 150 && Math.abs(100 - ratio) > 3 ? ratio : undefined }));
    }
    return children;
  };
  const numbering: d.INumberingOptions['config'][number][] = [];
  const paragraph = (block: WordParagraph) => {
    const children: d.TextRun[] = [];
    let markerLength = 0; let reference: string | undefined; let listAdvance = 0;
    if (block.list) {
      const value = lineText(block.lines[0]);
      const match = value.match(/^\s*([\u2022\u25cf\u25a0\u25e6\u2023\u2043\u2219\uf0b7-]|\d{1,3}[.)])\s+/u);
      if (match) {
        markerLength = match[0].length; reference = `source-list-${numbering.length}`;
        const first = block.lines[0].spans[0];
        measure.font = `${isItalic(first) ? 'italic ' : ''}${isBold(first) ? 'bold ' : ''}${first.size}px "${familyFor(first)}"`;
        listAdvance = measure.measureText(match[0]).width;
        numbering.push({ reference, levels: [{ level: 0, format: block.list === 'bullet' ? d.LevelFormat.BULLET : d.LevelFormat.DECIMAL,
          text: block.list === 'bullet' ? match[1] : `%1${match[1].at(-1)}`, start: block.list === 'number' ? parseInt(match[1]) : 1,
          suffix: d.LevelSuffix.TAB, alignment: d.AlignmentType.LEFT,
          style: { paragraph: { indent: { left: twips(listAdvance), hanging: twips(listAdvance) } }, run: { font: familyFor(first), size: Math.round(first.size * 2) } } }] });
      }
    }
    for (const [index, line] of block.lines.entries()) {
      if (index) children.push(new d.TextRun({ break: 1 }));
      const continuationSpaces = index && block.list && Math.abs(line.spans[0].x - block.x) <= 2 ? (line.spans[0].text.match(/^\s+/)?.[0].length ?? 0) : 0;
      children.push(...runs(line.spans, index ? continuationSpaces : markerLength));
    }
    const leading = block.lines.length > 1 ? (block.lines.at(-1)!.y - block.lines[0].y) / (block.lines.length - 1) : Math.max(block.leading, block.lines[0].size * 1.12);
    return new d.Paragraph({ children, heading: block.heading as typeof d.HeadingLevel[keyof typeof d.HeadingLevel] | undefined,
      alignment: block.alignment, numbering: reference ? { reference, level: 0 } : undefined,
      indent: reference ? undefined : { left: 0, right: 0, firstLine: 0 },
      spacing: { before: 0, after: 0, line: twips(leading), lineRule: d.LineRuleType.EXACT },
      contextualSpacing: false, widowControl: false, keepNext: false, keepLines: true,
      frame: { type: 'absolute', position: { x: twips(block.x), y: twips(Math.max(0, block.lines[0].y - leading + block.lines[0].size * .26)) },
        width: twips(block.width + 3), anchor: { horizontal: d.FrameAnchorType.PAGE, vertical: d.FrameAnchorType.PAGE },
        wrap: d.FrameWrap.NONE, space: { horizontal: 0, vertical: 0 }, anchorLock: true } });
  };
  const anchor = () => new d.Paragraph({ children: [], spacing: { before: 0, after: 0, line: 20, lineRule: d.LineRuleType.EXACT }, run: { size: 2 } });
  const sections = await Promise.all(reconstructed.map(async page => {
    const children: (d.Paragraph | d.Table)[] = [];
    const headers: d.Paragraph[] = []; const footers: d.Paragraph[] = [];
    for (const block of page.blocks) {
      if (block.kind === 'paragraph') {
        (block.role === 'header' ? headers : block.role === 'footer' ? footers : children).push(paragraph(block));
      } else {
        const border = { style: block.borders ? d.BorderStyle.SINGLE : d.BorderStyle.NONE, size: block.borders ? Math.max(1, Math.round((block.borderSize ?? .75) * 8)) : 0, color: block.borderColor ?? '000000' };
        const widths = block.widths.map(twips);
        if (block.grid) {
          const xs = [block.x]; block.widths.forEach(w => xs.push(xs.at(-1)! + w));
          // Word positions floating tables relative to the first cell's text margin.
          const firstCell = block.grid[0].cells[0];
          const tablePad = firstCell?.lines[0] ? Math.max(0, firstCell.lines[0].spans[0].x - xs[firstCell.column]) : 0;
          children.push(new d.Table({ width: { size: widths.reduce((a, b) => a + b, 0), type: d.WidthType.DXA }, columnWidths: widths,
            layout: d.TableLayoutType.FIXED, margins: { top: 0, bottom: 0, left: 0, right: 0 },
            borders: { top: border, bottom: border, left: border, right: border, insideHorizontal: border, insideVertical: border },
            float: { horizontalAnchor: d.TableAnchorType.PAGE, verticalAnchor: d.TableAnchorType.PAGE,
              absoluteHorizontalPosition: twips(block.x + tablePad), absoluteVerticalPosition: twips(block.y), topFromText: 0, bottomFromText: 0, leftFromText: 0, rightFromText: 0 },
            rows: block.grid.map(row => {
              // Word uses the largest top cell margin across a row. A common line box
              // keeps neighboring single-line and wrapped cells on the same baseline.
              const rowLeading = Math.max(...row.cells.map(cell => cell.lines.length > 1
                ? (cell.lines.at(-1)!.y - cell.lines[0].y) / (cell.lines.length - 1)
                : (cell.lines[0]?.size ?? block.lines[0].size) * 1.2));
              return new d.TableRow({ cantSplit: true, height: { value: twips(row.height), rule: d.HeightRule.EXACT },
              children: row.cells.map(cell => {
                const first = cell.lines[0]; const size = first?.size ?? block.lines[0].size;
                const leading = rowLeading;
                const top = first ? Math.max(0, first.y - row.y - leading + size * .26 - (block.borderSize ?? .75) / 2) : 0;
                const pad = first ? Math.max(0, first.spans[0].x - xs[cell.column]) : 0;
                const children: d.TextRun[] = [];
                cell.lines.forEach((line, i) => { if (i) children.push(new d.TextRun({ break: 1 })); children.push(...runs(line.spans)); });
                return new d.TableCell({ columnSpan: cell.columnSpan, rowSpan: cell.rowSpan,
                  width: { size: widths.slice(cell.column, cell.column + cell.columnSpan).reduce((a, b) => a + b, 0), type: d.WidthType.DXA },
                  margins: { top: twips(top), bottom: 0, left: twips(pad), right: 0 }, verticalAlign: d.VerticalAlign.TOP,
                  children: [new d.Paragraph({ children, spacing: { before: 0, after: 0, line: twips(leading), lineRule: d.LineRuleType.EXACT } })] });
              }) }); }) }));
          continue;
        }
        const pad = Math.max(0, block.rows[0][0][0].x - block.x);
        const cellTop = Math.max(0, block.lines[0].y - block.y - block.lines[0].size * 1.2 + block.lines[0].size * .26 - (block.borders ? (block.borderSize ?? .75) / 2 : 0));
        children.push(new d.Table({ width: { size: widths.reduce((a, b) => a + b, 0), type: d.WidthType.DXA }, columnWidths: widths,
          layout: d.TableLayoutType.FIXED, margins: { top: twips(cellTop), bottom: 0, left: twips(pad), right: 0 },
          borders: { top: border, bottom: border, left: border, right: border, insideHorizontal: border, insideVertical: border },
          float: { horizontalAnchor: d.TableAnchorType.PAGE, verticalAnchor: d.TableAnchorType.PAGE,
            absoluteHorizontalPosition: twips(block.x + pad), absoluteVerticalPosition: twips(block.y), topFromText: 0, bottomFromText: 0, leftFromText: 0, rightFromText: 0 },
          rows: block.rows.map((row, i) => new d.TableRow({ cantSplit: true, height: { value: twips(i + 1 < block.lines.length ? block.lines[i + 1].y - block.lines[i].y : block.lines.length > 1 ? block.lines[i].y - block.lines[i - 1].y : block.lines[i].size * 1.5), rule: d.HeightRule.EXACT },
            children: row.map((cell, column) => new d.TableCell({ width: { size: widths[column], type: d.WidthType.DXA },
              verticalAlign: d.VerticalAlign.TOP, children: [new d.Paragraph({ children: runs(cell), spacing: { before: 0, after: 0, line: twips(block.lines[i].size * 1.2), lineRule: d.LineRuleType.EXACT } })] })) })) }));
      }
    }
    const graphics = [...page.images];
    for (const rule of page.rules ?? []) {
      if (page.blocks.some(b => b.kind === 'table' && rule.y >= b.y - 2 && rule.y <= (b.grid ? b.grid.at(-1)!.y + b.grid.at(-1)!.height : b.lines.at(-1)!.y + b.lines[0].size) + 2 && rule.x >= b.x - 2 && rule.x <= b.x + b.widths.reduce((a, c) => a + c, 0) + 2)) continue;
      if (page.lines.some(l => l.spans.some(s => s.underline && Math.abs(rule.y - s.y) < s.size * .25 && rule.x <= s.x + 2 && rule.x + rule.width >= s.x + s.width - 2))) continue;
      const c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil((rule.width || rule.thickness) * 2)); c.height = Math.max(1, Math.ceil((rule.height || rule.thickness) * 2));
      if (c.width * c.height > 4_000_000) continue;
      const ctx = c.getContext('2d')!; ctx.fillStyle = `#${rule.color}`; ctx.fillRect(0, 0, c.width, c.height);
      const blob = awaitBlob(c);
      graphics.push({ data: await blob, x: rule.x, y: rule.y, width: Math.max(rule.width, rule.thickness), height: Math.max(rule.height, rule.thickness) });
      c.width = c.height = 0;
    }
    if (graphics.length) children.push(new d.Paragraph({ spacing: { before: 0, after: 0, line: 20, lineRule: d.LineRuleType.EXACT },
      children: graphics.map(image => new d.ImageRun({ data: image.data, type: 'png',
        transformation: { width: Math.max(1, image.width * 96 / 72), height: Math.max(1, image.height * 96 / 72) },
        floating: { horizontalPosition: { relative: d.HorizontalPositionRelativeFrom.PAGE, offset: Math.round(image.x * 12700) },
          verticalPosition: { relative: d.VerticalPositionRelativeFrom.PAGE, offset: Math.round(image.y * 12700) },
          wrap: { type: d.TextWrappingType.NONE }, allowOverlap: true, layoutInCell: false, lockAnchor: true } })) }));
    children.push(anchor());
    const body = page.blocks.filter((b): b is WordParagraph => b.kind === 'paragraph' && !b.role);
    const left = Math.max(0, Math.min(...page.lines.flatMap(l => l.spans.map(s => s.x))));
    const top = Math.max(0, Math.min(...body.map(b => b.y), page.height * .1));
    return { properties: { type: d.SectionType.NEXT_PAGE, page: { size: { width: twips(page.width), height: twips(page.height) },
      margin: { top: twips(top), left: twips(left), right: twips(Math.max(0, page.width - Math.max(...page.lines.flatMap(l => l.spans.map(s => s.x + s.width))))), bottom: 200, header: 0, footer: 0 } } },
      headers: { default: new d.Header({ children: headers.length ? headers : [anchor()] }) },
      footers: { default: new d.Footer({ children: footers.length ? footers : [anchor()] }) }, children };
  }));
  canvas.width = canvas.height = 0;
  const report = { version: 1, fonts: Object.fromEntries(fontChoices), warnings: [...warnings], pages: reconstructed.map(page => ({
    paragraphs: page.blocks.filter(b => b.kind === 'paragraph').length, headings: page.blocks.filter(b => b.kind === 'paragraph' && b.heading).length,
    tables: page.blocks.filter(b => b.kind === 'table').length, regions: page.regions })) };
  return d.Packer.toBlob(new d.Document({ sections, numbering: { config: numbering },
    styles: { default: { document: { run: { font: 'Arial', size: 22 }, paragraph: { spacing: { before: 0, after: 0 } } } } },
    description: 'Editable PDF reconstruction. See FileFlowConversionReport in document custom properties for font substitutions and limitations.',
    customProperties: [{ name: 'FileFlowConversionReport', value: JSON.stringify(report) }] }));
}

async function awaitBlob(canvas: HTMLCanvasElement) {
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('Rule export failed.')), 'image/png'));
  return new Uint8Array(await blob.arrayBuffer());
}
