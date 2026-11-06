#!/bin/sh
# Membungkus src/editor.html menjadi index.html lengkap (untuk GitHub Pages dan APK).
set -e
cd "$(dirname "$0")/.."
{
  printf '<!doctype html>\n<html lang="id">\n<head>\n<meta charset="utf-8">\n'
  printf '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
  printf '<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0}</style>\n'
  printf '<link rel="icon" href="icon.svg">\n</head>\n<body>\n'
  cat src/editor.html
  printf '\n</body>\n</html>\n'
} > index.html
echo "index.html dibuat"
