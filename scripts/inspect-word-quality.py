"""Inspect actual OOXML and office-rendered PDFs; create images and measured comparisons.

Development-only dependencies are the existing python-docx, PyMuPDF and numpy environment.
No similarity number is a claim about general conversion accuracy.
"""
import collections
import difflib
import hashlib
import html
import json
import re
import sys
import unicodedata
from pathlib import Path
from urllib.parse import quote
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import fitz
import numpy as np

ROOT = Path('.tools/pdf-word-quality')
NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
W = '{' + NS['w'] + '}'


def normalized(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', text))


def structure(path):
    with ZipFile(path) as z:
        main = ET.fromstring(z.read('word/document.xml'))
        parts = [main] + [ET.fromstring(z.read(n)) for n in z.namelist() if re.match(r'word/(?:header|footer)\d*\.xml$', n)]
        paragraphs = [p for part in parts for p in part.findall('.//w:p', NS) if p.findall('.//w:t', NS)]
        result = {
            'text_paragraphs': len(paragraphs),
            'paragraph_text': [''.join(p.itertext()) for p in paragraphs],
            'runs': sum(len(p.findall('.//w:r', NS)) for p in paragraphs),
            'headings': [p.find('w:pPr/w:pStyle', NS).get(W+'val') for p in paragraphs if p.find('w:pPr/w:pStyle', NS) is not None and p.find('w:pPr/w:pStyle', NS).get(W+'val', '').startswith(('Heading', 'Title'))],
            'fonts': sorted({node.get(W+'ascii') for part in parts for node in part.findall('.//w:rFonts', NS) if node.get(W+'ascii')}),
            'sizes_halfpoints': sorted({node.get(W+'val') for part in parts for node in part.findall('.//w:sz', NS)}),
            'tables': len(main.findall('.//w:tbl', NS)),
            'frames': len(main.findall('.//w:framePr', NS)),
            'lists': len(main.findall('.//w:numPr', NS)),
            'bold_runs': sum(sum(node.get(W+'val') not in ('0', 'false', 'off') for node in part.findall('.//w:b', NS)) for part in parts),
            'italic_runs': sum(sum(node.get(W+'val') not in ('0', 'false', 'off') for node in part.findall('.//w:i', NS)) for part in parts),
            'underlined_runs': sum(len(part.findall('.//w:u', NS)) for part in parts),
            'colors': sorted({node.get(W+'val') for part in parts for node in part.findall('.//w:color', NS)}),
            'images': len([n for n in z.namelist() if n.startswith('word/media/') and not n.endswith('/')]),
            'headers': len(main.findall('.//w:headerReference', NS)),
            'footers': len(main.findall('.//w:footerReference', NS)),
            'page_sizes_twips': [{k.split('}')[-1]: v for k, v in node.attrib.items()} for node in main.findall('.//w:pgSz', NS)],
        }
        result['editable_text'] = ''.join(t.text or '' for part in parts for t in part.findall('.//w:t', NS))
        if 'docProps/custom.xml' in z.namelist():
            custom = ET.fromstring(z.read('docProps/custom.xml'))
            result['conversion_report'] = [json.loads(''.join(p.itertext())) for p in custom if p.get('name') == 'FileFlowConversionReport']
        return result


def comparison(source, rendered, directory):
    directory.mkdir(parents=True, exist_ok=True)
    source_text = ''.join(p.get_text() for p in source)
    output_text = ''.join(p.get_text() for p in rendered)
    a, b = collections.Counter(normalized(source_text)), collections.Counter(normalized(output_text))
    measurements = []; positions = []
    for index, page in enumerate(source):
        pix = page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False)
        pix.save(directory / f'page-{index+1}-source.png')
        if index >= len(rendered): continue
        def lines(p):
            return [(normalized(''.join(s['text'] for s in line['spans'])), line['spans'][0]['origin']) for block in p.get_text('dict')['blocks'] if block['type'] == 0 for line in block['lines'] if line['spans']]
        output_lines = {text: origin for text, origin in lines(rendered[index])}
        for text, origin in lines(page):
            if text in output_lines:
                target = output_lines[text]; positions.append((abs(origin[0]-target[0]), abs(origin[1]-target[1])))
        output = rendered[index].get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False)
        output.save(directory / f'page-{index+1}-word.png')
        if (pix.width, pix.height) != (output.width, output.height):
            measurements.append({'page': index+1, 'dimension_mismatch': True}); continue
        x = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3)
        y = np.frombuffer(output.samples, dtype=np.uint8).reshape(output.height, output.width, 3)
        difference = np.abs(x.astype(np.int16) - y.astype(np.int16))
        mask_a, mask_b = x.mean(axis=2) < 220, y.mean(axis=2) < 220
        union = (mask_a | mask_b).sum(); intersection = (mask_a & mask_b).sum()
        # Foreground overlap is intentionally strict: small font or baseline shifts lower it.
        measurements.append({'page': index+1, 'mean_absolute_rgb_error_0_255': round(float(difference.mean()), 4),
                             'foreground_intersection_over_union': round(float(intersection / union), 4) if union else 1})
        overlay = ((x.astype(np.uint16) + y.astype(np.uint16)) // 2).astype(np.uint8)
        fitz.Pixmap(fitz.csRGB, pix.width, pix.height, overlay.tobytes(), False).save(directory / f'page-{index+1}-overlay.png')
        heat = np.minimum(255, difference * 3).astype(np.uint8)
        fitz.Pixmap(fitz.csRGB, pix.width, pix.height, heat.tobytes(), False).save(directory / f'page-{index+1}-difference.png')
    return {'source_pages': len(source), 'rendered_pages': len(rendered),
            'missing_nonspace_characters': sum((a-b).values()), 'extra_nonspace_characters': sum((b-a).values()),
            'normalized_text_sequence_equal': normalized(source_text) == normalized(output_text),
            'character_sequence_similarity': round(difflib.SequenceMatcher(None, normalized(source_text), normalized(output_text), autojunk=False).ratio(), 5),
            'matched_source_lines': len(positions),
            'median_matched_line_left_error_pt': round(float(np.median([p[0] for p in positions])), 4) if positions else None,
            'median_matched_line_baseline_error_pt': round(float(np.median([p[1] for p in positions])), 4) if positions else None,
            'visual_measurements': measurements}


report = {'scope': 'Local fixtures only. Real Microsoft Word PDF renderings. Character counters detect losses/duplicates; sequence equality depends on PDF reading order. Pixel metrics are strict comparisons, not a general accuracy percentage.', 'samples': {}}
for source_path in sorted((ROOT / 'sources').glob('*.pdf')):
    source = fitz.open(source_path)
    sample = {'sha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
              'source_fonts': sorted({span['font'] for page in source for block in page.get_text('dict')['blocks'] if block['type'] == 0 for line in block['lines'] for span in line['spans']})}
    for phase in ['before', 'after']:
        path = ROOT / phase / (source_path.stem + '.docx')
        if not path.exists(): continue
        metrics = structure(path)
        pdf = path.with_suffix('.pdf')
        render_record = ROOT / phase / 'word-render.json'
        if render_record.exists():
            entries = json.loads(render_record.read_text(encoding='utf-8-sig'))
            if isinstance(entries, dict): entries = [entries]
            record = next((entry for entry in entries if entry['name'] == path.name), {})
            metrics['render_matches_docx_hash'] = record.get('sourceSha256') == hashlib.sha256(path.read_bytes()).hexdigest()
        if pdf.exists():
            with fitz.open(pdf) as rendered:
                metrics.update(comparison(source, rendered, ROOT / 'images' / source_path.stem / phase))
        source_chars = collections.Counter(normalized(''.join(p.get_text() for p in source)))
        word_chars = collections.Counter(normalized(metrics['editable_text']))
        # List labels are stored in w:numbering, not w:t; visual text counters include them.
        metrics['ooxml_missing_characters_including_numbering_labels'] = sum((source_chars-word_chars).values())
        sample[phase] = metrics
    report['samples'][source_path.stem] = sample
    source.close()
supplied = ROOT / 'before/supplied-cv.docx'
if supplied.exists():
    report['supplied_cv'] = structure(supplied)
    if supplied.with_suffix('.pdf').exists():
        with fitz.open(ROOT / 'sources/primary-cv.pdf') as source, fitz.open(supplied.with_suffix('.pdf')) as rendered:
            report['supplied_cv'].update(comparison(source, rendered, ROOT / 'images/primary-cv/supplied'))
(ROOT / 'comparison.json').write_text(json.dumps(report, ensure_ascii=True, indent=2), encoding='utf-8')
native_pdf = ROOT / 'android/native-layout.pdf'
if native_pdf.exists():
    with fitz.open(native_pdf) as native:
        assert len(native) == 2, 'Native writer fixture pagination changed'
        for index, page in enumerate(native):
            value = normalized(page.get_text())
            for column in ['LEFT', 'RIGHT']:
                for row in range(3): assert f'{column}columncontinuation{row}' in value, 'Native writer lost column text'
            page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False).save(ROOT / f'android/page-{index+1}-word.png')
        native_result = {'result': 'PASS', 'rendered_pages': len(native), 'renderer': 'Microsoft Word',
                         'scope': 'Actual compiled native Java layout/OOXML writer with synthetic positioned spans. No Android PDF extraction or device accuracy claim.'}
        (ROOT / 'android/native-inspection.json').write_text(json.dumps(native_result, indent=2), encoding='utf-8')

