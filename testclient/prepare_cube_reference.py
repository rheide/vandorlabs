#!/usr/bin/env python3
"""Create deterministic portrait artwork only inside an isolated client game directory."""
from pathlib import Path
import struct
import sys
import zlib


def prepare(game):
    width, height = 16, 32
    pixels = b''.join(b'\0' + bytes(value for x in range(width)
                                  for value in (x * 17, y * 8, (x ^ y) * 7, 255))
                      for y in range(height))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
    path = Path(game) / 'config/vandorlabs/textures/Reference/portrait.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    return path


if __name__ == '__main__':
    prepare(Path(sys.argv[1]))
