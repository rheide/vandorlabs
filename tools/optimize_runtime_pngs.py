"""Losslessly recompress staged PNGs; retain only candidates that shrink in the JAR."""
import argparse, io, struct, zlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from PIL import Image


def recompress(data):
    chunks=[]; stream=bytearray(); pos=8
    while pos<len(data):
        size=struct.unpack('>I',data[pos:pos+4])[0]; kind=data[pos+4:pos+8]; payload=data[pos+8:pos+8+size]
        if kind==b'IDAT':stream.extend(payload)
        else:chunks.append((kind,payload))
        pos+=12+size
    raw=zlib.decompress(stream); best=bytes(stream)
    for strategy in (zlib.Z_DEFAULT_STRATEGY,zlib.Z_FILTERED):
        encoder=zlib.compressobj(9,zlib.DEFLATED,15,9,strategy)
        candidate=encoder.compress(raw)+encoder.flush()
        if len(candidate)<len(best):best=candidate
    output=bytearray(data[:8])
    for kind,payload in chunks:
        if kind==b'IEND':
            output.extend(struct.pack('>I',len(best))+b'IDAT'+best+struct.pack('>I',zlib.crc32(b'IDAT'+best)))
        output.extend(struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload)))
    return bytes(output)


def optimize(path):
    original=path.read_bytes(); best=original; original_zip=len(zlib.compress(original,6)); best_zip=original_zip
    with Image.open(io.BytesIO(original)) as image:
        expected=image.convert('RGBA').tobytes(); size=image.size
        candidates=[recompress(original)]
        modes=[image]
        if image.mode=='RGBA' and image.getextrema()[3]==(255,255):modes.append(image.convert('RGB'))
        for mode in modes:
            output=io.BytesIO();mode.save(output,format='PNG',optimize=True,compress_level=9)
            candidates.append(output.getvalue())
        # Exact palette conversion is useful for model materials and item icons.
        if image.convert('RGBA').getcolors(256) is not None:
            palette=image.convert('RGBA').quantize(colors=256,method=Image.Quantize.FASTOCTREE)
            output=io.BytesIO();palette.save(output,format='PNG',optimize=True)
            candidates.append(output.getvalue())
        for candidate in candidates:
            packed=len(zlib.compress(candidate,6))
            if packed>=best_zip:continue
            with Image.open(io.BytesIO(candidate)) as decoded:
                if decoded.size!=size or decoded.convert('RGBA').tobytes()!=expected:continue
            best=candidate;best_zip=packed
    if best!=original:path.write_bytes(best)
    return int(best!=original),len(original)-len(best),original_zip-best_zip


def stage(directory):
    paths=sorted((directory/'textures').rglob('*.png'))
    with ThreadPoolExecutor(max_workers=4) as executor:results=list(executor.map(optimize,paths))
    changed,raw,packed=map(sum,zip(*results))
    print(f'Lossless PNG compression: {changed}/{len(paths)} improved; {raw} PNG bytes and approximately {packed} ZIP bytes saved; all decoded RGBA pixels unchanged')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage',type=Path,required=True)
    stage(parser.parse_args().stage)