# A local, self-contained review page; image files stay beside it and no service is contacted.
cards = []
for name, sample in sorted(report['samples'].items(), key=lambda pair: (pair[0] != 'primary-cv', pair[0])):
    for number in range(1, sample['after'].get('source_pages', 0) + 1):
        directory = f'images/{quote(name)}/'
        options = [('after', 'Improved DOCX'), ('before', 'Baseline engine DOCX')]
        if name == 'primary-cv': options.append(('supplied', 'Supplied DOCX'))
        option_html = ''.join(f'<option value="{directory}{phase}/page-{number}-word.png">{label}</option>' for phase, label in options)
        cards.append(f'<article><h2>{html.escape(name)} · page {number}</h2><select onchange="this.parentNode.querySelector(\'.word\').src=this.value">{option_html}</select>'
                     f'<div class="compare"><img src="{directory}after/page-{number}-source.png" alt="Original PDF"><img class="word" src="{directory}after/page-{number}-word.png" alt="Microsoft Word rendering"></div>'
                     '<input type="range" value="50" min="0" max="100" aria-label="Comparison position" oninput="this.previousElementSibling.querySelector(\'.word\').style.clipPath=\'inset(0 \'+(100-this.value)+\'% 0 0)\'"></article>')
(ROOT / 'comparison.html').write_text('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>FileFlow conversion comparison</title>'
    '<style>body{font:16px system-ui;background:#eef1f5;color:#17263b;max-width:960px;margin:32px auto;padding:16px}article{background:white;padding:20px;margin:24px 0;border-radius:12px}h2{font-size:18px}select{font:inherit;padding:8px;margin-bottom:16px}.compare{position:relative}.compare img{width:100%;display:block}.compare .word{position:absolute;top:0;left:0;clip-path:inset(0 50% 0 0)}input{width:100%;margin-top:20px}</style>'
    '<h1>PDF and editable Word comparison</h1><p>Drag each slider to compare Word on the left with the original PDF on the right. Select a saved conversion to review the supplied, baseline or improved output. All images were rendered locally; these examples do not establish general conversion accuracy.</p>'
    + ''.join(cards), encoding='utf-8')
