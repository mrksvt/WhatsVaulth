#!/usr/bin/env bash
# =============================================================================
# extract-hook-audit.sh
#
# Mengekstrak APK WhatsApp (apktool / jadx) lalu membandingkan hook ID yang
# dipakai di codebase (class names, nama member obfuscated, resource IDs)
# dengan isi APK hasil ekstrak — untuk mengetahui hook mana yang MASIH RELEVAN
# (masih ada di APK versi baru) dan mana yang HILANG (perlu re-obfuscation).
#
# Usage:
#   ./extract-hook-audit.sh <path-to-apk> [--tool apktool|jadx] [--src <dir>]
#
# Output folder: <nama-apk-tanpa-ext>_extracted
#   (jika sudah ada, script menanyakan Batal / Replace)
#
# =============================================================================
set -uo pipefail

# ---------------------------------------------------------------------------
# Konfigurasi
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR_DEFAULT="$SCRIPT_DIR/app/src/main/java/com/mrksvt/waen"
TOOL_CHOICE=""   # "" = tanya, atau "apktool"/"jadx"
SRC_DIR="$SRC_DIR_DEFAULT"

# ---------------------------------------------------------------------------
# Util
# ---------------------------------------------------------------------------
log()  { printf '[extract] %s\n' "$*"; }
die()  { printf '[extract] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
Usage: ./extract-hook-audit.sh <path-to-apk> [--tool apktool|jadx] [--src <dir>]

  <path-to-apk>  file APK WhatsApp yang akan diekstrak
  --tool         paksa tool ekstrak: apktool (smali) / jadx (java)
                 (jika tidak diberikan dan kedua tool ada, script menanya)
  --src          folder source codebase tempat hook ID dibaca
                 (default: app/src/main/java/com/mrksvt/waen)

Output: <nama-apk>_extracted/  +  laporan audit hook ID di stdout
EOF
}

# Parsing argumen sederhana
APK=""
while [ $# -gt 0 ]; do
    case "$1" in
        --tool) TOOL_CHOICE="${2:-}"; shift 2 ;;
        --src)  SRC_DIR="${2:-}";     shift 2 ;;
        -h|--help) usage; exit 0 ;;
        -*) die "argumen tidak dikenal: $1 (lihat --help)" ;;
        *) APK="$1"; shift ;;
    esac
done

[ -n "$APK" ] || { usage; exit 1; }
[ -f "$APK" ] || die "APK tidak ditemukan: $APK"
[ -d "$SRC_DIR" ] || die "folder source tidak ditemukan: $SRC_DIR"

APK="$(realpath "$APK")"
APK_BASE="$(basename "$APK")"
APK_NAME="${APK_BASE%.apk}"
OUT_DIR="$(dirname "$APK")/${APK_NAME}_extracted"

# ---------------------------------------------------------------------------
# Deteksi tool yang tersedia
# ---------------------------------------------------------------------------
HAS_APKTOOL=0; command -v apktool >/dev/null 2>&1 && HAS_APKTOOL=1
HAS_JADX=0;    command -v jadx    >/dev/null 2>&1 && HAS_JADX=1

if [ -z "$TOOL_CHOICE" ]; then
    if [ "$HAS_APKTOOL" -eq 1 ] && [ "$HAS_JADX" -eq 1 ]; then
        echo "Pilih tool ekstrak:"
        echo "  1) apktool  (smali — disarankan untuk analisis hook)"
        echo "  2) jadx     (java sources)"
        printf "Pilihan [1/2]: "
        read -r ans
        case "${ans:-1}" in
            2|jadx) TOOL_CHOICE="jadx" ;;
            *)      TOOL_CHOICE="apktool" ;;
        esac
    elif [ "$HAS_APKTOOL" -eq 1 ]; then
        log "apktool ditemukan, jadx tidak tersedia — pakai apktool"
        TOOL_CHOICE="apktool"
    elif [ "$HAS_JADX" -eq 1 ]; then
        log "jadx ditemukan, apktool tidak tersedia — pakai jadx"
        TOOL_CHOICE="jadx"
    else
        die "tidak ada tool ekstrak (apktool / jadx). Install salah satunya."
    fi
fi

case "$TOOL_CHOICE" in
    apktool) [ "$HAS_APKTOOL" -eq 1 ] || die "apktool tidak terinstall" ;;
    jadx)    [ "$HAS_JADX" -eq 1 ]    || die "jadx tidak terinstall" ;;
    *) die "tool tidak dikenal: $TOOL_CHOICE (pilih apktool/jadx)" ;;
