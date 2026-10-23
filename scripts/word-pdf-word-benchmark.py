"""docx2pdf desktop benchmark only, isolated from any existing user Word instance."""
import json
import hashlib
import os
import sys
import time
from pathlib import Path

package = Path('.tools/office-evaluation/docx2pdf').resolve()
sys.path[:0] = [str(package), str(package/'win32'), str(package/'win32/lib')]
dll = os.add_dll_directory(str(package/'pywin32_system32'))
import win32com.client
import pythoncom
from docx2pdf import convert

root = Path('.tools/word-pdf-rendering').resolve()
output = root/'docx2pdf'; output.mkdir(exist_ok=True)
pythoncom.CoInitialize()
original = win32com.client.Dispatch
# docx2pdf normally uses Dispatch, which can attach to the user's Word session.
# Give the wrapper only our separately created instance.
records=[]
try:
    for source in sorted((root/'sources').glob('*.docx')):
        word = win32com.client.DispatchEx('Word.Application')
        word.Visible=False; word.DisplayAlerts=0; word.AutomationSecurity=3
        word.Options.UpdateLinksAtOpen=False
        win32com.client.Dispatch = lambda name, *a, **kw: word if name == 'Word.Application' else original(name,*a,**kw)
        start=time.perf_counter();target=output/(source.stem+'.pdf')
        source_hash=hashlib.sha256(source.read_bytes()).hexdigest()
        record={'name':source.stem,'engine':'Installed Microsoft Word via docx2pdf 0.1.8'}
        try:
            convert(str(source),str(target),keep_active=True)
            record.update({'seconds':time.perf_counter()-start,'outputBytes':target.stat().st_size})
            assert source_hash==hashlib.sha256(source.read_bytes()).hexdigest()
        except Exception as error: record['error']=str(error)
        finally:
            win32com.client.Dispatch=original
            try: word.Quit()
            except Exception as error: record['shutdownNotice']=str(error)
        records.append(record)
        print(records[-1],flush=True)
        (output/'results.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
finally:
    win32com.client.Dispatch=original
    pythoncom.CoUninitialize(); dll.close()
(output/'results.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
