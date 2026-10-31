"""Reads O-Level Biology's compiled key: a year heading and then a grid of 40 answers, in columns
of question number and key letter, one or two years a page.

    python grid_key.py BACKUP_DIR

The grids are pictures, so the pages are read by OCR, larger than words.py reads them (single
letters go missing otherwise). A letter's question comes from where it sits in its grid (which
column, which row), and the numbers OCR read beside letters are checked against that. Writes
work/keys/<key>/grid.json, {year: {question: letter}}, which answers.py takes the year's answers from.
"""
import json
import os
import re
import sys

import cv2
import fitz
import numpy as np

import keywords
import words

KEY = 'Biology/TYS Answers␠/Paper 1/Compiled Paper 1 Answers (2015-2025).pdf.pdf'
YEAR = re.compile(r'^(20\d\d)$')
LETTER = re.compile(r'^[A-D]$')
NUMBER = re.compile(r'^\d{1,2}$')


def clusters(values, gap):
    """Indexes of [values] grouped where they're more than [gap] apart, in order."""
    order = sorted(range(len(values)), key=lambda i: values[i])
    out, group, last = {}, -1, None
    for i in order:
        if last is None or values[i] - last > gap:
            group += 1
        out[i] = group
        last = values[i]
    return out, group + 1


def grid(found):
    """{question: letter} from one year's words, and the numbers that disagree with their place."""
    lets = [w for w in found if LETTER.match(w[0])]
    nums = [w for w in found if NUMBER.match(w[0]) and 1 <= int(w[0]) <= 40]
    if not lets:
        return {}, []
    h = np.median([w[4] - w[2] for w in lets])
    col, ncols = clusters([(w[1] + w[3]) / 2 for w in lets], h * 3)
    row, nrows = clusters([(w[2] + w[4]) / 2 for w in lets], h * 0.6)
    out, wrong = {}, []
    per = 40 // ncols if ncols in (2, 4) else None
    if per is None or nrows != per:
        return None, ['%d columns, %d rows' % (ncols, nrows)]
    for i, w in enumerate(lets):
        q = col[i] * per + row[i] + 1
        out[q] = w[0]
        # The number on the letter's left, in its row.
        cy = (w[2] + w[4]) / 2
        beside = [n for n in nums if abs((n[2] + n[4]) / 2 - cy) < h * 0.6 and n[3] < w[1] and w[1] - n[3] < h * 8]
        if beside:
            n = max(beside, key=lambda n: n[3])
            if int(n[0]) != q:
                wrong.append('%s read as %d' % (n[0], q))
    return out, wrong


def unruled(gray):
    """[gray] with a table's ruling lines whited out: OCR misses a letter boxed in by them."""
    ink = (gray < 160).astype(np.uint8)
    lines = cv2.morphologyEx(ink, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (60, 1)))
    lines |= cv2.morphologyEx(ink, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (1, 60)))
    lines = cv2.dilate(lines, np.ones((5, 5), np.uint8))
    out = gray.copy()
    out[lines > 0] = 255
    return out


def main():
    from rapidocr_onnxruntime import RapidOCR
    ocr = RapidOCR()
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    doc = fitz.open(root + KEY)
    years = {}
    for page in doc:
        pix = page.get_pixmap(dpi=300, colorspace=fitz.csGRAY)
        img = unruled(np.frombuffer(pix.samples, np.uint8).reshape(pix.h, pix.w))
        result, _ = ocr(cv2.cvtColor(img, cv2.COLOR_GRAY2BGR))
        found = []
        for box, text, _ in result or []:
            xs, ys = [p[0] / pix.w for p in box], [p[1] / pix.h for p in box]
            found.append([text.strip(), min(xs), min(ys), max(xs), max(ys)])
        # Each year's grid: from its heading down to the next one.
        heads = sorted((w[2], int(w[0])) for w in found if YEAR.match(w[0]))
        for i, (top, year) in enumerate(heads):
            bottom = heads[i + 1][0] if i + 1 < len(heads) else 1
            answers, wrong = grid([w for w in found if top < w[2] < bottom])
            pid = 'biology-o-level-%d' % year
            if answers is None:
                print('%s  grid not understood: %s' % (pid, wrong[0]))
                continue
            years[str(year)] = {str(k): v for k, v in sorted(answers.items())}
            missing = sorted(set(range(1, 41)) - set(answers))
            print('%s %2d answers%s%s' % (pid, len(answers), '  missing %s' % missing if missing else '',
                                          '  NUMBERS DISAGREE: %s' % ', '.join(wrong) if wrong else ''), flush=True)
    json.dump(years, open(os.path.join(keywords.KEYS, keywords.key_id(KEY), 'grid.json'), 'w'), indent=1)


if __name__ == '__main__':
    main()