esac

# ---------------------------------------------------------------------------
# Cek folder hasil ekstrak (hindari double-extract)
# ---------------------------------------------------------------------------
if [ -d "$OUT_DIR" ]; then
    echo
    echo "Folder '$OUT_DIR' sudah ada."
    echo "  1) Batal   (pakai hasil ekstrak yang sudah ada)"
    echo "  2) Replace (hapus lalu ekstrak ulang)"
    printf "Pilihan [1/2]: "
    read -r ans
    case "${ans:-1}" in
        2|r|R|replace) rm -rf "$OUT_DIR"; log "folder lama dihapus, ekstrak ulang..." ;;
        *) log "dibatalkan, memakai hasil ekstrak yang sudah ada" ;;
    esac
fi

# ---------------------------------------------------------------------------
# Ekstrak APK
# ---------------------------------------------------------------------------
if [ ! -d "$OUT_DIR" ] || [ -z "$(ls -A "$OUT_DIR" 2>/dev/null)" ]; then
    mkdir -p "$OUT_DIR"
    log "mengekstrak dengan $TOOL_CHOICE ..."
    case "$TOOL_CHOICE" in
        apktool) apktool d -f -o "$OUT_DIR" "$APK" >/dev/null 2>&1 \
                     || die "apktool gagal mengekstrak $APK" ;;
        jadx)    jadx -d "$OUT_DIR" "$APK" >/dev/null 2>&1 \
                     || die "jadx gagal mengekstrak $APK" ;;
    esac
    log "ekstrak selesai → $OUT_DIR"
else
    log "folder '$OUT_DIR' sudah berisi hasil ekstrak — dilewati"
fi

# ---------------------------------------------------------------------------
# Kumpulkan hook ID dari codebase
# ---------------------------------------------------------------------------
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# 1. Nama member obfuscated (pola A00, A01, A2T, A0J, ...) — string literal
grep -rhoE '"[A-Z][0-9][0-9A-Z]"' "$SRC_DIR" 2>/dev/null \
    | tr -d '"' | sort -u > "$TMP_DIR/ids.txt"

# 2. Class names (com.whatsapp.* + segmen class berhuruf besar)
grep -rhoE '"(com\.whatsapp[a-zA-Z0-9_.]*)"' "$SRC_DIR" 2>/dev/null \
    | tr -d '"' | sort -u \
    | grep -E '\.[A-Z]' > "$TMP_DIR/classes.txt"

# 3. Resource IDs dari Utils.getID("...")
grep -rhoE 'getID\("[^"]*"' "$SRC_DIR" 2>/dev/null \
    | sed 's/getID("//; s/"$//' | sort -u > "$TMP_DIR/resources.txt"

# ---------------------------------------------------------------------------
# Fungsi pencarian per tool
# ---------------------------------------------------------------------------
search_class_apktool() {  # $1 = com.whatsapp.X.Y ; hasil: jumlah file
    local path; path="$(printf '%s' "$1" | tr '.' '/')"
    find "$OUT_DIR" -type f -path "*/smali*/${path}.smali" 2>/dev/null | wc -l
}
search_class_jadx() {
    local path; path="$(printf '%s' "$1" | tr '.' '/')"
    find "$OUT_DIR" -type f -path "*/sources/${path}.java" 2>/dev/null | wc -l
}

search_member_apktool() {  # $1 = A00 ; jumlah file smali WhatsApp yang menyebut
    # scope ke smali*/com/whatsapp/ agar tidak tercemar androidx/dependency
    grep -rlE " $1\(|->$1:" "$OUT_DIR"/smali*/com/whatsapp/ 2>/dev/null | wc -l
}
search_member_jadx() {
    grep -rlE "\b$1\b" "$OUT_DIR"/sources/com/whatsapp/ 2>/dev/null | wc -l
}

