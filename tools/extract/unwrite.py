"""Takes a student's writing off scanned papers (answers circled, crossed out, worked on), with
Oscanner's handwriting network (core/imaging/src/main/assets/clean/hand.onnx).

    python unwrite.py HAND_ONNX [PROCESSES] [paper-id ...]

For each page image of each paper (all of them, or those named): finds the writing, and if there's
more than a speck, writes pNN_clean.png beside pNN.png with it filled in with the paper around it,
which crop.py then cuts from. Printed words (where words.json has them) are never taken out, so a
circled letter keeps its print. Prints how much each paper had.
"""
import json
import os
import sys
from multiprocessing import Pool

for _var in ('OMP_NUM_THREADS', 'OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS'):
    os.environ[_var] = '1'

import cv2  # noqa: E402
import numpy as np  # noqa: E402

cv2.setNumThreads(1)

WORK = os.path.join(os.path.dirname(__file__), '..', '..', 'work')
THREADS = 2
SPECK = 0.0004   # less of the page than this marked as writing: left as it is
TILE = 768       # the network's input at most, a side (pixels), as the app's
MARGIN = 64      # what each tile sees past its share
_session = None


def network(path):
    global _session
    if _session is None:
        import onnxruntime as ort
        options = ort.SessionOptions()
        options.intra_op_num_threads = THREADS
        options.inter_op_num_threads = 1
        _session = ort.InferenceSession(path, options, providers=['CPUExecutionProvider'])
    return _session


def probability(session, rgb):
    """In tiles, as the app does (HandMask.kt): a whole page at once wants gigabytes. Each tile
    reaches MARGIN past its share, so a stroke cut at its edge is judged with what's beside it."""
    h, w = rgb.shape[:2]
    out = np.zeros((h, w), np.float32)
    for y in range(0, h, TILE - 2 * MARGIN):
        for x in range(0, w, TILE - 2 * MARGIN):
            y0, x0 = max(0, y - MARGIN), max(0, x - MARGIN)
            tile = rgb[y0:min(h, y + TILE - MARGIN), x0:min(w, x + TILE - MARGIN)]
            th, tw = tile.shape[:2]
            # Sizes divisible by 16 for the network's four halvings.
            t = cv2.copyMakeBorder(tile, 0, (16 - th % 16) % 16, 0, (16 - tw % 16) % 16, cv2.BORDER_REPLICATE)
            logit = session.run(None, {'rgb': t.astype(np.float32).transpose(2, 0, 1)[None] / 255})[0][0, 0, :th, :tw]
            # Only the tile's own share: its margins belong to its neighbours.
            sy, sx = y - y0, x - x0
            share = logit[sy:sy + TILE - 2 * MARGIN, sx:sx + TILE - 2 * MARGIN]
            out[y:y + share.shape[0], x:x + share.shape[1]] = share
    return 1 / (1 + np.exp(-out))


def writing(prob, gray, printed):
    """Where the writing is, as Oscanner finds it: strokes it's sure of, and the less sure parts
    joined to them, with their soft rims; never ink it's sure is print, nor a printed word."""
    sure = (prob > 0.5).astype(np.uint8)
    maybe = (prob > 0.15).astype(np.uint8)
    n, ids = cv2.connectedComponents(maybe, connectivity=8)
    keep = np.zeros(n, bool)
    keep[np.unique(ids[sure > 0])] = True
    keep[0] = False
    mask = (keep[ids] * 255).astype(np.uint8)
    mask = cv2.dilate(mask, np.ones((3, 3), np.uint8))
    ink = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV,
                                max(15, max(gray.shape) // 70 | 1), 12) > 0
    mask[ink & (prob < 0.1)] = 0
    h, w = gray.shape
    for x0, y0, x1, y1 in printed:
        mask[int(y0 * h) + 1:int(y1 * h) - 1, int(x0 * w) + 1:int(x1 * w) - 1] = 0
    return mask


def filled(gray, mask):
    """[gray] with [mask] filled with the paper around it."""
    ink = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 31, 6)
    holes = cv2.dilate(ink | mask, np.ones((5, 5), np.uint8))
    k = 600 / max(gray.shape)
    small = cv2.resize(gray, None, fx=k, fy=k, interpolation=cv2.INTER_AREA)
    sh = (cv2.resize(holes, (small.shape[1], small.shape[0]), interpolation=cv2.INTER_AREA) > 0).astype(np.uint8) * 255
    paper = cv2.inpaint(small, sh, 3, cv2.INPAINT_TELEA)
    paper = cv2.resize(cv2.GaussianBlur(paper, (5, 5), 0), (gray.shape[1], gray.shape[0]))
    out = gray.copy()
    out[mask > 0] = paper[mask > 0]
    return out


def job(args):
    onnx, pid = args
    d = os.path.join(WORK, pid)
    pages = json.load(open(os.path.join(d, 'words.json'), encoding='utf-8'))
    session = network(onnx)
    share = []
    for i, ws in enumerate(pages):
        gray = cv2.imread(os.path.join(d, 'p%02d.png' % i), cv2.IMREAD_GRAYSCALE)
        out = os.path.join(d, 'p%02d_clean.png' % i)
        if os.path.exists(out):
            os.remove(out)
        if gray is None:
            continue
        # Printed words only: a word OCR read off the writing is no reason to keep it.
        printed = [w[1:] for w in ws if len(w[0]) >= 2 or w[0].isalpha()]
        mask = writing(probability(session, cv2.cvtColor(gray, cv2.COLOR_GRAY2RGB)), gray, printed)
        part = (mask > 0).mean()
        share.append(part)
        if part > SPECK:
            cv2.imwrite(out, filled(gray, mask))
    return pid, share


def main():
    onnx = sys.argv[1]
    args = sys.argv[2:]
    processes = int(args.pop(0)) if args and args[0].isdigit() else 24
    ids = args or sorted(x for x in os.listdir(WORK) if os.path.exists(os.path.join(WORK, x, 'words.json')))
    with Pool(processes) as pool:
        for pid, share in pool.imap_unordered(job, [(onnx, pid) for pid in ids]):
            cleaned = [i for i, s in enumerate(share) if s > SPECK]
            print('%-28s %d of %d pages written on%s' % (pid, len(cleaned), len(share),
                  (': ' + ' '.join('p%d %.2f%%' % (i, share[i] * 100) for i in cleaned)) if cleaned else ''), flush=True)


if __name__ == '__main__':
    main()
