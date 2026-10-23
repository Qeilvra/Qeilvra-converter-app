"""Scope-specific evidence checks, deliberately not a claim of universal DOCX fidelity."""
import json
from pathlib import Path
import fitz

root=Path('.tools/word-pdf-rendering')
comparison=json.loads((root/'comparison.json').read_text())
records=json.loads((root/'browser-after/results.json').read_text())
checks={}
checks['allFiveOriginalDocxInputsHaveRealSavedPdfs']=len(records)==5 and all(r.get('bytes',0)>0 and (root/'browser-after'/(r['name']+'.pdf')).exists() for r in records)
checks['allFiveMatchWordPageCount']=all(x['browser-after'].get('pages')==x['reference']['pages'] for x in comparison.values())
checks['noUploadsOrOutsideRequests']=all(not r['uploads'] and not r['blockedExternalRequests'] for r in records)
checks['fiveConversionsWithoutScreenReloadLeaveNoWorkersOrEngineFrames']=all(r.get('remainingWorkers')==0 and r.get('remainingEngineFrames')==0 for r in records)
cv=comparison['professional-cv']
checks['fontAssistedCvPixelErrorFallsByAtLeast80Percent']=cv['browser-after']['visual'][0]['rgbMeanAbsoluteError']<cv['browser-before']['visual'][0]['rgbMeanAbsoluteError']*.2
checks['columnsImproveOnBothPages']=all(a['rgbMeanAbsoluteError']<b['rgbMeanAbsoluteError'] for a,b in zip(comparison['columns']['browser-after']['visual'],comparison['columns']['browser-before']['visual']))
checks['fourLatinFixturesKeepCharacterCounts']=all(comparison[n]['browser-after']['missingCharacters']==comparison[n]['browser-after']['extraCharacters']==0 for n in ['professional-cv','columns','complex-report','shapes'])
with fitz.open(root/'browser-after/complex-report.pdf') as d:
    tables=d[0].find_tables().tables
    checks['mergedTableRowsAndDistinctCells']=len(tables)==1 and len(tables[0].rows)==5 and len(tables[0].cells)==12
    checks['paintedLogoAndHyperlink']=sum(len(p.get_image_info()) for p in d)==1 and sum(len(p.get_links()) for p in d)==1
    checks['runningMatterAndExplicitBreak']=all('Engineering report' in p.get_text() and 'document footer' in p.get_text() for p in d) and 'Explicit second page' in d[1].get_text()
checks['productionWordConversionWithNetworkingDisabled']=json.loads((root/'production-offline/results.json').read_text())['result']=='PASS'
checks['otherOfflineDocumentToolsStillWork']=json.loads(Path('.tools/backend-free-evidence/offline-web.json').read_text())['result']=='PASS'
limitations=['Native Android DOCX layout renderer is not implemented or device-tested.', 'Matching fonts are necessary for the measured CV improvement; the no-extra-font CV is worse by pixel error.', 'Urdu differs from Word in spacing and one extracted codepoint.', 'Runtime reserves 1 GiB WASM memory; JS heap snapshots are not total or peak RSS.', 'Complete runtime redistribution/source/relinking materials are not assembled.']
report={'scope':'Local browser implementation only; full product goal remains incomplete','result':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'limitations':limitations}
(root/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
assert all(checks.values())
