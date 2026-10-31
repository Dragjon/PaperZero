"""words.py and keywords.py for many papers at once, for a machine with cores to spare.

    python farm.py BACKUP_DIR [PROCESSES] [--sharp | --margins paper-id ... | --upright paper-id ...]

--sharp reads the O-Level keys again, larger (SHARP dpi), a page a job, into
work/keys/<key>/words<SHARP>.json: a second reading for answers.py to fill the first's gaps
from and check it against. --margins does margins.py's work for the papers named.

--upright is for papers scanned two pages to a sheet, turned on their side (Chemistry
2005-2008): each sheet is turned whichever way OCR reads better, cut into its two pages, and
read again; the sheets as they were are kept in work/<id>/sheets/.

Each process reads one paper or key at a time, OCR limited to two threads so the processes
don't trip over each other. Skips what's already done, like the two scripts it stands for.
"""
import json
import os
import sys
from multiprocessing import Pool

# One thread each for the maths libraries' own pools, in every process.
for _var in ('OMP_NUM_THREADS', 'OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS'):
    os.environ[_var] = '1'

import onnxruntime  # noqa: E402

THREADS = 2

# Every OCR session on THREADS threads: ONNX Runtime otherwise takes one a core, in every process.
_session = onnxruntime.InferenceSession


def _limited(path, sess_options=None, *args, **kwargs):
    options = sess_options or onnxruntime.SessionOptions()
    options.intra_op_num_threads = THREADS
    options.inter_op_num_threads = 1
    return _session(path, options, *args, **kwargs)


onnxruntime.InferenceSession = _limited

import cv2  # noqa: E402
import fitz  # noqa: E402
import numpy as np  # noqa: E402

cv2.setNumThreads(1)

import keywords
import margins
import words


def ocr_limited():
    from rapidocr_onnxruntime import RapidOCR
    return RapidOCR()


