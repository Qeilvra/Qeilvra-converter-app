"""Desktop-only LibreOffice benchmark with sampled process-tree memory, not an Android engine."""
import json
import os
import subprocess
import time
from pathlib import Path
import psutil

root = Path('.tools/word-pdf-rendering').resolve()
output = root / 'libreoffice'; output.mkdir(exist_ok=True)
binary = Path('.tools/office-evaluation/libreoffice/SourceDir/LibreOffice/program/soffice.com').resolve()
records = []
for source in sorted((root / 'sources').glob('*.docx')):
    profile = output / ('profile-' + source.stem)
    start = time.perf_counter()
    args = [str(binary), '--headless', '--nologo', '--nodefault', '--norestore', '--nofirststartwizard',
            '-env:UserInstallation=' + profile.as_uri(), '--convert-to', 'pdf:writer_pdf_Export', '--outdir', str(output), str(source)]
    with (output / (source.stem+'.log')).open('w', encoding='utf-8') as log:
        process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT)
        peak = 0
        while process.poll() is None:
            try:
                parent=psutil.Process(process.pid)
                peak=max(peak,sum(p.memory_info().rss for p in [parent]+parent.children(recursive=True) if p.is_running()))
            except (psutil.NoSuchProcess,psutil.AccessDenied): pass
            if time.perf_counter()-start > 120:
                for p in parent.children(recursive=True): p.kill()
                process.kill(); raise RuntimeError('LibreOffice benchmark exceeded timeout')
            time.sleep(.1)
    record={'name':source.stem,'seconds':time.perf_counter()-start,'peakSampledTreeRssBytes':peak,'exit':process.returncode,'outputExists':(output/(source.stem+'.pdf')).exists()}
    records.append(record); print(record,flush=True)
    (output/'results.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
