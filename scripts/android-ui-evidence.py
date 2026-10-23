"""Index real native Android render-test artifacts; never fabricate app screenshots."""
from pathlib import Path
import hashlib
import html
import json
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
evidence = root / '.tools/android-ui'
evidence.mkdir(parents=True, exist_ok=True)
reports = root / 'android/app/build/test-results/testDebugUnitTest'
suites = [ET.parse(p).getroot() for p in reports.glob('TEST-*.xml')]
tests = {key: sum(int(s.get(key, '0')) for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
screens = sorted(p.name for p in (evidence / 'after').glob('*.png'))
apk = root / 'android/app/build/outputs/apk/debug/app-debug.apk'
record = {
    'renderer': 'Robolectric 4.17 native graphics, Android API 28; inset test API 30',
    'deviceTested': False,
    'unitTests': tests,
    'screenshots': screens,
    'apkBytes': apk.stat().st_size if apk.exists() else None,
    'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest() if apk.exists() else None,
    'fontSource': 'https://github.com/google/fonts/tree/main/ofl/inter',
    'fontSha256': hashlib.sha256((root / 'android/app/src/main/res/font/inter.ttf').read_bytes()).hexdigest(),
    'limitations': ['No physical device or running Android emulator', 'No device FPS, peak memory or camera measurement', 'Native PdfDocument could not execute in this Windows JVM renderer', 'Open/Share receiving apps and URI grants require device validation'],
}
(evidence / 'verification.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
groups = [
    ('Original Home / redesigned Home', [('before/home.png', 'Original committed Java/XML app'), ('after/home.png', 'Redesigned native Home'), ('after/home-dark.png', 'Dark Home')]),
    ('Tools', [('after/tools-light.png', 'Light'), ('after/tools-dark.png', 'Dark')]),
    ('Real PDFBox merge: processing and success', [('after/processing-light.png', 'Processing, light'), ('after/processing-dark.png', 'Processing, dark'), ('after/success-light.png', 'Verified merged.pdf, light'), ('after/success-dark.png', 'Verified merged.pdf, dark')]),
    ('Actual retained output history', [('after/recent-light.png', 'Light, actual generated test output'), ('after/recent-dark.png', 'Dark, actual generated test output'), ('after/recent-dark-empty.png', 'Empty state')]),
    ('Accessibility and failure states', [('after/home-320-large-font.png', '320 dp / 1.6 font scale'), ('after/tools-320-large-font.png', '320 dp / 1.6 font scale'), ('after/home-480-xhdpi.png', '480 dp / xhdpi'), ('after/error-light.png', 'Real missing-file processing error'), ('after/settings-dark.png', 'Settings')]),
]
body = '<h1>FileFlow native Android redesign</h1><p>Actual Java/XML views rendered by Robolectric native graphics. These are not device/emulator screenshots or React mockups. No phone frames or marketing panels are included in the app. History shown here comes from real test conversions only.</p>'
for title, items in groups:
    body += '<h2>' + html.escape(title) + '</h2><div class="screens">'
    for path, label in items:
        if (evidence / path).exists():
            body += f'<figure><figcaption>{html.escape(label)}</figcaption><a href="{path}"><img src="{path}" loading="lazy"></a></figure>'
    body += '</div>'
body += '<p><a href="verification.json">Verification metadata</a> · <a href="verified-merged-light.pdf">Actual two-page merged PDF</a></p>'
(evidence / 'comparison.html').write_text('<!doctype html><meta charset="utf-8"><title>FileFlow Android native UI</title><style>body{font:16px system-ui;background:#eef4ff;color:#091b4d;margin:28px}h1{font-size:28px}h2{margin-top:36px}.screens{display:flex;gap:20px;overflow:auto;align-items:flex-start}figure{margin:0;flex:0 0 300px}figcaption{min-height:48px;font-size:14px}img{width:300px;border:1px solid #c8d9ee;border-radius:12px}</style>' + body, encoding='utf-8')
tracked = subprocess.check_output(['git', '-c', 'core.safecrlf=false', '-c', 'core.quotepath=false', 'diff', '--name-only', '--', 'android'], cwd=root, text=True).splitlines()
added = subprocess.check_output(['git', '-c', 'core.safecrlf=false', '-c', 'core.quotepath=false', 'ls-files', '--others', '--exclude-standard', '--', 'android'], cwd=root, text=True).splitlines()
files = sorted(set(tracked + added + ['scripts/android-ui-evidence.py', 'docs/android-ui-redesign.md', 'docs/android-ui-redesign-files.txt']))
(root / 'docs/android-ui-redesign-files.txt').write_text('\n'.join(files) + '\n', encoding='utf-8')
print(json.dumps(record, indent=2))
