import type { PDFPageProxy } from 'pdfjs-dist';
import type { WordSpan, WordRule } from './pdfWordLayout';

/** Recover paint metadata that getTextContent intentionally omits. No page rasterization. */
export async function readWordPaint(page: PDFPageProxy, spans: WordSpan[]) {
  const { OPS, Util } = await import('pdfjs-dist');
  const operators = await page.getOperatorList(); const viewport = page.getViewport({ scale: 1 });
  const rules: WordRule[] = [];
  const paint: Array<{ text: string; color: string; width: number }> = [];
  let state = { matrix: [1, 0, 0, 1, 0, 0], fill: '000000', stroke: '000000', thickness: 1 };
  const stack: typeof state[] = [];
  let unsupportedPaths = 0;
  const hex = (value: unknown) => typeof value === 'string' && /^#[\da-f]{6}$/i.test(value) ? value.slice(1).toUpperCase() : '000000';
  for (let i = 0; i < operators.fnArray.length; i++) {
    const op = operators.fnArray[i]; const args = operators.argsArray[i];
    if (op === OPS.save) stack.push({ ...state, matrix: [...state.matrix] });
    else if (op === OPS.restore) state = stack.pop() ?? state;
    else if (op === OPS.transform) state.matrix = Util.transform(state.matrix, args);
    else if (op === OPS.setFillRGBColor) state.fill = hex(args[0]);
    else if (op === OPS.setStrokeRGBColor) state.stroke = hex(args[0]);
    else if (op === OPS.setLineWidth) state.thickness = args[0];
    else if (op === OPS.showText || op === OPS.showSpacedText || op === OPS.nextLineShowText) {
      const glyphs = args[0];
      if (Array.isArray(glyphs)) for (const glyph of glyphs) if (glyph && typeof glyph === 'object' && typeof glyph.unicode === 'string') {
        const text = glyph.unicode.normalize('NFKC');
        if (text) paint.push({ text, color: state.fill, width: Math.max(.001, Number(glyph.width) || 1) });
      }
    } else if (op === OPS.constructPath && args[2]) {
      const bounds = args[2] as number[];
      const matrix = Util.transform(viewport.transform, state.matrix);
      const a = [bounds[0], bounds[1]]; Util.applyTransform(a, matrix);
      const b = [bounds[2], bounds[3]]; Util.applyTransform(b, matrix);
      const width = Math.abs(b[0] - a[0]); const height = Math.abs(b[1] - a[1]);
      // Only straight rules. Curves, diagonals, clipping and arbitrary bounding boxes are not invented as borders.
      if ((width > 3 && height < .3) || (height > 3 && width < .3)) {
        if (rules.length >= 2000) throw new Error('PDF rules exceed the safe local conversion limit.');
        rules.push({ x: Math.min(a[0], b[0]), y: Math.min(a[1], b[1]), width, height,
          color: state.stroke, thickness: Math.max(.1, state.thickness * Math.hypot(state.matrix[0], state.matrix[1])) });
      } else unsupportedPaths++;
    }
  }
  const raw = paint.map(p => p.text).join('');
  const rawColors = paint.flatMap(p => p.text.split('').map(() => p.color));
  const rawWidths = paint.flatMap(p => p.text.split('').map(() => p.width / p.text.length));
  const offsets: number[] = []; let source = '';
  for (let i = 0; i < raw.length; i++) if (!/\s/.test(raw[i])) { source += raw[i]; offsets.push(i); }
  let cursor = 0; let unmatched = 0; const styled: WordSpan[] = [];
  for (const span of spans) {
    span.underline = rules.some(r => r.height < .3 && r.y - span.y >= -.5 && r.y - span.y <= span.size * .25 && r.x <= span.x + 2 && r.x + r.width >= span.x + span.width - 2);
    const value = span.text.normalize('NFKC').replace(/\s/g, '');
    const at = source.indexOf(value, cursor);
    if (at >= cursor && at < cursor + 128) {
      const start = offsets[at], end = offsets[at + value.length - 1] + 1;
      span.color = rawColors[start] ?? '000000'; cursor = at + value.length;
      const original = raw.slice(start, end);
      // getTextContent collapses repeated source spaces. Restore only evidence present in glyph operators.
      if ((original.match(/\s/g)?.length ?? 0) > (span.text.match(/\s/g)?.length ?? 0)) span.text = original;
      if (new Set(rawColors.slice(start, end)).size > 1) {
        // PDF.js can merge differently painted text into one TextItem. Split by actual glyph paint.
        const total = rawWidths.slice(start, end).reduce((a, b) => a + b, 0); let weight = 0;
        for (let from = start; from < end;) {
          let to = from + 1; while (to < end && rawColors[to] === rawColors[from]) to++;
          const width = rawWidths.slice(from, to).reduce((a, b) => a + b, 0);
          styled.push({ ...span, text: raw.slice(from, to), color: rawColors[from], x: span.x + span.width * weight / total, width: span.width * width / total });
          weight += width; from = to;
        }
        continue;
      }
    }
    else unmatched++;
    styled.push(span);
  }
  spans.length = 0; for (const span of styled) spans.push(span);
  return { rules, unmatched, unsupportedPaths };
}
