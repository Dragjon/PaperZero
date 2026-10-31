"""Adds the question numbers OCR missed or ran into a word to papers read before words.py did.

    python margins.py BACKUP_DIR [paper-id ...]

(farm.py --margins does the same for many papers at once.)

Only scanned pages (no text of their own) are looked at; a number already among the words isn't
added twice.
"""
import json
import os
import sys

import cv2
import fitz

import words

WORK = words.WORK


def add(root, p):
    """The numbers [p]'s words lack, added to its words.json; how many."""
    pid = words.paper_id(p)
    path = os.path.join(WORK, pid, 'words.json')
    pages = json.load(open(path, encoding='utf-8'))
    doc = fitz.open(root + p['qp'], filetype='pdf')
    added = 0
    for i, page in enumerate(doc):
        if len(words.text_words(page)) >= 30:
            continue
        gray = cv2.imread(os.path.join(WORK, pid, 'p%02d.png' % i), cv2.IMREAD_GRAYSCALE)
        before = len(pages[i])
        pages[i] = words.unglue(pages[i])
        added += len(pages[i]) - before
        for n in words.margin_numbers(gray, pages[i]):
            cx, cy = (n[1] + n[3]) / 2, (n[2] + n[4]) / 2
            if not any(w[1] <= cx <= w[3] and w[2] <= cy <= w[4] for w in pages[i]):
                pages[i].append(n)
                added += 1
    json.dump(pages, open(path, 'w', encoding='utf-8'), ensure_ascii=False)
    return added


def main():
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    only = set(sys.argv[2:])
    for p in json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        if (only and pid not in only) or not os.path.exists(os.path.join(WORK, pid, 'words.json')):
            continue
        print(pid, '+%d numbers' % add(root, p), flush=True)


if __name__ == '__main__':
    main()
