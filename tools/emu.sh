#!/usr/bin/env bash
# Utilitário de desenvolvimento: compila o debug, instala no emulador e tira capturas.
#   tools/emu.sh build          compila e instala o APK de debug
#   tools/emu.sh menu           abre o menu
#   tools/emu.sh mode N         abre direto o modo N (0 partículas, 1 fluido, 2 escuro)
#   tools/emu.sh shot nome      captura a tela em $OUT/nome.png (e uma versão reduzida _s.png)
#   tools/emu.sh key CODE       envia uma tecla (ex.: 24 = volume+, 4 = voltar)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK=/d/Trabalho/Code/_toolchain/sdk
ADB=$SDK/platform-tools/adb.exe
export JAVA_HOME=/d/Trabalho/Code/_toolchain/jdk17
OUT=${OUT:-"$ROOT/build/emu"}
OUTW=$(cygpath -w "$OUT" 2>/dev/null || echo "$OUT")
PKG=br.com.rfranklin.particulas
case "$1" in
  build)
    cd "$ROOT" && ./gradlew.bat assembleDebug --console=plain -q 2>&1 | grep -E "^(e:|w:)" || true
    $ADB install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | tail -1 ;;
  menu) $ADB shell am start -n $PKG/.MenuActivity >/dev/null ;;
  mode)  # a MainActivity não é exportada: abre o menu e toca no botão do modo
    $ADB shell am start -n $PKG/.MenuActivity >/dev/null; sleep 1.5
    $ADB shell input tap 858 2307; sleep 0.8      # "Got it" do diálogo de fixação (cai no vazio se não houver)
    case "$2" in 0) y=630;; 1) y=1300;; *) y=1970;; esac
    $ADB shell input tap 540 $y ;;
  stop) $ADB shell am force-stop $PKG ;;
  key)  $ADB shell input keyevent "$2" ;;
  shot)
    mkdir -p "$OUT"
    $ADB exec-out screencap -p > "$OUT/$2.png"
    python -c "from PIL import Image; im=Image.open(r'$OUTW/$2.png'); im.resize((im.width//3, im.height//3)).save(r'$OUTW/$2_s.png')" ;;
  log)  $ADB logcat -d -b crash | tail -40 ;;
esac
