#!/usr/bin/env python3
"""
make-gif.py — assemble PNG frames into an optimised animated GIF.

    python3 tools/make-gif.py <frames-dir> <out.gif> [--fps 16] [--colors 200]

Used by tools/capture.js. It exists because the ffmpeg that ships with
Playwright is a stripped build with no GIF muxer and no palettegen/paletteuse
filters, so the palette work happens here instead.

Strategy: sample frames across the whole clip, build ONE adaptive palette from
that sample, then map every frame to it. A per-frame palette would shimmer as
the colour table changed underneath a static background.
"""
import argparse
import pathlib
import sys

try:
    from PIL import Image
except ImportError:                                     # pragma: no cover
    sys.exit("Pillow is required:  pip install Pillow")


def build_palette(frames, colors):
    """One shared palette, derived from a strip of evenly spaced frames."""
    step = max(1, len(frames) // 12)
    sample = frames[::step][:12]
    tiles = [Image.open(f).convert("RGB") for f in sample]
    w, h = tiles[0].size
    strip = Image.new("RGB", (w, h * len(tiles)))
    for i, tile in enumerate(tiles):
        strip.paste(tile, (0, i * h))
    return strip.quantize(colors=colors, method=Image.MEDIANCUT)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("frames_dir")
    ap.add_argument("out")
    ap.add_argument("--fps", type=int, default=16)
    ap.add_argument("--colors", type=int, default=144)
    ap.add_argument("--dither", action="store_true",
                    help="Floyd-Steinberg. Off by default: dithering makes every "
                         "static pixel jitter frame to frame, which destroys GIF "
                         "delta compression and multiplies the file size.")
    args = ap.parse_args()

    frames = sorted(pathlib.Path(args.frames_dir).glob("*.png"))
    if not frames:
        sys.exit("no frames in " + args.frames_dir)

    palette = build_palette(frames, args.colors)
    dither = Image.FLOYDSTEINBERG if args.dither else Image.NONE
    mapped = [Image.open(f).convert("RGB").quantize(palette=palette, dither=dither)
              for f in frames]

    mapped[0].save(
        args.out,
        save_all=True,
        append_images=mapped[1:],
        duration=round(1000 / args.fps),
        loop=0,
        optimize=True,
        disposal=1,          # leave the previous frame in place; only deltas move
    )
    size = pathlib.Path(args.out).stat().st_size / 1048576
    print(f"  {len(frames)} frames @ {args.fps} fps -> {args.out} ({size:.2f} MB)")


if __name__ == "__main__":
    main()
