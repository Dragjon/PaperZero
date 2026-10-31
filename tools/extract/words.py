"""Renders every page of every paper in work/manifest.json and finds its words.

    python words.py BACKUP_DIR [paper-id ...]

For each paper, work/<id>/: page images (pNN.png, 200 dpi, gray) and words.json, a list per page
of [text, left, top, right, bottom] in page fractions. Words come from the PDF's own text where it
has some, and otherwise from OCR (RapidOCR, the PP-OCR models): a line read by OCR is cut into
words in proportion to their letters.
"""
import json
import os
import re
import sys

import cv2
import fitz
import numpy as np

DPI = 200
WORK = os.path.join(os.path.dirname(__file__), '..', '..', 'work')
_ocr = None


def paper_id(p):
    return re.sub(r'[^a-z0-9]+', '-', '%s %s %s' % (p['subject'], p['source'], p['year']), flags=re.I).strip('-').lower()


def ocr():
    global _ocr
    if _ocr is None:
        from rapidocr_onnxruntime import RapidOCR
        _ocr = RapidOCR()
    return _ocr


def text_words(page):
    W, H = page.rect.width, page.rect.height
    return [[w[4], w[0] / W, w[1] / H, w[2] / W, w[3] / H] for w in page.get_text('words')]


GLUED = re.compile(r'^(\d{1,2})([A-Z].*)$')


def unglue(words):
    """OCR runs a question's number into its first word ("25A stone", "26Arayof"): the number is
    made a word of its own, its width shared by letters."""
    out = []
    for w in words:
        m = GLUED.match(w[0])
        if not m:
            out.append(w)
            continue
        a, b = m.group(1), m.group(2)
        split = w[1] + (w[3] - w[1]) * len(a) / len(w[0])
        out.append([a, w[1], w[2], split, w[4]])
        out.append([b, split, w[2], w[3], w[4]])
    return out


def ocr_words(gray):
    h, w = gray.shape
    result, _ = ocr()(gray)
    out = []
    for box, text, conf in result or []:
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        parts = text.split()
        total = sum(len(p) for p in parts) + max(0, len(parts) - 1)
        x = x0
        for p in parts:
            pw = (x1 - x0) * len(p) / max(1, total)
            out.append([p, x / w, y0 / h, (x + pw) / w, y1 / h])
            x += pw + (x1 - x0) / max(1, total)
    return out


def margin_numbers(gray, words):
    """Question numbers in the margin, which OCR's detector passes over (a lone digit or two):
    marks left of the text, in clusters a digit or two wide, each read on its own."""
    h, w = gray.shape
    starts = sorted(l[1] for l in words)
    if len(starts) < 5:
        return []
    body = starts[len(starts) // 10] * w
    heights = sorted((x[4] - x[2]) * h for x in words)
    line = heights[len(heights) // 2]
    strip = gray[:, :max(1, int(body - line * 0.3))]
    ink = (strip < 140).astype(np.uint8)
    n, _, stats, _ = cv2.connectedComponentsWithStats(ink, connectivity=8)
    marks = [s for s in stats[1:] if line * 0.35 <= s[3] <= line * 1.6 and s[2] <= line * 1.2 and s[4] >= 12]
    # Digits of one number: side by side, overlapping in height.
    marks.sort(key=lambda s: (s[1], s[0]))
    groups = []
    for s in marks:
        for g in groups:
            x0, y0, x1, y1 = g
            if min(y1, s[1] + s[3]) - max(y0, s[1]) > 0.5 * min(y1 - y0, s[3]) and s[0] - x1 < line * 0.6 and x0 - (s[0] + s[2]) < line * 0.6:
                g[:] = [min(x0, s[0]), min(y0, s[1]), max(x1, s[0] + s[2]), max(y1, s[1] + s[3])]
                break
        else:
            groups.append([s[0], s[1], s[0] + s[2], s[1] + s[3]])
    out = []
    for x0, y0, x1, y1 in groups:
        if x1 - x0 > line * 2.2:
            continue
        pad = int(line * 0.4)
        piece = gray[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad]
        piece = cv2.copyMakeBorder(piece, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=255)
        result, _ = ocr()(piece, use_det=False, use_cls=False, use_rec=True)
        # (text, score), or (box, text, score) from older RapidOCR, which reads with detection anyway.
        first = result[0] if result else ['']
        read = str(first[1] if len(first) == 3 else first[0]).strip().rstrip('.')
        if re.fullmatch(r'\d{1,2}', read):
            out.append([read, x0 / w, y0 / h, x1 / w, y1 / h])
    return out


def main():
    root = sys.argv[1].replace('\\', '/').rstrip('/') + '/Science/'
    only = set(sys.argv[2:])
    papers = json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8'))
    for p in papers:
        pid = paper_id(p)
        if only and pid not in only:
            continue
        out = os.path.join(WORK, pid)
        if os.path.exists(os.path.join(out, 'words.json')):
            continue
        os.makedirs(out, exist_ok=True)
        doc = fitz.open(root + p['qp'], filetype='pdf')
        pages = []
        for i, page in enumerate(doc):
            pix = page.get_pixmap(dpi=DPI, colorspace=fitz.csGRAY)
            gray = np.frombuffer(pix.samples, np.uint8).reshape(pix.h, pix.w).copy()
            cv2.imwrite(os.path.join(out, 'p%02d.png' % i), gray)
            words = text_words(page)
            # A scan has no text, or only a scanning app's stamp ("CamScanner").
            if len(words) < 30:
                words = unglue(ocr_words(gray))
                words += margin_numbers(gray, words)
            pages.append(words)
        json.dump(pages, open(os.path.join(out, 'words.json'), 'w', encoding='utf-8'), ensure_ascii=False)
        print(pid, len(pages), 'pages', sum(map(len, pages)), 'words', flush=True)


if __name__ == '__main__':
    main()
