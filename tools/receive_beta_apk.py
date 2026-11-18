"""Atomic private APK staging on the VPS. This module never publishes the live beta."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import tempfile


def digest(path):
    h=hashlib.sha256()
    with open(path,'rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):
            h.update(chunk)
    return h.hexdigest()


def promote(directory, incoming_name, receipt, owner='devgram'):
    directory=Path(directory)
    if not re.fullmatch(r'\.incoming-[a-f0-9]+\.apk',incoming_name):
        raise ValueError('Invalid incoming filename')
    incoming=directory/incoming_name
    assert digest(incoming)==receipt['sha256'], 'Uploaded APK hash mismatch'
    account=pwd.getpwnam(owner) if owner else None
    def protect(path):
        os.chmod(path,0o640)
        if account:
            os.chown(path,0,account.pw_gid)
    lock=os.open(directory/'.candidate.lock',os.O_CREAT|os.O_RDWR,0o600)
    try:
        fcntl.flock(lock,fcntl.LOCK_EX)
        current=directory/'latest.json'
        if current.exists():
            previous=json.loads(current.read_text())
            assert receipt['versionCode']>=previous['versionCode'], 'Refusing to stage an older version'
            if receipt['versionCode']==previous['versionCode']:
                assert receipt['sha256']==previous['sha256'], 'Version code reused for a different APK'
        version=receipt['versionCode']
        filename=f'DevGram-{version}-{receipt["sha256"][:12]}.apk'
        target=directory/filename
        if target.exists():
            assert digest(target)==receipt['sha256'], 'Versioned candidate has unexpected bytes'
            incoming.unlink()
        else:
            os.replace(incoming,target)
        protect(target)
        receipt=dict(receipt,candidate=str(target))
        handle,tmp=tempfile.mkstemp(prefix='.receipt-',dir=directory)
        try:
            with os.fdopen(handle,'w') as output:
                json.dump(receipt,output,ensure_ascii=False,indent=2)
                output.flush()
                os.fsync(output.fileno())
            protect(tmp)
            # Resolve the versioned pathname once in the bot before reading it.
            link=directory/('.latest-'+receipt['sha256'][:12])
            if link.is_symlink():
                link.unlink()
            link.symlink_to(filename)
            os.replace(link,directory/'latest.apk')
            os.replace(tmp,current)
        finally:
            if os.path.exists(tmp):
                os.unlink(tmp)
        return receipt
    finally:
        os.close(lock)


def receive(directory,incoming_name,receipt):
    if not re.fullmatch(r'\.incoming-[a-f0-9]+\.apk',incoming_name):
        raise ValueError('Invalid incoming filename')
    incoming=Path(directory)/incoming_name
    try:
        badging=subprocess.check_output(['/usr/bin/aapt','dump','badging',str(incoming)],text=True)
        package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
        assert package and package[1]==receipt['package'] and int(package[2])==receipt['versionCode'] and package[3]==receipt['versionName'], 'APK metadata mismatch'
        assert 'application-debuggable' not in badging, 'Debug APK cannot be staged'
        result=promote(directory,incoming_name,receipt)
        print(json.dumps({k:result[k] for k in ['versionCode','versionName','sha256','candidate']}))
    finally:
        incoming.unlink(missing_ok=True)
