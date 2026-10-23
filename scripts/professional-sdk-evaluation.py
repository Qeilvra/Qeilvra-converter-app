"""Development-only PyMuPDF Office restricted evaluation. Never a runtime dependency.

Run with PYTHONPATH=.tools/office-evaluation/office-sdk, TEMP/TMP on D:.
Python network connections are blocked. No credentials or volume license are used.
"""
import json
import socket
import time
from pathlib import Path


def no_network(*args, **kwargs):
    raise RuntimeError('Network disabled for local SDK evaluation')


socket.create_connection = no_network
socket.socket.connect = no_network
import pymupdf
import pymupdf.office

root = Path('.tools/professional-conversion')
output = root / 'pymupdf-office'; output.mkdir(exist_ok=True)
pymupdf.office.unlock()  # Vendor's restricted, first-three-page evaluation; no key.
records = []
for name in ['office-report', 'supplied-cv']:
    source = root / 'sources' / (name + '.docx')
    target = output / (name + '.pdf')
    start = time.perf_counter()
    record = {'name': name}
    try:
        # Exercise the documented API first, recording any failure rather than hiding it.
        try:
            pymupdf.office.to_pdf(str(source), str(target))
            record['method'] = 'pymupdf.office.to_pdf'
        except Exception as error:
            record['direct_export_error'] = str(error)
            try:
                document = pymupdf.open(source)
            except Exception as path_error:
                record['path_loader_error'] = str(path_error)
                document = pymupdf.open(stream=source.read_bytes(), filetype='docx')
            with document:
                record['evaluated_pages'] = len(document)
                target.write_bytes(document.convert_to_pdf())
            record['method'] = 'pymupdf.open Office loader + convert_to_pdf'
        with pymupdf.open(target) as pdf:
            record['output_pages'] = len(pdf)
        record['status'] = 'generated'
    except Exception as error:
        record['status'] = 'failed'; record['error'] = str(error)
        if target.exists() and target.stat().st_size == 0:
            target.unlink()
    record['seconds'] = time.perf_counter() - start
    records.append(record); print(record, flush=True)
(output / 'generation.json').write_text(json.dumps({'version': pymupdf.VersionBind, 'restriction': 'Only the first three pages are available without a trial/full license. Evaluation only; no production dependency.', 'results': records}, indent=2), encoding='utf-8')
