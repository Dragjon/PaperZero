"""Finds every Paper 1 (MCQ) in the backup and its answer key, and writes work/manifest.json.

    python inventory.py BACKUP_DIR

Each paper: subject, source ('O-Level' or the school), year, the question paper's path, and where
its answers are: a key file, or the paper itself (keys printed at its end).
"""
import json
import os
import re
import sys

import fitz

root = sys.argv[1].replace('\\', '/').rstrip('/') + '/Science/'
papers = []


def add(subject, source, year, qp, key, key_in_paper=False):
    if not key and not key_in_paper:
        # No key of its own: some papers have theirs printed at the back.
        doc = fitz.open(qp, filetype='pdf')
        back = ' '.join(page.get_text() for page in list(doc)[-6:])
        key_in_paper = bool(re.search(r'(answer key|marking scheme|answer scheme)', back, re.I))
    papers.append(dict(subject=subject, source=source, year=year, qp=qp[len(root):], key=key[len(root):] if key else None,
                       key_in_paper=key_in_paper))


def year_of(name):
    m = re.search(r'(20\d\d)', name)
    if m:
        return int(m.group(1))
    # "MFSS P1_QP + ANS 24": a two-digit year, last.
    m = re.search(r'\b(\d\d)\D*$', name)
    return 2000 + int(m.group(1)) if m else None


# Biology, BPGHS: Paper 1/<year>/... QP.pdf and ... MS.pdf
d = root + 'Biology/BPGHS Bio Past Year Papers/Paper 1/'
for y in sorted(os.listdir(d)):
    fs = os.listdir(d + y)
    qp = next(f for f in fs if 'QP' in f)
    ms = next((f for f in fs if 'MS' in f), None)
    add('Biology', 'BPGHS', int(y), d + y + '/' + qp, d + y + '/' + ms if ms else None)

# Biology and Physics, other schools: one folder a school, a question paper and maybe a key.
KEY = re.compile(r'(\bMS\b|\[MS\]|mark ?scheme|answer|\bans\b|scheme)', re.I)
for subject, d in (('Biology', root + 'Biology/Other Schools Bio Past Year Papers/Paper 1/'),
                   ('Physics', root + 'Physics/Other Schools Past Year Papers/Other Schools Phys Past Year Papers/')):
    for school in sorted(os.listdir(d)):
        fs = sorted(os.listdir(d + school))
        p1 = [f for f in fs if re.search(r'(p1|paper ?1)', f, re.I) and not re.search(r'paper ?2\b|\bp2\b(?!.*p1)', f, re.I) or re.search(r'P1(&|\+| and )', f)]
        qps = [f for f in p1 if not KEY.search(f) or re.search(r'(QP ?\+ ?ANS|w Ans|\+ Ans|with MS)', f, re.I)]
        # A key for Paper 1: not one for Paper 2 or 3 alone (a combined "P1 & P2" key is fine).
        keys = [f for f in fs if KEY.search(f) and not re.search(r'(QP ?\+ ?ANS|w Ans|\+ Ans|with MS)', f, re.I)
                and not (re.search(r'(paper ?[23]|\bp[23])', f, re.I) and not re.search(r'(paper ?1|p1)', f, re.I))]
        for qp in qps:
            inside = bool(re.search(r'(QP ?\+ ?ANS|w Ans|\+ Ans|with MS)', qp, re.I))
            key = None if inside else (keys[0] if keys else None)
            name = re.sub(r' (Phy|Bio)$', '', re.sub(r' Phy Past Year Papers.*', '', school))
            add(subject, name, year_of(qp), d + school + '/' + qp, d + school + '/' + key if key else None, inside)

# O-Level Chemistry: Paper 1/<year>/..., keys in Answers (2005-2025).
d = root + 'Chemistry/TYS (Yearly) Chem Past Year Papers/Paper 1/'
keys = d + 'Answers (2005-2025)/'
for y in sorted(x for x in os.listdir(d) if x.isdigit()):
    qp = os.listdir(d + y)[0]
    yy = int(y)
    worked = next((f for f in os.listdir(keys) if f.startswith('Copy of %d' % yy)), None)
    if worked:
        key = keys + worked
    else:
        key = keys + next(f for f in os.listdir(keys) if f.startswith('Answer Key') and
                          int(re.findall(r'(\d{4})-(\d{4})', f)[0][0]) <= yy <= int(re.findall(r'(\d{4})-(\d{4})', f)[0][1]))
    add('Chemistry', 'O-Level', yy, d + y + '/' + qp, key)

# O-Level Physics: <year>/<year> O-Level Physics P1 QP.pdf and ... ANS.pdf
d = root + 'Physics/TYS\uff0fO Levels/TYS Phys Past Year Papers/'
for y in sorted(os.listdir(d)):
    fs = os.listdir(d + y)
    add('Physics', 'O-Level', int(y), d + y + '/' + next(f for f in fs if 'P1 QP' in f), d + y + '/' + next(f for f in fs if 'ANS' in f))

# O-Level Biology: the papers have no extension; one compiled key (2016 on; there's no 2015 paper).
d = root + 'Biology/TYS Answers\u2420/Paper 1/'
for f in sorted(os.listdir(d)):
    if f.startswith('GCE O Level Biology'):
        add('Biology', 'O-Level', year_of(f), d + f, d + 'Compiled Paper 1 Answers (2015-2025).pdf.pdf')

os.makedirs('../../work', exist_ok=True)
json.dump(papers, open('../../work/manifest.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
for p in papers:
    print('%-9s %-14s %s  key: %s%s' % (p['subject'], p['source'][:14], p['year'], 'IN PAPER' if p['key_in_paper'] else (os.path.basename(p['key']) if p['key'] else 'NONE')[:60], ''))
print(len(papers), 'papers')
