"""Prints each text key's Paper 1 table, as the PDF has it, beside the answers found, to read over.

    python review_keys.py BACKUP_DIR [paper-id ...]
"""
import json
import os
import re
import sys

import fitz

import answers
import words


def main():
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    only = set(a for a in sys.argv[2:] if not a.startswith('--'))
    for p in json.load(open(os.path.join(words.WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        if (only and pid not in only) or p['source'] == 'O-Level' or not (p['key'] or p['key_in_paper']):
            continue
        doc = fitz.open(root + (p['qp'] if p['key_in_paper'] else p['key']), filetype='pdf')
        text = [page.get_text() for page in doc]
        if p['key_in_paper']:
            first = next((i for i in range(len(text) - 1, -1, -1)
                          if any(answers.KEY_HEADING.match(l.strip()) for l in text[i].splitlines())), len(text) - 6)
            text = text[first:]
        # The stretch with the most single letters A to D in it: the Paper 1 table.
        joined = ' '.join(' '.join(t.split()) for t in text)
        best, at = 0, 0
        for i in range(0, max(1, len(joined) - 700), 50):
            n = len(re.findall(r'(?<![\w/])[A-D](?![\w/])', joined[i:i + 700]))
            if n > best:
                best, at = n, i
        found = json.load(open(os.path.join(words.WORK, pid, 'answers.json')))
        flat = ' '.join(' '.join(t.split()) for t in text)
        # Each answer found must be in the key as its number then its letter, somewhere.
        unseen = [n for n in range(1, 41) if str(n) in found and not re.search(
            r'(?<![\d.])(?:Q|A|MCQ)?%d[.)]?\s+(?:\S+\s+){0,12}?%s(?![\w/])' % (n, re.escape(found[str(n)][0])), flat)]
        missing = [n for n in range(1, 41) if str(n) not in found]
        if '--brief' in sys.argv:
            if unseen or missing:
                print('%-26s not in key as found: %s  missing: %s' % (pid, unseen, missing))
            continue
        print('=====', pid)
        print('FOUND', ' '.join('%d%s' % (n, found.get(str(n), '?')) for n in range(1, 41)))
        print('KEY  ', joined[at:at + 700])


if __name__ == '__main__':
    main()
