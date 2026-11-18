#!/usr/bin/env python3
"""Verify and upload a release candidate over SSH; leave publication to the bot owner."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import uuid


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--build-commit')
    args=parser.parse_args()
    config=json.loads(args.config.read_text())
    apk=args.apk.resolve(strict=True)
    build_tools=Path(config.get('build_tools','/opt/android-sdk/build-tools/36.0.0'))
    badging=subprocess.check_output([str(build_tools/'aapt'),'dump','badging',str(apk)],text=True)
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    assert package and package[1]==config['package'], 'Unexpected package'
    assert 'application-debuggable' not in badging, 'Only a release APK may be staged'
    verification=subprocess.check_output([str(build_tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
    signers=re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)',verification)
    assert signers==[config['certificate_sha256']], 'Unexpected APK signing certificate'
    sha=hashlib.sha256()
    with apk.open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''):
            sha.update(chunk)
    commit=args.build_commit or subprocess.check_output(['git','rev-parse','HEAD'],cwd=Path(__file__).resolve().parents[1],text=True).strip()
    assert re.fullmatch(r'[a-f0-9]{40}',commit), 'Invalid build commit'
    receipt={'package':package[1],'versionCode':int(package[2]),'versionName':package[3],
             'sha256':sha.hexdigest(),'size':apk.stat().st_size,'certificate_sha256':signers[0],
             'buildCommit':commit}
    destination=config['destination']
    assert re.fullmatch(r'[a-zA-Z0-9_.-]+@[a-zA-Z0-9_.:-]+',destination), 'Invalid SSH destination'
    directory=config['directory']
    assert re.fullmatch(r'/home/devgram/incoming(?:/[a-zA-Z0-9_-]+)*',directory), 'Staging must stay in the private incoming directory'
    ssh=['ssh','-i',str(Path(config['identity_file']).expanduser()),'-p',str(int(config.get('port',443))),
         '-o','BatchMode=yes','-o','ConnectTimeout=15','-o','IPQoS=none','-o','StrictHostKeyChecking=yes']
    # The SSH key is read by ssh itself. Its bytes never enter this program or its output.
    setup='import os,pwd,pathlib; p=pathlib.Path('+repr(directory)+'); p.mkdir(parents=True,exist_ok=True); a=pwd.getpwnam("devgram"); os.chown(p,0,a.pw_gid); p.chmod(0o750)'
    subprocess.run(ssh+[destination,'python3 -'],input=setup.encode(),check=True)
    incoming_name='.incoming-'+uuid.uuid4().hex+'.apk'
    subprocess.run(['rsync','--archive','--no-owner','--no-group','--chmod=F600',
                    '-e',shlex.join(ssh),str(apk),destination+':'+directory+'/'+incoming_name],check=True)
    receiver=Path(__file__).with_name('receive_beta_apk.py').read_text()
    payload={'directory':directory,'incoming_name':incoming_name,'receipt':receipt}
    remote=receiver+'\nreceive(**json.loads('+repr(json.dumps(payload))+'))\n'
    subprocess.run(ssh+[destination,'python3 -'],input=remote.encode(),check=True)
    print('Beta candidate is ready on the VPS. The live beta was not published.')


if __name__=='__main__':
    main()