search_resource_apktool() {  # $1 = nama resource ; jumlah file values yang mendefinisikan
    local n=0
    if [ -d "$OUT_DIR/res/values" ]; then
        n=$(grep -rl "name=\"$1\"" "$OUT_DIR/res/values/" 2>/dev/null | wc -l)
    fi
    printf '%s' "$n"
}
search_resource_jadx() {
    local n=0
    n=$(grep -rlE "R\.id\.$1\b" "$OUT_DIR"/sources/com/whatsapp/ 2>/dev/null | wc -l)
    if [ "$n" -eq 0 ]; then
        n=$(grep -rlE "int $1=" "$OUT_DIR"/sources/com/whatsapp/*/R.java 2>/dev/null | wc -l)
    fi
    printf '%s' "$n"
}

# ---------------------------------------------------------------------------
# Laporan
# ---------------------------------------------------------------------------
report() { # $1=type $2=id $3=count $4=sample
    local type="$1" id="$2" cnt="$3" sample="${4:--}"
    local status="MISSING"
    [ "$cnt" -gt 0 ] && status="FOUND"
    printf '%-9s %-52s %-7s %5s  %s\n' "$type" "$id" "$status" "$cnt" "$sample"
}

echo
echo "=============================================================="
echo " HOOK ID AUDIT"
echo "=============================================================="
echo " APK      : $APK"
echo " Tool     : $TOOL_CHOICE"
echo " Extract  : $OUT_DIR"
echo " Source   : $SRC_DIR"
echo "--------------------------------------------------------------"
printf '%-9s %-52s %-7s %5s  %s\n' "TYPE" "ID" "STATUS" "COUNT" "SAMPLE"
echo "--------------------------------------------------------------"

TOTAL_FOUND=0
TOTAL_MISSING=0

# --- Class names ---
while IFS= read -r cls; do
    [ -n "$cls" ] || continue
    if [ "$TOOL_CHOICE" = "apktool" ]; then
        cnt=$(search_class_apktool "$cls")
        sample=$(find "$OUT_DIR" -type f -path "*/smali*/$(printf '%s' "$cls" | tr '.' '/').smali" 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
    else
        cnt=$(search_class_jadx "$cls")
        sample=$(find "$OUT_DIR" -type f -path "*/sources/$(printf '%s' "$cls" | tr '.' '/').java" 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
    fi
    report "class" "$cls" "$cnt" "$sample"
    [ "$cnt" -gt 0 ] && TOTAL_FOUND=$((TOTAL_FOUND+1)) || TOTAL_MISSING=$((TOTAL_MISSING+1))
done < "$TMP_DIR/classes.txt"

# --- Nama member obfuscated ---
while IFS= read -r id; do
    [ -n "$id" ] || continue
    if [ "$TOOL_CHOICE" = "apktool" ]; then
        cnt=$(search_member_apktool "$id")
        sample=$(grep -rlE " $id\(|->$id:" "$OUT_DIR"/smali*/com/whatsapp/ 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
    else
        cnt=$(search_member_jadx "$id")
        sample=$(grep -rlE "\b$id\b" "$OUT_DIR"/sources/com/whatsapp/ 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
    fi
    report "member" "$id" "$cnt" "$sample"
    [ "$cnt" -gt 0 ] && TOTAL_FOUND=$((TOTAL_FOUND+1)) || TOTAL_MISSING=$((TOTAL_MISSING+1))
done < "$TMP_DIR/ids.txt"

# --- Resource IDs ---
while IFS= read -r rid; do
    [ -n "$rid" ] || continue
    if [ "$TOOL_CHOICE" = "apktool" ]; then
        cnt=$(search_resource_apktool "$rid")
        sample=$(grep -rl "name=\"$rid\"" "$OUT_DIR/res/values/" 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
        [ -z "$sample" ] && sample="-"
    else
        cnt=$(search_resource_jadx "$rid")
        sample=$(grep -rlE "R\.id\.$rid\b" "$OUT_DIR"/sources/com/whatsapp/ 2>/dev/null | head -1 | sed "s|$OUT_DIR/||")
        [ -z "$sample" ] && sample="-"
    fi
    report "resource" "$rid" "$cnt" "$sample"
    [ "$cnt" -gt 0 ] && TOTAL_FOUND=$((TOTAL_FOUND+1)) || TOTAL_MISSING=$((TOTAL_MISSING+1))
done < "$TMP_DIR/resources.txt"

echo "--------------------------------------------------------------"
echo " RINGKASAN : $TOTAL_FOUND FOUND / $TOTAL_MISSING MISSING"
echo "=============================================================="

# ---------------------------------------------------------------------------
# Catatan akhir
# ---------------------------------------------------------------------------
if [ "$TOTAL_MISSING" -gt 0 ]; then
    echo
    echo "⚠  Hook MISSING kemungkinan besar RUSAK pada APK versi ini."
    echo "   Periksa ulang via Unobfuscator / DexKit (metode non-hardcode)."
    echo "   Buka hasil ekstrak: $OUT_DIR"
fi