if '--assert' in sys.argv:
    checks = []
    for name, sample in report['samples'].items():
        before, after = sample['before'], sample['after']
        assert before.get('render_matches_docx_hash') and after.get('render_matches_docx_hash'), f'{name}: rendering is stale or missing'
        assert after.get('source_pages') == after.get('rendered_pages') and after.get('rendered_pages'), f'{name}: missing render or wrong page count'
        assert after['missing_nonspace_characters'] == 0 and after['extra_nonspace_characters'] == 0, f'{name}: text lost or duplicated'
        assert after['visual_measurements'], f'{name}: no visual measurements'
        assert sum(p['mean_absolute_rgb_error_0_255'] for p in after['visual_measurements']) < sum(p['mean_absolute_rgb_error_0_255'] for p in before['visual_measurements']), f'{name}: visual regression against baseline'
        checks.append(f'{name}: page count, rendered text, measured visual improvement')
    assert report['samples']['columns']['after']['tables'] == 0
    assert report['samples']['table-image']['after']['tables'] == 1
    assert report['samples']['table-image']['after']['images'] == 1
    assert len(report['samples']['synthetic-cv']['after']['headings']) >= 5
    assert report['samples']['primary-cv']['after']['headings'] == []
    assert 3 <= report['samples']['primary-cv']['after']['text_paragraphs'] <= 10
    assert report['samples']['typography']['after']['underlined_runs'] >= 1
    assert report['samples']['typography']['after']['italic_runs'] >= 1
    (ROOT / 'quality-gates.json').write_text(json.dumps({'result': 'PASS', 'checks': checks}, indent=2), encoding='utf-8')
for name, sample in report['samples'].items():
    print(name, {phase: {k: sample[phase].get(k) for k in ['text_paragraphs', 'headings', 'tables', 'source_pages', 'rendered_pages', 'missing_nonspace_characters', 'extra_nonspace_characters', 'visual_measurements']} for phase in ['before', 'after'] if phase in sample})
print('Saved report and comparison images to', ROOT)
