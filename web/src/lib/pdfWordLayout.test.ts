import { describe, expect, it } from 'vitest';
import { groupLines } from './localDocuments';
import { lineText, markRunningMatter, reconstructWordPage, wordFont } from './pdfWordLayout';
import type { WordSpan, WordPage, WordParagraph, WordRule } from './pdfWordLayout';

const span = (text: string, x: number, y: number, size = 11, font = 'Arial', width = text.length * size * .48): WordSpan => ({ text, x, y, size, font, width });
const page = (spans: WordSpan[]): WordPage => ({ width: 612, height: 792, lines: groupLines(spans) });
const paragraphs = (model: WordPage) => reconstructWordPage(model).blocks.filter((b): b is WordParagraph => b.kind === 'paragraph');

describe('editable Word geometry reconstruction', () => {
  it('reconstructs ruled merged cells, multiline text and empty cells from actual edges', () => {
    const model = page([span('Merged heading', 58, 122, 10), span('001234', 58, 155, 10), span('First line', 310, 155, 10),
      span('Continuation', 310, 170, 10), span('009876', 58, 217, 10)]);
    const h = (y: number): WordRule => ({ x: 50, y, width: 510, height: 0, color: '123456', thickness: .75 });
    const v = (x: number, y = 100, height = 130): WordRule => ({ x, y, width: 0, height, color: '123456', thickness: .75 });
    model.rules = [100, 135, 195, 230].map(h).concat([v(50), v(560), v(300, 135, 95)]);
    const blocks = reconstructWordPage(model).blocks;
    expect(blocks).toHaveLength(1); const block = blocks[0]; expect(block.kind).toBe('table');
    if (block.kind !== 'table') return;
    expect(block.grid?.map(r => r.cells.map(c => c.columnSpan))).toEqual([[2], [1, 1], [1, 1]]);
    expect(block.grid?.[1].cells[1].lines.map(lineText)).toEqual(['First line', 'Continuation']);
    expect(block.grid?.[2].cells[1].lines).toEqual([]);
    expect(block.widths).toEqual([250, 260]);
    // A partial horizontal boundary proves a vertical merge in the left column.
    model.rules = [h(100), h(135), { ...h(195), x: 300, width: 260 }, h(230), v(50), v(560), v(300, 135, 95)];
    const merged = reconstructWordPage(model).blocks[0];
    if (merged.kind !== 'table') throw new Error('Missing table');
    expect(merged.grid?.[1].cells[0].rowSpan).toBe(2);
    expect(merged.grid?.[1].cells[0].lines.map(lineText)).toEqual(['001234', '009876']);
  });
  it('joins continuation lines while preserving real paragraph and heading boundaries', () => {
    const blocks = paragraphs(page([span('PROFILE', 50, 60, 16, 'Arial-Bold'), span('The paragraph begins.', 50, 90),
      span('It continues on a second line.', 50, 104), span('Another paragraph.', 50, 140), span('Its continuation.', 50, 154)]));
    expect(blocks.map(b => b.lines.length)).toEqual([1, 2, 2]);
    expect(blocks[0].heading).toBe('Heading1'); expect(blocks[1].heading).toBeUndefined();
  });
  it('keeps long prose columns separate, in column reading order, without inventing a table', () => {
    const spans: WordSpan[] = [];
    for (let i = 0; i < 4; i++) { spans.push(span(`LEFT continuation ${i} with a substantial phrase.`, 50, 90 + i * 14, 10, 'Arial', 210)); spans.push(span(`RIGHT continuation ${i} with a substantial phrase.`, 330, 90 + i * 14, 10, 'Arial', 220)); }
    const blocks = reconstructWordPage(page(spans)).blocks;
    expect(blocks).toHaveLength(2); expect(blocks.every(b => b.kind === 'paragraph')).toBe(true);
    expect(blocks[0].lines.every(l => lineText(l).startsWith('LEFT'))).toBe(true);
    expect(blocks[1].lines.every(l => lineText(l).startsWith('RIGHT'))).toBe(true);
  });
  it('requires numeric/short aligned rows or rules for a Word table and preserves exact cell text', () => {
    const model = page([span('ID', 50, 80), span('Value', 300, 80), span('001234', 50, 106), span('$1,234.50', 300, 106), span('12345678901234567890', 50, 132), span('=SUM(A1:A2)', 300, 132)]);
    const block = reconstructWordPage(model).blocks[0];
    expect(block.kind).toBe('table');
    if (block.kind === 'table') { expect(block.rows[1][0][0].text).toBe('001234'); expect(block.borders).toBe(false); }
  });
  it('separates staggered columns even when their PDF baselines never align', () => {
    const spans = Array.from({ length: 4 }, (_, i) => [span(`LEFT text ${i}`, 50, 90 + i * 14, 10, 'Arial', 210), span(`RIGHT text ${i}`, 330, 96 + i * 14, 10, 'Arial', 210)]).flat();
    const blocks = paragraphs(page(spans));
    expect(blocks).toHaveLength(2);
    expect(blocks[0].lines.every(l => lineText(l).startsWith('LEFT'))).toBe(true);
    expect(blocks[1].lines.every(l => lineText(l).startsWith('RIGHT'))).toBe(true);
  });
  it('groups wrapped bullet lines and keeps separate list items', () => {
    const blocks = paragraphs(page([span('A sufficiently long body text establishes the font.', 50, 50), span('• First item', 50, 90), span('continued achievement', 62, 104), span('• Second item', 50, 130)]));
    expect(blocks.slice(1).map(b => [b.list, b.lines.length])).toEqual([['bullet', 2], ['bullet', 1]]);
  });
  it('bounds ambiguous uniform streams without one paragraph per PDF line or invented headings', () => {
    const blocks = paragraphs(page(Array.from({ length: 30 }, (_, i) => span(`Uniform source line ${i} without structural evidence`, 50, 50 + i * 14))));
    expect(blocks.length).toBe(3); expect(blocks.every(b => b.lines.length <= 12 && !b.heading)).toBe(true);
  });
  it('only promotes repeated edge matter to headers and footers', () => {
    const models = [1, 2].map(i => ({ height: 792, blocks: paragraphs(page([span('Shared header', 50, 20, 9), span(`Chapter body ${i}`, 50, 100), span(`Page ${i}`, 500, 775, 9)])) }));
    markRunningMatter(models);
    expect(models[0].blocks.map(b => b.role)).toEqual(['header', undefined, 'footer']);
    const lone = [{ height: 792, blocks: paragraphs(page([span('One document', 50, 20)])) }]; markRunningMatter(lone);
    expect(lone[0].blocks[0].role).toBeUndefined();
  });
  it('normalizes font subset/style names and explicitly reports standard-font substitutions', () => {
    expect(wordFont('ABCDEF+Arial-BoldMT').family).toBe('Arial');
    expect(wordFont('TimesNewRomanPS-ItalicMT').family).toBe('Times New Roman');
    expect(wordFont('Helvetica').substitution).toContain('Arial');
    expect(wordFont('Cambria').family).toBe('Cambria');
  });
});
