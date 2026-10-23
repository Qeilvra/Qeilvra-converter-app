"""Compare the selected local reconstruction with the repository's optional desktop engine.

This is a development benchmark, not a runtime or Android dependency. No network is used.
"""
import json
import time
from pathlib import Path
from docx import Document
from pdf2docx import Converter

directory = Path('.tools/backend-free-evidence/chromium-desktop')
output = directory / 'optional-pdf2docx.docx'
start = time.perf_counter()
converter = Converter(str(directory / 'source.pdf'))
try:
    converter.convert(str(output))
finally:
    converter.close()
results = {}
for name, path in [('browser-local', directory / 'editable.docx'), ('optional-desktop-pdf2docx', output)]:
    document = Document(path)
    text = '\n'.join([p.text for p in document.paragraphs] + [cell.text for table in document.tables for row in table.rows for cell in row.cells])
    results[name] = {
        'paragraphs': len(document.paragraphs), 'tables': len(document.tables),
        'images': len(document.element.xpath('.//a:blip')), 'sections': len(document.sections),
        'identifiers_preserved': all(value in text for value in ['001234', '12345678901234567890']),
        'editable_sentence': 'This sentence must remain editable' in text,
    }
results['limits'] = 'Structural inspection on the same synthetic two-page PDF. These counts do not measure rendered layout accuracy. The desktop engine is not Android compatible and is not used by the app.'
results['desktop_elapsed_seconds'] = round(time.perf_counter() - start, 3)
print(json.dumps(results, indent=2))
(directory / 'engine-comparison.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
