/**
 * Generate original LeoVer Music starter catalog (instrumental, LEOVER_OWNED).
 * No third-party samples. Additive synthesis of original motifs.
 */
const fs = require("fs");
const path = require("path");

const SAMPLE_RATE = 22050;
const DURATION_S = 18.0;
const VERSION = 1;
const LICENSE = "LEOVER_OWNED";
const ATTRIBUTION = "LeoVer";
const OUT_DIR = path.resolve(__dirname, "../../app/src/main/assets/leover_music");

const TRACKS = [
  ["happy_paws", "Happy Paws", "ALEGRE", 112, [0, 2, 4, 7, 4, 2, 0, 7], "tri"],
  ["soft_nuzzle", "Soft Nuzzle", "TIERNA", 76, [0, 3, 5, 7, 5, 3, 0, 5], "sine"],
  ["zoomies", "Zoomies", "DIVERTIDA", 128, [0, 4, 7, 12, 7, 4, 2, 0], "sq"],
  ["park_run", "Park Run", "ENERGETICA", 132, [0, 7, 4, 9, 7, 12, 9, 7], "saw"],
  ["sunset_nap", "Sunset Nap", "TRANQUILA", 64, [0, 2, 3, 5, 3, 2, 0, 2], "sine"],
  ["first_day_home", "First Day Home", "EMOTIVA", 84, [0, 3, 7, 8, 7, 3, 5, 0], "tri"],
  ["new_family", "New Family", "ADOPCION", 96, [0, 4, 7, 9, 7, 4, 2, 4], "sine"],
  ["neighborhood_walk", "Neighborhood Walk", "PASEO", 104, [0, 2, 4, 5, 4, 2, 0, 2], "tri"],
  ["birthday_treat", "Birthday Treat", "CELEBRACION", 120, [0, 4, 7, 12, 11, 12, 7, 4], "sq"],
  ["puppy_steps", "Puppy Steps", "CACHORROS", 118, [0, 2, 4, 2, 5, 4, 2, 0], "tri"],
  ["whisker_dance", "Whisker Dance", "GATITOS", 108, [0, 3, 5, 7, 10, 7, 5, 3], "sine"],
  ["together", "Together", "COMUNIDAD", 90, [0, 4, 7, 4, 9, 7, 4, 0], "tri"],
];

function midi(root, degree) {
  return 440.0 * 2 ** ((root + degree - 69) / 12.0);
}

function osc(kind, t, freq) {
  const phase = (freq * t) % 1.0;
  if (kind === "sine") return Math.sin(2 * Math.PI * phase);
  if (kind === "tri") return 2 * Math.abs(2 * phase - 1) - 1;
  if (kind === "sq") return phase < 0.5 ? 1.0 : -1.0;
  return 2 * phase - 1;
}

function envelope(pos, noteLen) {
  const attack = Math.min(0.04, noteLen * 0.15);
  const release = Math.min(0.12, noteLen * 0.35);
  if (pos < attack) return pos / attack;
  if (pos > noteLen - release) return Math.max(0.0, (noteLen - pos) / release);
  return 1.0;
}

function render(bpm, degrees, kind) {
  const nSamples = Math.floor(SAMPLE_RATE * DURATION_S);
  const samples = new Float64Array(nSamples);
  const beat = 60.0 / bpm;
  const noteLen = beat * 0.9;
  const root = 64;
  const bassDeg = [0, 0, 4, 7];
  let t = 0.0;
  let idx = 0;
  while (t < DURATION_S - 0.05) {
    const degree = degrees[idx % degrees.length];
    const freq = midi(root, degree);
    const bass = midi(root - 24, bassDeg[Math.floor(idx / 2) % bassDeg.length]);
    const start = Math.floor(t * SAMPLE_RATE);
    const length = Math.floor(noteLen * SAMPLE_RATE);
    for (let i = 0; i < length; i++) {
      const s = start + i;
      if (s >= nSamples) break;
      const pos = i / SAMPLE_RATE;
      const env = envelope(pos, noteLen);
      const pad = osc("sine", (start + i) / SAMPLE_RATE, freq / 2.0) * 0.12;
      const lead = osc(kind, (start + i) / SAMPLE_RATE, freq) * 0.38;
      const low = osc("sine", (start + i) / SAMPLE_RATE, bass) * 0.22;
      samples[s] += (lead + low + pad) * env;
    }
    t += beat;
    idx += 1;
  }
  let peak = 1e-9;
  for (let i = 0; i < nSamples; i++) peak = Math.max(peak, Math.abs(samples[i]));
  const pcm = new Int16Array(nSamples);
  const fadeIn = SAMPLE_RATE * 0.05;
  const fadeOut = SAMPLE_RATE * 0.4;
  for (let i = 0; i < nSamples; i++) {
    let fade = 1.0;
    const tail = nSamples - i;
    if (tail < fadeOut) fade = tail / fadeOut;
    if (i < fadeIn) fade *= i / fadeIn;
    const x = (samples[i] / peak) * 0.86 * fade;
    pcm[i] = Math.max(-32767, Math.min(32767, Math.round(x * 32767)));
  }
  return pcm;
}

function writeWav(filePath, pcm) {
  const dataSize = pcm.length * 2;
  const buf = Buffer.alloc(44 + dataSize);
  buf.write("RIFF", 0);
  buf.writeUInt32LE(36 + dataSize, 4);
  buf.write("WAVE", 8);
  buf.write("fmt ", 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(SAMPLE_RATE, 24);
  buf.writeUInt32LE(SAMPLE_RATE * 2, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write("data", 36);
  buf.writeUInt32LE(dataSize, 40);
  for (let i = 0; i < pcm.length; i++) buf.writeInt16LE(pcm[i], 44 + i * 2);
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, buf);
}

function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const manifest = {
    catalog_id: "leover_music_catalog",
    provider: "LEOVER_CATALOG",
    license_type: LICENSE,
    license_reference: "LeoVer original instrumental catalog",
    attribution: ATTRIBUTION,
    version: VERSION,
    generator: "scripts/leover_music/generate_leover_music_catalog.js",
    tracks: [],
  };
  for (const [slug, title, category, bpm, degrees, kind] of TRACKS) {
    const wavName = `${slug}.wav`;
    const pcm = render(bpm, degrees, kind);
    writeWav(path.join(OUT_DIR, wavName), pcm);
    const durationMs = Math.round((pcm.length / SAMPLE_RATE) * 1000);
    manifest.tracks.push({
      track_id: `leover-music-${slug}`,
      provider: "LEOVER_CATALOG",
      title,
      category,
      duration_ms: durationMs,
      asset_uri: `asset:///leover_music/${wavName}`,
      license_type: LICENSE,
      license_reference: `LeoVer owned original instrumental — ${title}`,
      is_active: true,
      version: VERSION,
      bpm,
      attribution: ATTRIBUTION,
    });
    console.log(`wrote ${wavName} (${durationMs} ms)`);
  }
  fs.writeFileSync(
    path.join(OUT_DIR, "leover_music_catalog.json"),
    JSON.stringify(manifest, null, 2) + "\n",
    "utf8"
  );
  fs.writeFileSync(
    path.join(OUT_DIR, "LICENSE.txt"),
    [
      "LeoVer original instrumental catalog.",
      "license = LEOVER_OWNED",
      "attribution = LeoVer",
      "Generated by scripts/leover_music/generate_leover_music_catalog.js",
      "No third-party samples.",
      "",
    ].join("\n"),
    "utf8"
  );
  console.log(`catalog: ${manifest.tracks.length} tracks`);
}

main();
