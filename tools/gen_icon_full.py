#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 完整图标：原图 464x512 完整等比适配（不裁剪任何部位），透明背景补边
import os
from PIL import Image

SRC = "/home/user/.doubao/agent_mode/workspace/.sessions/38448477696610562/attachments/17_104874 (1).png"
BASE = os.path.join(os.path.dirname(__file__), '..', 'res')

SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

src = Image.open(SRC).convert('RGBA')
w, h = src.size
# 目标正方形内完整容纳原图（等比），上下留透明边；安全区缩放到 96%
for d, size in SIZES.items():
    out_dir = os.path.join(BASE, d)
    os.makedirs(out_dir, exist_ok=True)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    usable = int(size * 0.96)
    scale = usable / max(w, h)
    nw = max(1, int(w * scale))
    nh = max(1, int(h * scale))
    thumb = src.resize((nw, nh), Image.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.paste(thumb, (x, y), thumb)
    path = os.path.join(out_dir, 'ic_launcher.png')
    canvas.save(path)
    print(f'{d}: {size}x{size}, 原图完整缩放 {nw}x{nh}')
