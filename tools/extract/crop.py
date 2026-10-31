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
UNWRITE = os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'unwrite.json')
GAP = 24      # pixels between stacked stretches
MARGIN = 16   # white kept round the ink when trimming
PRINT = 160         # darker than this is print
PRINT_PIXELS = 3    # ...a row with this many such pixels has some
SIDE = 0.04         # the share of each side left out of that (a scan's edge shadow)
# A page's furniture, whitened before cutting: the exam board's and printer's lines, page and paper
# codes, and a scanning app's stamp. Only near the top or foot of a page.
JUNK = re.compile(r'(©|ucles|cambridge|printed and published|camscanner|scanned with|turn over|'
                  r'^\(?\d{4}\)?\s*\d{1,2}$|^ol (physics|chemistry|biology)$|asia publishers|^\d{4}/\d{2}|^page \d+)', re.I)
# Words only a page's foot has ("2016 Paper1", "2018Poper1", a scanning app's stamp, a copyright line).
FOOT_WORD = re.compile(r'(\d{4} ?p\w{2,4}r|^p\w{2,4}r ?\d$|camscanner|^cs$|examination|preliminary|published|^permission|reproduce|^sec ?\d$|^[@©]$|^\d{4}/\d{2}$)', re.I)
FOOT_TOO = re.compile(r'^(\d[\w?]{3}|\w\d\w{2}|(?-i:[PF]\w{2,4})\W?\d?|ol|chemistry|physics|biology|ucles.*|\(\d{4}\)\d+|.*publish.*)$', re.I)
LINE = 0.006       # words this near in height are on one line
PAGE_NUMBER = 0.93  # a number alone on its line below this is the page's
SLIVER = 0.006      # ink this short, run through by the cut above a page's foot, is a page number's top
NOTICE = 0.6        # the copyright notice is below this
FOOT = 0.88        # a page's foot is below this


def trim(img):
    """The image cut to its ink, with a little white round it. Top and bottom go by print (a few
    dark pixels to a row, away from the sides): not a scan's faint speckle or the shadow down the
    page's edge, which would keep a band of empty paper under the question."""
    ink = img < 200
    side = int(img.shape[1] * SIDE)
    dark = (img[:, side:img.shape[1] - side] < PRINT).sum(1) >= PRINT_PIXELS
    rows = np.flatnonzero(dark) if dark.any() else np.flatnonzero(ink.any(1))
    cols = np.flatnonzero(ink[rows[0]:rows[-1] + 1].any(0)) if len(rows) else []
    if not len(rows) or not len(cols):
        return None
    y0, y1 = max(0, rows[0] - MARGIN), min(img.shape[0], rows[-1] + 1 + MARGIN)
    x0, x1 = max(0, cols[0] - MARGIN), min(img.shape[1], cols[-1] + 1 + MARGIN)
    return img[y0:y1, x0:x1]


def foot_top(words, bottom):
    """Where a page's foot starts, when OCR read it (and nothing else of the page's is below it):
    the top of its words and the page numbers beside them; [bottom] if there's none."""
    def mid(w):
        return (w[2] + w[4]) / 2

    def footish(w, near):
        """Whether [w]'s line (the words [near] it in height) is all foot: numbers, codes."""
        return all(re.fullmatch(r'\W*\d{1,4}\W*|\W*[a-z]{1,2}\W*', x[0]) or FOOT_TOO.match(x[0]) or FOOT_WORD.search(x[0])
                   for x in words if abs(mid(x) - mid(w)) < near)
    # The exam board's copyright notice on the last page ("Permission to reproduce items ...") and
    # all under it.
    notice = [w[2] for w in words if mid(w) > NOTICE and re.match(r'permission', w[0], re.I)]
    if notice:
        return min(bottom, min(notice) - 0.01)
    feet = [w for w in words if mid(w) > FOOT and FOOT_WORD.search(w[0])]
    # Or a page number with nothing else near it.
    feet += [w for w in words if mid(w) > PAGE_NUMBER and re.fullmatch(r'\W*\d{1,3}\W*', w[0]) and w not in feet
             and footish(w, 2.5 * LINE)]
    if not feet:
        return bottom
    # With that, what else down there reads as a foot: misread "2023 Paper 1"s, page numbers, codes.
    feet += [w for w in words if w not in feet and mid(w) > FOOT and footish(w, LINE)
             and (FOOT_TOO.match(w[0]) or re.fullmatch(r'\W*\d{1,3}\W*', w[0]))]
    mids = [mid(w) for w in feet]
    # And the rest of their lines (the page number, the school's name).
    feet += [w for w in words if w not in feet and any(abs(mid(w) - m) < LINE for m in mids)]
    top = min(w[2] for w in feet)
    # Below it, nothing but numbers and furniture.
    rest = [w for w in words if (w[2] + w[4]) / 2 > top and w not in feet and not re.fullmatch(r'\W*\d{0,4}\W*', w[0])
            and not JUNK.search(w[0]) and not re.match(r'ol ?(physics|chemistry|biology)', w[0], re.I)]
    if any((w[2] + w[4]) / 2 < bottom for w in rest):
        return bottom
    # A little above it (a page number's top reaches above its box), but not into the line above.
    above = max((w[4] for w in words if w not in feet and w[4] <= top), default=0)
    return max(min(top - 0.004, above + 0.003), top - 0.015)


