#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 用用户提供的图标生成 Android 全密度 mipmap
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

img = Image.open(SRC).convert('RGBA')
w, h = img.size
# 居中裁剪为正方形（保留全部主体）
side = min(w, h)
left = (w - side) // 2
top = (h - side) // 2
img = img.crop((left, top, left + side, top + side))

# 为图标加一点内边距（安卓图标规范 8% 左右安全区）
pad = 0.06
for d, size in SIZES.items():
    out_dir = os.path.join(BASE, d)
    os.makedirs(out_dir, exist_ok=True)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    inner = int(size * (1 - pad * 2))
    thumb = img.resize((inner, inner), Image.LANCZOS)
    off = (size - inner) // 2
    canvas.paste(thumb, (off, off), thumb)
    path = os.path.join(out_dir, 'ic_launcher.png')
    canvas.save(path)
    print('written:', path, os.path.getsize(path), 'bytes')
