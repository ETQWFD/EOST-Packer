#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 生成 EOST Packer 启动图标（无第三方依赖，纯标准库写 PNG）
import zlib, struct, os

def write_png(path, size, px):
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    raw = b''
    for y in range(size):
        raw += b'\x00' + bytes(px[y])
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)

def make(size):
    px = []
    for y in range(size):
        row = []
        for x in range(size):
            # 坐标归一化 -1..1
            nx = (x - size/2) / (size/2)
            ny = (y - size/2) / (size/2)
            d = (nx*nx + ny*ny) ** 0.5
            # 圆角背景遮罩
            if d > 1.0:
                row.extend([0, 0, 0, 0])
                continue
            # 渐变：左上蓝 -> 右下紫
            t = (nx + ny + 2) / 4
            r = int(31 + (123-31)*t)
            g = int(111 + (63-111)*t)
            b = int(235 + (228-235)*t)
            # 中央白色圆环
            ring = 0.30 <= d <= 0.62
            # 三个小方块（打包意象）
            sq = False
            for ox, oy in ((-0.55,0.55),(0.0,0.55),(0.55,0.55)):
                if abs(nx-ox) < 0.16 and abs(ny-oy) < 0.16:
                    sq = True
            # 环带色
            if sq or ring:
                r, g, b = 255, 255, 255
            row.extend([r, g, b, 255])
        px.append(row)
    return px

base = os.path.join(os.path.dirname(__file__), '..', 'res', 'mipmap-xxxhdpi')
os.makedirs(base, exist_ok=True)
path = os.path.join(base, 'ic_launcher.png')
write_png(path, 192, make(192))
print('icon written:', path, os.path.getsize(path), 'bytes')