def without_sliver(part, h):
    """[part] (of a page h high) without the sliver of a page number's top that reaches above where
    OCR put it, when it's cut just above the page's foot: a thin run of ink at its very foot, under a
    blank band."""
    rows = np.flatnonzero((part < 128).sum(1) > 1)
    if len(rows) < 2:
        return part
    # The last run of inked rows, and the blank above it.
    breaks = np.flatnonzero(np.diff(rows) > 1)
    if not len(breaks):
        return part
    start, above = rows[breaks[-1] + 1], rows[breaks[-1]]
    if rows[-1] - start < SLIVER * h and start - above > SLIVER * h and rows[-1] > part.shape[0] - 3 * SLIVER * h:
        part = part.copy()
        part[start:] = 255
    return part


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
    found, shared = questions.parts(pages, questions.manual(pid))
    # Where a page's foot starts, set by eye where OCR didn't read it: {page: top}.
    feet = questions.manual(pid).get('feet', {})
    _, furniture = questions.furniture(pages)
    images = {}
    # Questions to cut from the pages with a student's writing taken off (unwrite.py), looked over
    # by eye: the writing gave the answer away, and taking it off left the print whole.
    unwritten = set(json.load(open(UNWRITE, encoding='utf-8')).get(pid, [])) if os.path.exists(UNWRITE) else set()

    def page(p, clean=False):
        if (p, clean) not in images:
            path = os.path.join(d, 'p%02d_unwritten.png' % p)
            img = cv2.imread(path if clean and os.path.exists(path) else os.path.join(d, 'p%02d.png' % p), cv2.IMREAD_GRAYSCALE)
            h, w = img.shape
            junk = list(furniture[p]) + [l for l in questions.lines(pages[p])
                                         if (l[0][2] < 0.08 or l[0][4] > 0.8) and JUNK.search(questions.text(l))]
            for l in junk:
                x0, y0 = int(min(x[1] for x in l) * w) - 8, int(min(x[2] for x in l) * h) - 8
                x1, y1 = int(max(x[3] for x in l) * w) + 8, int(max(x[4] for x in l) * h) + 8
                img[max(0, y0):y1, max(0, x0):x1] = 255
            images[p, clean] = img
        return images[p, clean]

    def piece(p, top, bottom, n=None):
        img = page(p, n in unwritten)
        h = img.shape[0]
        foot = min(foot_top(pages[p], bottom), feet.get(str(p), 1))
        part = img[int(top * h):int(min(bottom, foot) * h)]
        return trim(without_sliver(part, h))

    by = {}
    for n, p, top, bottom in found:
        by.setdefault(n, []).append(piece(p, top, bottom, n))
    os.makedirs(os.path.join(d, 'q'), exist_ok=True)
    for f in os.listdir(os.path.join(d, 'q')):
        os.remove(os.path.join(d, 'q', f))
    numbers = sorted(n for n in by if n > 0)
    for n in numbers:
        stem = [piece(p, t, b, n) for p, t, b in shared.get(n, [])]
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
