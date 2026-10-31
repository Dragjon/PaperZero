"""Cuts each paper's questions out of its page images (after words.py).

    python crop.py [paper-id ...]

work/<id>/q/NN.png for question NN (its stretches of page stacked, a shared stem on top), and
work/<id>/sheet.jpg to look over. Prints how many questions each paper gave.
"""
import json
import os
import re
import sys

import cv2
import numpy as np

import questions

WORK = os.path.join(os.path.dirname(__file__), '..', '..', 'work')
GAP = 24      # pixels between stacked stretches
MARGIN = 16   # white kept round the ink when trimming
# A page's furniture, whitened before cutting: the exam board's and printer's lines, page and paper
# codes, and a scanning app's stamp. Only near the top or foot of a page.
JUNK = re.compile(r'(©|ucles|cambridge|printed and published|camscanner|scanned with|turn over|'
                  r'^\(?\d{4}\)?\s*\d{1,2}$|^ol (physics|chemistry|biology)$|asia publishers|^\d{4}/\d{2}|^page \d+)', re.I)


def trim(img):
    """The image cut to its ink, with a little white round it."""
    ink = img < 200
    rows = np.flatnonzero(ink.any(1))
    cols = np.flatnonzero(ink.any(0))
    if not len(rows):
        return None
    y0, y1 = max(0, rows[0] - MARGIN), min(img.shape[0], rows[-1] + 1 + MARGIN)
    x0, x1 = max(0, cols[0] - MARGIN), min(img.shape[1], cols[-1] + 1 + MARGIN)
    return img[y0:y1, x0:x1]


def stack(pieces):
    pieces = [p for p in pieces if p is not None]
    if not pieces:
        return None
    w = max(p.shape[1] for p in pieces)
    out = []
    for i, p in enumerate(pieces):
        if i:
            out.append(np.full((GAP, w), 255, np.uint8))
        out.append(cv2.copyMakeBorder(p, 0, 0, 0, w - p.shape[1], cv2.BORDER_CONSTANT, value=255))
    return np.vstack(out)


def crop_paper(pid):
    d = os.path.join(WORK, pid)
    pages = json.load(open(os.path.join(d, 'words.json'), encoding='utf-8'))
    found, shared = questions.parts(pages)
    _, furniture = questions.furniture(pages)
    images = {}

    def page(p):
        if p not in images:
            # With a student's writing taken off, where unwrite.py found some.
            clean = os.path.join(d, 'p%02d_clean.png' % p)
            img = cv2.imread(clean if os.path.exists(clean) else os.path.join(d, 'p%02d.png' % p), cv2.IMREAD_GRAYSCALE)
            h, w = img.shape
            junk = list(furniture[p]) + [l for l in questions.lines(pages[p])
                                         if (l[0][2] < 0.08 or l[0][4] > 0.8) and JUNK.search(questions.text(l))]
            for l in junk:
                x0, y0 = int(min(x[1] for x in l) * w) - 8, int(min(x[2] for x in l) * h) - 8
                x1, y1 = int(max(x[3] for x in l) * w) + 8, int(max(x[4] for x in l) * h) + 8
                img[max(0, y0):y1, max(0, x0):x1] = 255
            images[p] = img
        return images[p]

    def piece(p, top, bottom):
        img = page(p)
        h = img.shape[0]
        return trim(img[int(top * h):int(bottom * h)])

    by = {}
    for n, p, top, bottom in found:
        by.setdefault(n, []).append(piece(p, top, bottom))
    os.makedirs(os.path.join(d, 'q'), exist_ok=True)
    for f in os.listdir(os.path.join(d, 'q')):
        os.remove(os.path.join(d, 'q', f))
    numbers = sorted(n for n in by if n > 0)
    for n in numbers:
        stem = [piece(p, t, b) for p, t, b in shared.get(n, [])]
        img = stack(stem + by[n])
        if img is not None:
            cv2.imwrite(os.path.join(d, 'q', '%02d.png' % n), img)
    if 0 in by and by[0]:
        img = stack(by[0])
        if img is not None:
            cv2.imwrite(os.path.join(d, 'q', 'lead.png'), img)
    sheet(d, numbers)
    return numbers


def sheet(d, numbers):
    """Every question small, in a grid, with its number."""
    tiles = []
    for n in numbers:
        img = cv2.imread(os.path.join(d, 'q', '%02d.png' % n), cv2.IMREAD_GRAYSCALE)
        if img is None:
            continue
        s = 360 / img.shape[1]
        img = cv2.resize(img, (360, max(1, min(420, int(img.shape[0] * s)))), interpolation=cv2.INTER_AREA)
        tile = np.full((440, 380, 3), 255, np.uint8)
        tile[18:18 + img.shape[0], 10:370] = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        cv2.putText(tile, str(n), (4, 15), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 220), 1)
        cv2.rectangle(tile, (0, 0), (379, 439), (200, 200, 200), 1)
        tiles.append(tile)
    if not tiles:
        return
    cols = 8
    while len(tiles) % cols:
        tiles.append(np.full_like(tiles[0], 255))
    rows = [np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)]
    cv2.imwrite(os.path.join(d, 'sheet.jpg'), np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 80])


def main():
    ids = sys.argv[1:] or sorted(x for x in os.listdir(WORK) if os.path.exists(os.path.join(WORK, x, 'words.json')))
    for pid in ids:
        numbers = crop_paper(pid)
        missing = sorted(set(range(1, 41)) - set(numbers))
        print('%-28s %2d questions%s' % (pid, len(numbers), ('  missing %s' % missing) if missing else ''), flush=True)


if __name__ == '__main__':
    main()
