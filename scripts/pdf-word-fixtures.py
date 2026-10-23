"""Generate non-sensitive PDF layout regressions. Development-only PyMuPDF/Pillow."""
from pathlib import Path
import json
import fitz

ROOT = Path('.tools/pdf-word-quality')
SOURCES = ROOT / 'sources'
SOURCES.mkdir(parents=True, exist_ok=True)
fonts = Path('C:/Windows/Fonts')
manifest = []


def page(document, width=612, height=792):
    p = document.new_page(width=width, height=height)
    for name, file in [('Arial', 'arial.ttf'), ('ArialBold', 'arialbd.ttf'), ('ArialItalic', 'ariali.ttf')]:
        p.insert_font(fontname=name, fontfile=str(fonts / file))
    return p


def text(p, value, x, y, size=11, font='Arial', color=(0, 0, 0)):
    p.insert_text((x, y), value, fontsize=size, fontname=font, color=color)


def save(document, name, **expected):
    document.save(SOURCES / f'{name}.pdf', garbage=4, deflate=True)
    manifest.append(dict(name=name, pages=len(document), **expected))
    document.close()


d = fitz.open(); p = page(d)
text(p, 'SYNTHETIC CV', 50, 58, 24, 'ArialBold', (.08, .2, .4))
text(p, 'Engineer | Lahore | sample@example.invalid', 50, 82, 10)
text(p, 'PROFILE', 50, 124, 14, 'ArialBold', (.08, .2, .4))
text(p, 'Document engineer building reliable local tools for editable files.', 50, 146)
text(p, 'Experienced with typography, structured content, and offline processing.', 50, 160)
text(p, 'EXPERIENCE', 50, 204, 14, 'ArialBold', (.08, .2, .4))
text(p, 'Senior Engineer', 50, 227, 12, 'ArialBold')
text(p, 'Example Company', 50, 244, 11, 'ArialItalic')
text(p, '\u2022 Built conversion tools that preserve selectable document text.', 62, 267)
text(p, '  Continued the same achievement across a second source line.', 62, 281)
text(p, '\u2022 Improved table recovery and protected identifier 001234.', 62, 301)
text(p, 'EDUCATION', 50, 344, 14, 'ArialBold', (.08, .2, .4))
text(p, 'BSc Computer Science', 50, 367, 11, 'ArialBold')
text(p, 'Example University, 2022', 50, 383)
text(p, 'SKILLS', 50, 426, 14, 'ArialBold', (.08, .2, .4))
text(p, 'Java, TypeScript, document layout, quality assurance', 50, 449)
save(d, 'synthetic-cv', min_headings=4, max_paragraphs=15)

d = fitz.open()
for number in range(1, 3):
    p = page(d, 595, 842)
    text(p, 'FileFlow quality sample', 50, 25, 9)
    text(p, f'Page {number}', 500, 820, 9)
    text(p, f'Chapter {number}', 50, 90, 18, 'ArialBold')
    text(p, 'A paragraph begins here and continues on the next PDF line.', 50, 120)
    text(p, 'Its second line must remain in the same editable Word paragraph.', 50, 134)
    text(p, 'A distinct paragraph starts after this larger vertical space.', 50, 165)
    text(p, 'This continuation belongs to that second paragraph.', 50, 179)
    text(p, '1. A numbered item with original numbering.', 50, 215)
    text(p, '2. A second numbered item.', 50, 236)
save(d, 'multipage', max_paragraphs=14, min_headings=2, headers=True)

d = fitz.open(); p = page(d)
text(p, 'TWO COLUMN DOCUMENT', 50, 60, 18, 'ArialBold')
for x, label in [(50, 'LEFT'), (325, 'RIGHT')]:
    text(p, f'{label} SECTION', x, 112, 14, 'ArialBold')
    for i, line in enumerate([f'{label} paragraph starts in its own column.', 'It continues without merging with the', 'unrelated text in the neighboring column.']):
        text(p, line, x, 139 + i * 14, 10)
    text(p, f'{label} second paragraph.', x, 200, 10)
    text(p, 'Another continuation line.', x, 214, 10)
save(d, 'columns', min_headings=3, columns=True)

d = fitz.open(); p = page(d)
text(p, 'TABLE AND IMAGE', 50, 60, 18, 'ArialBold')
rows = [('ID', 'Amount', 'Notes'), ('001234', '$1,234.50', 'Editable cell'), ('12345678901234567890', '=SUM(A1:A2)', 'Literal formula')]
for y in [100, 130, 160, 190]:
    p.draw_line((50, y), (560, y), color=(.2, .3, .4), width=.75)
for x in [50, 265, 410, 560]:
    p.draw_line((x, 100), (x, 190), color=(.2, .3, .4), width=.75)
for r, row in enumerate(rows):
    for x, value in zip([58, 273, 418], row):
        text(p, value, x, 120 + r * 30, 10, 'Arial' if r else 'ArialBold')
text(p, 'The image remains a separate asset below the table.', 50, 235)
pixels = bytes(channel for y in range(64) for x in range(96) for channel in ((227, 176, 71) if (x-48)**2+(y-32)**2 < 625 else (27, 71, 138)))
image = fitz.Pixmap(fitz.csRGB, 96, 64, pixels, False)
p.insert_image(fitz.Rect(380, 270, 476, 334), stream=image.tobytes('png'))
text(p, 'Caption beside the image.', 50, 290)
save(d, 'table-image', tables=1, images=1)

d = fitz.open(); p = page(d)
text(p, 'Typography and Unicode', 50, 65, 20, 'ArialBold', (.1, .3, .6))
text(p, 'Bold text', 50, 110, 12, 'ArialBold')
text(p, 'Italic text', 50, 140, 12, 'ArialItalic')
text(p, 'Underlined text', 50, 170, 12)
p.draw_line((50, 171.5), (131.4, 171.5), width=.6)
text(p, 'Colored text', 50, 200, 12, color=(.8, .1, .2))
text(p, 'Caf\u00e9 na\u00efve fa\u00e7ade \u2014 \u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac \u2014 \u041f\u0440\u0438\u0432\u0435\u0442', 50, 235, 12)
text(p, 'Centered heading', 240, 280, 14, 'ArialBold')
save(d, 'typography', unicode=True, underline=True)
(ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(f'Created {len(manifest)} synthetic PDFs in {SOURCES}')
