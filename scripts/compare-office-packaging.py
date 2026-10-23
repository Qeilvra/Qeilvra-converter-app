"""Verify that packaging did not change the renderer's actual saved PDF output."""
from pathlib import Path
import json
import re
import fitz
import numpy as np

root = Path('.tools/word-pdf-rendering')
output = Path('.tools/production-packaging/browser')
paths = {
    'word': root / 'word-reference/complex-report.pdf',
    'previous_wasm': root / 'browser-after/complex-report.pdf',
    'packaged': output / 'warm.pdf',
    'offline': output / 'offline.pdf',
}

def inspect(path):
    with fitz.open(path) as doc:
        pages = []
        for page in doc:
            pix = page.get_pixmap(matrix=fitz.Matrix(1.25, 1.25), alpha=False)
            pages.append(np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3).copy())
        return {
            'pages': len(doc),
            'text': re.sub(r'\s+', '', ''.join(p.get_text() for p in doc)),
            'fonts': sorted({s['font'] for p in doc for b in p.get_text('dict')['blocks'] if 'lines' in b for line in b['lines'] for s in line['spans']}),
            'images': sum(len(p.get_image_info()) for p in doc),
            'hyperlinks': sum(len(p.get_links()) for p in doc),
            'dimensions': [[p.rect.width, p.rect.height] for p in doc],
        }, pages

records = {name: inspect(path) for name, path in paths.items()}
baseline, rasters = records['previous_wasm']
for name in ['packaged', 'offline']:
    data, images = records[name]
    assert data == baseline, (name, data, baseline)
    assert all(np.array_equal(a, b) for a, b in zip(rasters, images)), name
metrics = []
for i, (word, packaged, offline) in enumerate(zip(records['word'][1], records['packaged'][1], records['offline'][1])):
    assert word.shape == packaged.shape == offline.shape
    metrics.append({'page': i + 1, 'maeVsPreviousWasm': 0, 'maeVsWord': float(np.abs(word.astype(float) - packaged).mean()), 'offlinePixelEqual': True})
    composite = np.concatenate([word, packaged, offline], axis=1)
    fitz.Pixmap(fitz.csRGB, composite.shape[1], composite.shape[0], composite.tobytes(), False).save(output / f'comparison-page-{i+1}.png')
details = {name: {k: v for k, v in record[0].items() if k != 'text'} for name, record in records.items()}
result = {'result': 'PASS', 'dpi': 90, 'metrics': metrics, 'details': details, 'normalizedTextEqualToPreviousWasm': True}
(output / 'comparison.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
(output / 'comparison.html').write_text('<!doctype html><meta charset="utf-8"><title>Packaged office renderer</title><h1>Production build: DOCX to PDF</h1><p>Left: native Microsoft Word reference. Center: production static build, actual FileFlow button. Right: same app reloaded with browser networking disabled. Matching fonts were selected locally. Images rendered at 90 DPI; no resizing.</p>' + ''.join(f'<h2>Page {m["page"]}</h2><img style="width:100%" src="comparison-page-{m["page"]}.png">' for m in metrics), encoding='utf-8')
print(json.dumps(result, indent=2))
