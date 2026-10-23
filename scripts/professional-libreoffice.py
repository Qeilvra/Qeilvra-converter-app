"""Run a workspace-local LibreOffice evaluation without installing a service."""
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

root = Path('.tools/professional-conversion').resolve()
positional = [argument for argument in sys.argv[1:] if not argument.startswith('--')]
binary = Path(positional[0]).resolve() if positional else Path('.tools/office-evaluation/libreoffice/SourceDir/LibreOffice/program/soffice.com').resolve()
output = root / ('libreoffice-import' if '--pdf-import' in sys.argv else 'libreoffice')
output.mkdir(exist_ok=True)
records = []
for source in sorted((root / 'sources').glob('*.pdf' if '--pdf-import' in sys.argv else '*.docx')):
    sample = next((a.split('=', 1)[1] for a in sys.argv if a.startswith('--sample=')), None)
    if sample and source.name != sample:
        continue
    profile = output / ('profile-' + source.stem)
    args = [str(binary), '--headless', '--nologo', '--nodefault', '--norestore', '--nofirststartwizard', '-env:UserInstallation=' + profile.as_uri()]
    if '--pdf-import' in sys.argv:
        args += ['--infilter=writer_pdf_import', '--convert-to', 'docx:Office Open XML Text']
    else:
        args += ['--convert-to', 'pdf:writer_pdf_Export']
    args += ['--outdir', str(output), str(source)]
    start = time.perf_counter()
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=120)
        target = output / (source.stem + ('.docx' if '--pdf-import' in sys.argv else '.pdf'))
        records.append({'name': source.name, 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'seconds': time.perf_counter()-start,
                        'exit': result.returncode, 'output_exists': target.exists(), 'stdout': result.stdout, 'stderr': result.stderr,
                        'outputSha256': hashlib.sha256(target.read_bytes()).hexdigest() if target.exists() else None})
        print(source.name, records[-1], flush=True)
    except Exception as error:
        records.append({'name': source.name, 'error': str(error)}); print(source.name, error, flush=True)
(output / 'generation.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
