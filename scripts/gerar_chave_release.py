#!/usr/bin/env python3
"""Generate your private signing key locally; never commit this output."""
import base64
import getpass
import os
from pathlib import Path
import shutil
import subprocess
import sys
root = Path(__file__).resolve().parents[1]
if not shutil.which('keytool'):
    sys.exit('Instale um JDK 17 para obter o comando keytool.')
folder = root / 'private-signing'
folder.mkdir(mode=0o700, exist_ok=True)
key = folder / 'camara-fria-release.jks'
if key.exists():
    sys.exit('A chave já existe. Ela não será sobrescrita. Preserve a chave original para futuras atualizações.')
password = getpass.getpass('Crie uma senha com pelo menos 8 caracteres: ')
if len(password) < 8 or password != getpass.getpass('Confirme a senha: '):
    sys.exit('Senha curta ou confirmação diferente. Nenhuma chave foi criada.')
env = dict(os.environ, CAMARA_NEW_KEY_PASSWORD=password)
subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storetype','JKS',
    '-storepass:env','CAMARA_NEW_KEY_PASSWORD','-keypass:env','CAMARA_NEW_KEY_PASSWORD',
    '-alias','camara-fria','-keyalg','RSA','-keysize','2048','-validity','10000',
    '-dname','CN=Controle de Cargas Camara Fria,O=Camara Fria,C=BR','-noprompt'], env=env, check=True)
key.chmod(0o600)
encoded = folder / 'ANDROID_KEYSTORE_BASE64.txt'
encoded.write_text(base64.b64encode(key.read_bytes()).decode('ascii'), encoding='ascii')
encoded.chmod(0o600)
print('Chave criada em private-signing/. Não envie esta pasta ao GitHub.')
print('Configure os quatro Secrets conforme o README:')
print('ANDROID_KEYSTORE_BASE64: conteúdo do arquivo ANDROID_KEYSTORE_BASE64.txt')
print('ANDROID_KEYSTORE_PASSWORD: a senha que você acabou de criar')
print('ANDROID_KEY_ALIAS: camara-fria')
print('ANDROID_KEY_PASSWORD: a mesma senha')
