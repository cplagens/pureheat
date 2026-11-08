#!/usr/bin/env bash
# PureHeat – render the Play Store graphics from docs/store/*.svg with headless Chrome (macOS):
# icon-512.png (512 × 512, 32-bit PNG) and feature-graphic-{en,de}.jpg (1024 × 500, no alpha).
set -eu
cd "$(dirname "$0")/../docs/store"
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
render() { # svg, width, height, png
  # Transparent page background: the PNG gets an alpha channel (Play wants a 32-bit icon).
  "$chrome" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --default-background-color=00000000 \
    --window-size="$2,$3" --screenshot="$PWD/$4" "file://$PWD/$1" > /dev/null 2>&1
}
render icon.svg 512 512 icon-rgb.png
# Chrome drops the alpha channel of opaque images; Play wants a 32-bit PNG icon.
ffmpeg -loglevel error -y -i icon-rgb.png -pix_fmt rgba icon-512.png
rm icon-rgb.png
for lang in en de; do
  render "feature-graphic-$lang.svg" 1024 500 "feature-graphic-$lang.png"
  # Play wants the feature graphic without alpha: JPEG.
  sips -s format jpeg -s formatOptions 92 "feature-graphic-$lang.png" --out "feature-graphic-$lang.jpg" > /dev/null
  rm "feature-graphic-$lang.png"
done
sips -g pixelWidth -g pixelHeight -g hasAlpha icon-512.png feature-graphic-en.jpg feature-graphic-de.jpg | grep -v '^/'
