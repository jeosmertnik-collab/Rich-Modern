from PIL import Image
import struct, io, os

img = Image.open('D:\\Rich-Modern\\loader123\\excel.png').convert('RGBA')

sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
images = []

for w, h in sizes:
    resized = img.resize((w, h), Image.LANCZOS)
    if w <= 64:
        b, g, r, a = resized.split()
        bgra = Image.merge('RGBA', (b, g, r, a))
        raw = bgra.tobytes()
        # BMP info header: 40 bytes
        hdr = struct.pack('<IiiHHIIiiII',
            40,       # biSize
            w,        # biWidth
            h * 2,    # biHeight (doubled for ICO)
            1,        # biPlanes
            32,       # biBitCount
            0,        # biCompression
            len(raw), # biSizeImage
            2835,     # biXPelsPerMeter
            2835,     # biYPelsPerMeter
            0,        # biClrUsed
            0)        # biClrImportant
        images.append((w, h, len(hdr) + len(raw), hdr + raw))
    else:
        buf = io.BytesIO()
        resized.save(buf, format='PNG')
        images.append((w, h, len(buf.getvalue()), buf.getvalue()))

header = struct.pack('<HHH', 0, 1, len(images))
offset = 6 + 16 * len(images)
entries = b''
for w, h, size, data in images:
    ew = 0 if w >= 256 else w
    eh = 0 if h >= 256 else h
    entries += struct.pack('<BBBBHHII', ew, eh, 0, 0, 1, 32, size, offset)
    offset += size

with open('D:\\Rich-Modern\\loader123\\excel.ico', 'wb') as f:
    f.write(header)
    f.write(entries)
    for _, _, _, data in images:
        f.write(data)

sz = os.path.getsize('D:\\Rich-Modern\\loader123\\excel.ico')
print(f'ICO created: {sz} bytes, {len(images)} sizes')
