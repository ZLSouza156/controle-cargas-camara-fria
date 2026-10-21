#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/preparar_web.py
command -v gradle >/dev/null || { echo 'Gradle ausente. Use o workflow do GitHub ou instale Gradle 8.11.1 e JDK 17.' >&2; exit 1; }
mode="teste"
variant="Debug"
signing_dir=""
cleanup() { if [[ -n "$signing_dir" ]]; then rm -rf -- "$signing_dir"; fi; }
trap cleanup EXIT
signing_state="$(python3 - <<'PY'
import os, sys
keys = ['ANDROID_KEYSTORE_BASE64','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD']
found = [bool(os.environ.get(k)) for k in keys]
if any(found) and not all(found):
    sys.exit('Assinatura incompleta: configure os quatro Secrets ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS e ANDROID_KEY_PASSWORD.')
print('release' if all(found) else 'teste')
PY
)"
if [[ "$signing_state" == "release" ]]; then
  umask 077
  signing_dir="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/camara-signing.XXXXXX")"
  export CAMARA_SIGNING_FILE="$signing_dir/release.jks"
  python3 - <<'PY'
import base64, os
from pathlib import Path
Path(os.environ['CAMARA_SIGNING_FILE']).write_bytes(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'], validate=True))
PY
  mode="release"
  variant="Release"
else
  unset CAMARA_SIGNING_FILE
fi
# Lint and compilation run on the real Android toolchain before any artifact is published.
gradle --no-daemon --console=plain ":app:lint${variant}" ":app:assemble${variant}"
mkdir -p dist
apk_source="app/build/outputs/apk/${variant,,}/app-${variant,,}.apk"
[[ -f "$apk_source" ]] || { echo 'A compilação não produziu o APK esperado.' >&2; exit 1; }
apk_name="Controle-Cargas-Camara-Fria-${mode}.apk"
cp "$apk_source" "dist/$apk_name"
(cd dist && sha256sum "$apk_name" > "$apk_name.sha256")
# Verify the APK's actual certificate with the SDK, rather than trusting its filename.
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -n "$sdk_root" && -x "$sdk_root/build-tools/35.0.0/apksigner" ]]; then
  "$sdk_root/build-tools/35.0.0/apksigner" verify "dist/$apk_name"
else
  echo 'apksigner não encontrado no Android SDK 35. A verificação da assinatura é obrigatória.' >&2
  exit 1
fi
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "mode=$mode" >> "$GITHUB_OUTPUT"
  echo "apk_path=dist/$apk_name" >> "$GITHUB_OUTPUT"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### APK gerado: $mode"
    echo "Arquivo: **$apk_name**"
    echo 'Baixe **Controle-Cargas-APK** em **Artifacts**, no final desta execução, e extraia o APK.'
    if [[ "$mode" == "teste" ]]; then
      echo 'Sem os quatro Secrets de assinatura, este é um APK de teste com chave de desenvolvimento fixa.'
      echo 'Para uma versão de uso definitivo, siga a seção de assinatura no README.'
    fi
  } >> "$GITHUB_STEP_SUMMARY"
fi
printf 'APK gerado e assinatura verificada: dist/%s\n' "$apk_name"
