"""Builds the private content pack the app reads, from the cut questions and found answers.

    python pack.py [OUT_DIR]   (default: ../../content/pack)

OUT_DIR/index.json lists the papers and each science's topics; OUT_DIR/<id>/paper.json has each
question's answer, topics (topics.py) and image (and explanation image, when there's one); the
images are gray WebP. A question goes in
only with both its picture and its answer, and not when content/review.json (questions looked over
by eye) says its picture gives the answer away or holds another question too; a paper only with
enough questions.
"""
import json
import os
import shutil
import sys

import cv2
import numpy as np

import words

WORK = words.WORK
WIDTH = 1100        # widest a question's picture is kept (pixels): plenty for a phone, zoomed in
QUALITY = 75
LEVELS = 4          # grays a printed question is kept in
INK, PAPER = 40, 215
PHOTO = 0.15        # more of a picture than this in mid grays: a photograph, kept lossy
MIN_QUESTIONS = 20  # a paper with fewer good questions than this is left out, to look at again


def webp(src, dst):
    """A question's picture, small: print (black on white, a few grays at its edges) in LEVELS grays,
    losslessly, which keeps it sharp at a third of the size; a picture with much gray between (a
    photograph, a shaded diagram) as it is, lossily."""
    img = cv2.imread(src, cv2.IMREAD_GRAYSCALE)
    if img.shape[1] > WIDTH:
        img = cv2.resize(img, (WIDTH, int(img.shape[0] * WIDTH / img.shape[1])), interpolation=cv2.INTER_AREA)
    if ((img > 70) & (img < 190)).mean() > PHOTO:
        cv2.imwrite(dst, img, [cv2.IMWRITE_WEBP_QUALITY, QUALITY])
        return
    # Ink to black and paper to white, then LEVELS grays.
    x = np.clip((img.astype(np.float32) - INK) * 255 / (PAPER - INK), 0, 255)
    x = (np.round(x / 255 * (LEVELS - 1)) * (255 / (LEVELS - 1))).astype(np.uint8)
    cv2.imwrite(dst, x, [cv2.IMWRITE_WEBP_QUALITY, 101])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'pack')
    os.makedirs(out, exist_ok=True)
    index = []
    path = os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'review.json')
    review = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    for p in json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        d = os.path.join(WORK, pid)
        qdir = os.path.join(d, 'q')
        apath = os.path.join(d, 'answers.json')
        if not (os.path.isdir(qdir) and os.path.exists(apath)):
            continue
        answers = json.load(open(apath))
        # From topics.py: the chapters of its science each question covers, as places in the list.
        tpath = os.path.join(d, 'topics.json')
        topics = json.load(open(tpath)) if os.path.exists(tpath) else {}
        left_out = {n for v in review.get(pid, {}).values() for n in v}
        qs = []
        # Afresh: a question left out since the last pack mustn't stay behind in it.
        shutil.rmtree(os.path.join(out, pid), ignore_errors=True)
        os.makedirs(os.path.join(out, pid), exist_ok=True)
        for n in range(1, 61):
            src = os.path.join(qdir, '%02d.png' % n)
            if not os.path.exists(src) or str(n) not in answers or n in left_out:
                continue
            webp(src, os.path.join(out, pid, '%02d.webp' % n))
            q = {'n': n, 'answer': answers[str(n)], 'image': '%02d.webp' % n, 'topics': topics.get(str(n), [])}
            ex = os.path.join(d, 'explain', '%02d.png' % n)
            if os.path.exists(ex):
                webp(ex, os.path.join(out, pid, 'e%02d.webp' % n))
                q['explain'] = 'e%02d.webp' % n
            qs.append(q)
        if len(qs) < MIN_QUESTIONS:
            print('%-28s left out: %d questions' % (pid, len(qs)))
            continue
        json.dump({'id': pid, 'questions': qs}, open(os.path.join(out, pid, 'paper.json'), 'w'), indent=0)
        index.append({'id': pid, 'subject': p['subject'], 'source': p['source'], 'year': p['year'], 'count': len(qs)})
        print('%-28s %d questions' % (pid, len(qs)))
    names = os.path.join(WORK, 'topics.json')
    json.dump({'version': 2, 'papers': index, 'topics': json.load(open(names)) if os.path.exists(names) else {}},
              open(os.path.join(out, 'index.json'), 'w'), indent=1)
    print(len(index), 'papers,', sum(p['count'] for p in index), 'questions')


if __name__ == '__main__':
    main()
