"""Uploads the built site (web/dist) to the hosting over FTP (with TLS when the server offers it).

    python deploy_ftp.py HOST USER [REMOTE_DIR]

The password is asked for when it runs (or read from FTP_PASSWORD): it's never kept in a file.
Only files that are new or a different size from what's there already go up, so later runs are
quick. REMOTE_DIR defaults to the FTP account's own folder; files outside it are never touched,
and nothing is deleted.
"""
import ftplib
import getpass
import os
import sys

DIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dist')


def connect(host, user, password):
    try:
        ftp = ftplib.FTP_TLS(host, timeout=60)
        ftp.login(user, password)
        ftp.prot_p()
        print('Connected with TLS.')
    except ftplib.error_perm:
        raise
    except Exception:
        ftp = ftplib.FTP(host, timeout=60)
        ftp.login(user, password)
        print('Connected (the server offers no TLS).')
    return ftp


def sizes(ftp, path):
    """What's in [path] on the server: {name: size, or None for a folder}."""
    out = {}
    try:
        for name, facts in ftp.mlsd(path, facts=['type', 'size']):
            if name in ('.', '..'):
                continue
            out[name] = None if facts.get('type') == 'dir' else int(facts.get('size', -1))
    except ftplib.error_perm:
        pass
    return out


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    host, user = sys.argv[1], sys.argv[2]
    remote = sys.argv[3] if len(sys.argv) > 3 else ''
    if not os.path.isdir(DIST):
        sys.exit('No web/dist: run npm run build first.')
    password = os.environ.get('FTP_PASSWORD') or getpass.getpass(f'FTP password for {user}@{host}: ')
    ftp = connect(host, user, password)
    root = ftp.pwd()
    print(f'The account\'s folder on the server: {root}')
    top = sizes(ftp, root)
    print('In it now:', ', '.join(sorted(top)) or '(empty)')
    if not remote and 'public_html' in top:
        sys.exit('This FTP account sees the whole hosting (there\'s a public_html here). Run again '
                 'with the folder the site should go in, e.g.  public_html/paperzero')
    base = '/'.join(p for p in (root.rstrip('/'), remote.strip('/')) if p) or '/'
    sent = skipped = 0
    for here, dirs, files in os.walk(DIST):
        rel = os.path.relpath(here, DIST).replace(os.sep, '/')
        target = base if rel == '.' else f'{base}/{rel}'
        try:
            ftp.mkd(target)
        except ftplib.error_perm:
            pass
        there = sizes(ftp, target)
        for f in sorted(files):
            local = os.path.join(here, f)
            if there.get(f) == os.path.getsize(local):
                skipped += 1
                continue
            with open(local, 'rb') as fh:
                ftp.storbinary(f'STOR {target}/{f}', fh)
            sent += 1
            if sent % 100 == 0:
                print(f'  {sent} files sent…', flush=True)
    ftp.quit()
    print(f'Done: {sent} files sent, {skipped} already there, into {base}')


if __name__ == '__main__':
    main()