def read(path, out, margins):
    """Pages of the PDF at [path] into [out]: images and words, as words.py does."""
    os.makedirs(out, exist_ok=True)
    pages = []
    for i, page in enumerate(fitz.open(path, filetype='pdf')):
        pix = page.get_pixmap(dpi=words.DPI, colorspace=fitz.csGRAY)
        gray = np.frombuffer(pix.samples, np.uint8).reshape(pix.h, pix.w).copy()
        cv2.imwrite(os.path.join(out, 'p%02d.png' % i), gray)
        ws = words.text_words(page)
        if len(ws) < 30:
            ws = words.unglue(words.ocr_words(gray))
            if margins:
                ws += words.margin_numbers(gray, ws)
        pages.append(ws)
    json.dump(pages, open(os.path.join(out, 'words.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    return out, len(pages)


def job(args):
    words._ocr = words._ocr or ocr_limited()
    try:
        return read(*args)
    except Exception as e:
        return args[1], 'FAILED %s' % e


SHARP = 300


def sharp_job(args):
    path, i = args
    words._ocr = words._ocr or ocr_limited()
    page = fitz.open(path, filetype='pdf')[i]
    pix = page.get_pixmap(dpi=SHARP, colorspace=fitz.csGRAY)
    gray = np.frombuffer(pix.samples, np.uint8).reshape(pix.h, pix.w).copy()
    return path, i, words.unglue(words.ocr_words(gray))


def sharp(root, papers, processes):
    keys = sorted({p['key'] for p in papers if p['source'] == 'O-Level' and p['key']})
    jobs = [(root + k, i) for k in keys for i in range(len(fitz.open(root + k, filetype='pdf')))]
    print(len(jobs), 'pages of', len(keys), 'keys with', processes, 'processes', flush=True)
    found = {}
    with Pool(processes) as pool:
        for path, i, ws in pool.imap_unordered(sharp_job, jobs):
            found.setdefault(path, {})[i] = ws
    for k in keys:
        pages = found[root + k]
        out = os.path.join(keywords.KEYS, keywords.key_id(k))
        os.makedirs(out, exist_ok=True)
        json.dump([pages[i] for i in sorted(pages)], open(os.path.join(out, 'words%d.json' % SHARP), 'w', encoding='utf-8'), ensure_ascii=False)
        print(keywords.key_id(k), len(pages), flush=True)


def margins_job(args):
    root, p = args
    words._ocr = words._ocr or ocr_limited()
    try:
        return words.paper_id(p), margins.add(root, p)
    except Exception as e:
        return words.paper_id(p), 'FAILED %s' % e


def legibility(gray):
    """How much OCR reads in [gray], smaller: confident letters."""
    small = cv2.resize(gray, None, fx=0.5, fy=0.5, interpolation=cv2.INTER_AREA)
    result, _ = words.ocr()(small)
    return sum(len(text) * float(score) for _, text, score in result or [])


def upright_job(args):
    sheet = args
    words._ocr = words._ocr or ocr_limited()
    gray = cv2.imread(sheet, cv2.IMREAD_GRAYSCALE)
    if gray.shape[1] > gray.shape[0]:
        turned = [gray]
    else:
        turned = [cv2.rotate(gray, cv2.ROTATE_90_COUNTERCLOCKWISE), cv2.rotate(gray, cv2.ROTATE_90_CLOCKWISE)]
    best = max(turned, key=legibility) if len(turned) > 1 else turned[0]
    half = best.shape[1] // 2
    pages = [best[:, :half], best[:, half:]]
    return sheet, [(p, words.unglue(words.ocr_words(p))) for p in pages]


def upright(pids, processes):
    jobs = []
    for pid in pids:
        d = os.path.join(words.WORK, pid)
        kept = os.path.join(d, 'sheets')
        if not os.path.isdir(kept):
            os.makedirs(kept)
            for f in sorted(os.listdir(d)):
                if f.startswith('p') and f.endswith('.png'):
                    os.replace(os.path.join(d, f), os.path.join(kept, f))
        jobs += [os.path.join(kept, f) for f in sorted(os.listdir(kept)) if f.endswith('.png') and '_' not in f]
    done = {}
    with Pool(processes) as pool:
        for sheet, halves in pool.imap_unordered(upright_job, jobs):
            done[sheet] = halves
            print(os.path.relpath(sheet, words.WORK), flush=True)
    for pid in pids:
        d = os.path.join(words.WORK, pid)
        kept = os.path.join(d, 'sheets')
        out = []
        for f in sorted(os.listdir(kept)):
            out += done.get(os.path.join(kept, f), [])
        for f in os.listdir(d):
            if f.startswith('p') and f.endswith('.png'):
                os.remove(os.path.join(d, f))
        for i, (img, _) in enumerate(out):
            cv2.imwrite(os.path.join(d, 'p%02d.png' % i), img)
        json.dump([ws for _, ws in out], open(os.path.join(d, 'words.json'), 'w', encoding='utf-8'), ensure_ascii=False)
        print(pid, len(out), 'pages', flush=True)


def main():
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    args = [a for a in sys.argv[2:] if not a.startswith('--')]
    processes = int(args[0]) if args and args[0].isdigit() else 24
    papers = json.load(open(os.path.join(words.WORK, 'manifest.json'), encoding='utf-8'))
    if '--upright' in sys.argv:
        upright(args[1:] if args and args[0].isdigit() else args, processes)
        return
    if '--margins' in sys.argv:
        named = set(args[1:] if args and args[0].isdigit() else args)
        jobs = [(root, p) for p in papers if words.paper_id(p) in named]
        with Pool(processes) as pool:
            for pid, added in pool.imap_unordered(margins_job, jobs):
                print(pid, added, flush=True)
        return
    if '--sharp' in sys.argv:
        sharp(root, papers, processes)
        return
    jobs, keys = [], set()
    for p in papers:
        out = os.path.join(words.WORK, words.paper_id(p))
        if not os.path.exists(os.path.join(out, 'words.json')):
            jobs.append((root + p['qp'], out, True))
        if p['key'] and p['key'] not in keys:
            keys.add(p['key'])
            out = os.path.join(keywords.KEYS, keywords.key_id(p['key']))
            if not os.path.exists(os.path.join(out, 'words.json')):
                jobs.append((root + p['key'], out, False))
    print(len(jobs), 'to read with', processes, 'processes', flush=True)
    with Pool(processes) as pool:
        for out, pages in pool.imap_unordered(job, jobs):
            print(os.path.basename(out), pages, flush=True)


if __name__ == '__main__':
    main()
