#!/usr/bin/env python3
"""Generate original LeoVer Music starter catalog (instrumental, LEOVER_OWNED).

No third-party samples. Simple additive synthesis of original motifs.
"""
from __future__ import annotations

import json
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
DURATION_S = 18.0
VERSION = 1
LICENSE = "LEOVER_OWNED"
ATTRIBUTION = "LeoVer"

OUT_DIR = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "leover_music"

# Original motifs: (name, category, bpm, intervals in scale degrees, waveform)
TRACKS = [
    ("happy_paws", "Happy Paws", "ALEGRE", 112, [0, 2, 4, 7, 4, 2, 0, 7], "tri"),
    ("soft_nuzzle", "Soft Nuzzle", "TIERNA", 76, [0, 3, 5, 7, 5, 3, 0, 5], "sine"),
    ("zoomies", "Zoomies", "DIVERTIDA", 128, [0, 4, 7, 12, 7, 4, 2, 0], "sq"),
    ("park_run", "Park Run", "ENERGETICA", 132, [0, 7, 4, 9, 7, 12, 9, 7], "saw"),
    ("sunset_nap", "Sunset Nap", "TRANQUILA", 64, [0, 2, 3, 5, 3, 2, 0, 2], "sine"),
    ("first_day_home", "First Day Home", "EMOTIVA", 84, [0, 3, 7, 8, 7, 3, 5, 0], "tri"),
    ("new_family", "New Family", "ADOPCION", 96, [0, 4, 7, 9, 7, 4, 2, 4], "sine"),
    ("neighborhood_walk", "Neighborhood Walk", "PASEO", 104, [0, 2, 4, 5, 4, 2, 0, 2], "tri"),
    ("birthday_treat", "Birthday Treat", "CELEBRACION", 120, [0, 4, 7, 12, 11, 12, 7, 4], "sq"),
    ("puppy_steps", "Puppy Steps", "CACHORROS", 118, [0, 2, 4, 2, 5, 4, 2, 0], "tri"),
    ("whisker_dance", "Whisker Dance", "GATITOS", 108, [0, 3, 5, 7, 10, 7, 5, 3], "sine"),
    ("together", "Together", "COMUNIDAD", 90, [0, 4, 7, 4, 9, 7, 4, 0], "tri"),
]


def midi(root: float, degree: int) -> float:
    return 440.0 * (2 ** ((root + degree - 69) / 12.0))


def osc(kind: str, t: float, freq: float) -> float:
    phase = (freq * t) % 1.0
    if kind == "sine":
        return math.sin(2 * math.pi * phase)
    if kind == "tri":
        return 2 * abs(2 * phase - 1) - 1
    if kind == "sq":
        return 1.0 if phase < 0.5 else -1.0
    # saw
    return 2 * phase - 1


def envelope(pos: float, note_len: float) -> float:
    attack = min(0.04, note_len * 0.15)
    release = min(0.12, note_len * 0.35)
    if pos < attack:
        return pos / attack
    if pos > note_len - release:
        return max(0.0, (note_len - pos) / release)
    return 1.0


def render(bpm: int, degrees: list[int], kind: str) -> list[int]:
    n_samples = int(SAMPLE_RATE * DURATION_S)
    samples = [0.0] * n_samples
    beat = 60.0 / bpm
    note_len = beat * 0.9
    root = 64  # E4-ish original tessitura
    t = 0.0
    idx = 0
    bass_deg = [0, 0, 4, 7]
    while t < DURATION_S - 0.05:
        degree = degrees[idx % len(degrees)]
        freq = midi(root, degree)
        bass = midi(root - 24, bass_deg[(idx // 2) % len(bass_deg)])
        start = int(t * SAMPLE_RATE)
        length = int(note_len * SAMPLE_RATE)
        for i in range(length):
            s = start + i
            if s >= n_samples:
                break
            pos = i / SAMPLE_RATE
            env = envelope(pos, note_len)
            pad = osc("sine", (start + i) / SAMPLE_RATE, freq / 2.0) * 0.12
            lead = osc(kind, (start + i) / SAMPLE_RATE, freq) * 0.38
            low = osc("sine", (start + i) / SAMPLE_RATE, bass) * 0.22
            samples[s] += (lead + low + pad) * env
        t += beat
        idx += 1
    peak = max(1e-9, max(abs(x) for x in samples))
    out = []
    for i, x in enumerate(samples):
        fade = 1.0
        tail = n_samples - i
        if tail < SAMPLE_RATE * 0.4:
            fade = tail / (SAMPLE_RATE * 0.4)
        if i < SAMPLE_RATE * 0.05:
            fade *= i / (SAMPLE_RATE * 0.05)
        out.append(int(max(-1.0, min(1.0, x / peak * 0.86 * fade)) * 32767))
    return out


def write_wav(path: Path, pcm: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(b"".join(struct.pack("<h", s) for s in pcm))


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "catalog_id": "leover_music_catalog",
        "provider": "LEOVER_CATALOG",
        "license_type": LICENSE,
        "license_reference": "LeoVer original instrumental catalog",
        "attribution": ATTRIBUTION,
        "version": VERSION,
        "generator": "scripts/leover_music/generate_leover_music_catalog.py",
        "tracks": [],
    }
    for slug, title, category, bpm, degrees, kind in TRACKS:
        wav_name = f"{slug}.wav"
        pcm = render(bpm, degrees, kind)
        write_wav(OUT_DIR / wav_name, pcm)
        duration_ms = int(len(pcm) / SAMPLE_RATE * 1000)
        manifest["tracks"].append(
            {
                "track_id": f"leover-music-{slug}",
                "provider": "LEOVER_CATALOG",
                "title": title,
                "category": category,
                "duration_ms": duration_ms,
                "asset_uri": f"asset:///leover_music/{wav_name}",
                "license_type": LICENSE,
                "license_reference": f"LeoVer owned original instrumental — {title}",
                "is_active": True,
                "version": VERSION,
                "bpm": bpm,
                "attribution": ATTRIBUTION,
            }
        )
        print(f"wrote {wav_name} ({duration_ms} ms)")
    (OUT_DIR / "leover_music_catalog.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (OUT_DIR / "LICENSE.txt").write_text(
        "LeoVer original instrumental catalog.\n"
        "license = LEOVER_OWNED\n"
        "attribution = LeoVer\n"
        "Generated by scripts/leover_music/generate_leover_music_catalog.py\n"
        "No third-party samples.\n",
        encoding="utf-8",
    )
    print(f"catalog: {len(manifest['tracks'])} tracks")


if __name__ == "__main__":
    main()
