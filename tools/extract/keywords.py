"""Renders and reads every answer key in work/manifest.json, once each (several papers share one).

    python keywords.py BACKUP_DIR

work/keys/<name>/: pNN.png pages and words.json, as words.py does for papers. A key printed in
the paper itself is read from the paper's own words.
"""
import json
import os
import re
import sys

import cv2
import fitz
import numpy as np

import words

KEYS = os.path.join(words.WORK, 'keys')


def key_id(path):
    return re.sub(r'[^a-z0-9]+', '-', os.path.splitext(os.path.basename(path))[0].lower()).strip('-')


def main():
    root = sys.argv[1].replace('\\', '/').rstrip('/') + '/Science/'
    seen = set()
    for p in json.load(open(os.path.join(words.WORK, 'manifest.json'), encoding='utf-8')):
        if not p['key'] or p['key'] in seen:
            continue
        seen.add(p['key'])
        out = os.path.join(KEYS, key_id(p['key']))
        if os.path.exists(os.path.join(out, 'words.json')):
            continue
        os.makedirs(out, exist_ok=True)
        pages = []
        for i, page in enumerate(fitz.open(root + p['key'], filetype='pdf')):
            pix = page.get_pixmap(dpi=words.DPI, colorspace=fitz.csGRAY)
            gray = np.frombuffer(pix.samples, np.uint8).reshape(pix.h, pix.w).copy()
            cv2.imwrite(os.path.join(out, 'p%02d.png' % i), gray)
            ws = words.text_words(page)
            if len(ws) < 30:
                ws = words.unglue(words.ocr_words(gray))
            pages.append(ws)
        json.dump(pages, open(os.path.join(out, 'words.json'), 'w', encoding='utf-8'), ensure_ascii=False)
        print(key_id(p['key']), len(pages), 'pages', flush=True)


if __name__ == '__main__':
    main()
