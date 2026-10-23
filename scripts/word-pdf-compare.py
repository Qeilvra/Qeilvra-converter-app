"""Inspect actual PDFs and render Word/reference, previous, and replacement comparisons."""
from pathlib import Path
from collections import Counter
import html
import json
import re
import unicodedata
import fitz
import numpy as np

ROOT = Path('.tools/word-pdf-rendering')
def normalized(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', text))
def inspect(path):
    if not path.exists(): return None
    with fitz.open(path) as d:
        text = ''.join(p.get_text() for p in d)
        return {'pages':len(d), 'text':text, 'characters':len(normalized(text)),
                'fonts':sorted({f[3] for p in d for f in p.get_fonts()}),
                'usedFonts':sorted({span['font'] for p in d for block in p.get_text('dict')['blocks'] if 'lines' in block for line in block['lines'] for span in line['spans']}),
                'images':sum(len(p.get_image_info()) for p in d), 'hyperlinks':sum(len(p.get_links()) for p in d),
                'dimensions':[[p.rect.width,p.rect.height] for p in d]}
def raster(page):
    pix = page.get_pixmap(matrix=fitz.Matrix(1.25,1.25), alpha=False)
    return np.frombuffer(pix.samples,dtype=np.uint8).reshape(pix.height,pix.width,3).copy()
def canvas(image,h,w):
    out=np.full((h,w,3),255,np.uint8)
    if image is not None: out[:image.shape[0],:image.shape[1]]=image
    return out
def compare(name,folder):
    reference=ROOT/'word-reference'/(name+'.pdf'); candidate=ROOT/folder/(name+'.pdf')
    details=inspect(candidate)
    if details is None: return {'status':'No PDF generated'}
    a=inspect(reference); ca,cb=Counter(normalized(a['text'])),Counter(normalized(details['text']))
    details.update({'missingCharacters':sum((ca-cb).values()),'extraCharacters':sum((cb-ca).values()),'textSequenceEqual':normalized(a['text'])==normalized(details['text'])})
    details.pop('text'); metrics=[]
    with fitz.open(reference) as ref, fitz.open(candidate) as new:
        for i in range(max(len(ref),len(new))):
            a=raster(ref[i]) if i<len(ref) else None; b=raster(new[i]) if i<len(new) else None
            h=max(x.shape[0] for x in [a,b] if x is not None); w=max(x.shape[1] for x in [a,b] if x is not None)
            a,b=canvas(a,h,w),canvas(b,h,w); fa=a.mean(2)<220;fb=b.mean(2)<220
            metrics.append({'page':i+1,'rgbMeanAbsoluteError':float(np.abs(a.astype(float)-b).mean()),'foregroundIoU':float((fa&fb).sum()/max(1,(fa|fb).sum()))})
    details['visual']=metrics
    return details

names=sorted(p.stem for p in (ROOT/'sources').glob('*.docx'))
folders=['browser-before','wasm-evaluation','wasm-fonts','libreoffice','docx2pdf','browser-after-default','browser-after']
results={}
gallery=[]
images=ROOT/'comparisons'; images.mkdir(exist_ok=True)
for name in names:
    results[name]={'reference':inspect(ROOT/'word-reference'/(name+'.pdf'))}
    results[name]['reference'].pop('text')
    for folder in folders: results[name][folder]=compare(name,folder)
    paths=[ROOT/'word-reference'/(name+'.pdf'), ROOT/'browser-before'/(name+'.pdf'), ROOT/('browser-after' if (ROOT/'browser-after'/(name+'.pdf')).exists() else 'wasm-evaluation')/(name+'.pdf')]
    for page_number in range(results[name]['reference']['pages']):
        rasters=[]
        for path in paths:
            if path.exists():
                with fitz.open(path) as d: rasters.append(raster(d[page_number]) if page_number<len(d) else None)
            else: rasters.append(None)
        h=max(a.shape[0] for a in rasters if a is not None);w=max(a.shape[1] for a in rasters if a is not None)
        strips=[canvas(a,h,w) for a in rasters]
        composite=np.concatenate(strips,axis=1)
        filename=name+('' if page_number==0 else '-page'+str(page_number+1))+'.png'
        fitz.Pixmap(fitz.csRGB,composite.shape[1],composite.shape[0],composite.tobytes(),False).save(images/filename)
        gallery.append((name,page_number+1,filename))
    print(name, {f: (results[name][f].get('pages'),results[name][f].get('visual',[{}])[0].get('rgbMeanAbsoluteError')) for f in folders})
(ROOT/'comparison.json').write_text(json.dumps(results,indent=2,ensure_ascii=True),encoding='utf-8')
body=''.join(f'<h2>{html.escape(n)} — page {p}</h2><p>Left: Microsoft Word native export. Middle: previous FileFlow button (blank means rejected or no corresponding page). Right: replacement local WASM with matching user-selected fonts.</p><img style="width:100%" src="comparisons/{f}">' for n,p,f in gallery)
(ROOT/'comparison.html').write_text('<!doctype html><meta charset="utf-8"><title>Word-to-PDF comparisons</title><h1>Authored DOCX rendering comparison</h1>'+body,encoding='utf-8')
