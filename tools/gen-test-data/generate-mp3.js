#!/usr/bin/env node
/**
 * Generate a large number of playable MP3 files with full ID3v2 tags,
 * embedded artwork (APIC) and lyrics (USLT), for music-player performance testing.
 *
 * Requires: system `ffmpeg` in PATH, and npm dep `node-id3`.
 *
 * Usage:
 *   node generate-mp3.js --count 1000 --out ./output --concurrency 8 \
 *       --min-duration 5 --max-duration 20 --covers 10 \
 *       --bitrate 64k --mono
 * 
 * Examples: 
 *   node tools/gen-test-data/generate-mp3.js --count 50000 --out ./test-output --bitrate 64k --mono --concurrency 8
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync, spawn } = require('child_process');
const NodeID3 = require('node-id3');

// ---------- CLI args ----------
function parseArgs(argv) {
  const args = {
    count: 100,
    out: './output',
    concurrency: os.cpus().length,
    minDuration: 3,
    maxDuration: 8,
    covers: 10,
    bitrate: '96k',
    mono: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    switch (a) {
      case '--count': args.count = parseInt(next(), 10); break;
      case '--out': args.out = next(); break;
      case '--concurrency': args.concurrency = parseInt(next(), 10); break;
      case '--min-duration': args.minDuration = parseFloat(next()); break;
      case '--max-duration': args.maxDuration = parseFloat(next()); break;
      case '--covers': args.covers = parseInt(next(), 10); break;
      case '--bitrate': args.bitrate = next(); break;
      case '--mono': args.mono = true; break;
      case '--help':
        console.log(fs.readFileSync(__filename, 'utf8').split('\n').slice(1, 14).join('\n'));
        process.exit(0);
        break;
      default:
        console.error(`Unknown arg: ${a}`);
        process.exit(1);
    }
  }
  return args;
}

const args = parseArgs(process.argv.slice(2));

// ---------- check ffmpeg ----------
try {
  execSync('ffmpeg -version', { stdio: 'ignore' });
} catch {
  console.error('ffmpeg not found in PATH. Install it first (e.g. `brew install ffmpeg`).');
  process.exit(1);
}

// ---------- random data pools ----------
const ARTISTS = [
  'Neon Tide', 'Crimson Echo', 'Velvet Static', 'Glass Horizon', 'Paper Wolves',
  'Solar Drift', 'Midnight Arcade', 'Silver Lining Co', 'The Quiet Machine', 'Ember & Ash',
  'Lunar Parade', 'Iron Butterfly Jr', 'Copper Sky', 'Falling Satellites', 'The Analog Kids',
];
const ALBUMS = [
  'Afterglow', 'Static Bloom', 'Distant Shores', 'Low Tide Sessions', 'Reverb City',
  'Nightdrive', 'Paper Moon', 'Concrete Garden', 'Slow Motion', 'Faded Polaroids',
];
const GENRES = ['Rock', 'Pop', 'Jazz', 'Electronic', 'Hip-Hop', 'Classical', 'Ambient', 'Indie', 'Metal', 'Lo-Fi'];
const TITLE_WORDS = [
  'Echoes', 'Horizon', 'Falling', 'Static', 'Neon', 'Silence', 'Drift', 'Waves', 'Signal',
  'Fragments', 'Gravity', 'Afterglow', 'Rewind', 'Skyline', 'Whisper', 'Voltage', 'Mirage', 'Hollow',
];
const LYRIC_WORDS = [
  'night', 'light', 'shadow', 'road', 'heart', 'sky', 'fire', 'rain', 'silence', 'echo',
  'dream', 'ocean', 'stone', 'wind', 'flame', 'ghost', 'signal', 'static', 'horizon', 'gravity',
];
const COVER_COLORS = [
  '1abc9c', 'e74c3c', '3498db', 'f1c40f', '9b59b6', '2ecc71', 'e67e22', '34495e', 'ff6b81', '00cec9',
];

const rand = (arr) => arr[Math.floor(Math.random() * arr.length)];
const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const randFloat = (min, max) => Math.random() * (max - min) + min;

function randomTitle() {
  return `${rand(TITLE_WORDS)} ${rand(TITLE_WORDS)}`;
}

function randomLyrics(lines = 12) {
  const out = [];
  for (let i = 0; i < lines; i++) {
    const wordsInLine = randInt(4, 8);
    const line = Array.from({ length: wordsInLine }, () => rand(LYRIC_WORDS)).join(' ');
    out.push(line[0].toUpperCase() + line.slice(1));
  }
  return out.join('\n');
}

function slugify(s) {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

// ---------- setup dirs ----------
const outDir = path.resolve(args.out);
const coverDir = path.join(outDir, '.covers');
fs.mkdirSync(outDir, { recursive: true });
fs.mkdirSync(coverDir, { recursive: true });

// ---------- generate a small pool of cover images (reused across tracks) ----------
function generateCovers(n) {
  const covers = [];
  for (let i = 0; i < n; i++) {
    const color = COVER_COLORS[i % COVER_COLORS.length];
    const file = path.join(coverDir, `cover-${i}.jpg`);
    execSync(
      `ffmpeg -y -f lavfi -i "color=c=0x${color}:s=600x600" -frames:v 1 "${file}"`,
      { stdio: 'ignore' }
    );
    covers.push(file);
  }
  return covers;
}

console.log(`Generating ${args.covers} cover art images...`);
const covers = generateCovers(args.covers);

// ---------- single track generation ----------
function generateAudio(filePath, durationSec, frequency) {
  return new Promise((resolve, reject) => {
    const proc = spawn('ffmpeg', [
      '-y',
      '-f', 'lavfi',
      '-i', `sine=frequency=${frequency}:duration=${durationSec}`,
      '-c:a', 'libmp3lame',
      '-b:a', args.bitrate,
      '-ar', '44100',
      '-ac', args.mono ? '1' : '2',
      filePath,
    ], { stdio: 'ignore' });
    proc.on('error', reject);
    proc.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited ${code}`))));
  });
}

function tagTrack(filePath, index, coverPath) {
  const artist = rand(ARTISTS);
  const album = rand(ALBUMS);
  const title = `${randomTitle()} #${index + 1}`;
  const tags = {
    title,
    artist,
    albumArtist: artist,
    album,
    genre: rand(GENRES),
    year: String(randInt(1990, 2026)),
    trackNumber: String(index + 1),
    partOfSet: '1/1',
    composer: rand(ARTISTS),
    bpm: String(randInt(70, 160)),
    comment: { language: 'eng', text: `Test track generated for performance testing (#${index + 1})` },
    unsynchronisedLyrics: { language: 'eng', text: randomLyrics() },
    image: {
      mime: 'image/jpeg',
      type: { id: 3, name: 'front cover' },
      description: 'Cover',
      imageBuffer: fs.readFileSync(coverPath),
    },
  };
  const ok = NodeID3.write(tags, filePath);
  if (ok !== true) throw new Error(`Failed to write ID3 tags for ${filePath}`);
  return { title, artist };
}

async function generateOne(index) {
  const frequency = randInt(220, 880);
  const durationSec = randFloat(args.minDuration, args.maxDuration).toFixed(1);
  const cover = rand(covers);
  const tmpTitle = `track-${String(index + 1).padStart(6, '0')}`;
  const filePath = path.join(outDir, `${tmpTitle}.mp3`);

  await generateAudio(filePath, durationSec, frequency);
  const { title, artist } = tagTrack(filePath, index, cover);

  const finalName = `${String(index + 1).padStart(6, '0')} - ${slugify(artist)} - ${slugify(title)}.mp3`;
  const finalPath = path.join(outDir, finalName);
  fs.renameSync(filePath, finalPath);
  return finalPath;
}

// ---------- concurrency pool ----------
async function runPool(total, concurrency, worker) {
  let next = 0;
  let done = 0;
  const startTime = Date.now();

  async function runner() {
    while (next < total) {
      const i = next++;
      await worker(i);
      done++;
      const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
      process.stdout.write(`\rGenerated ${done}/${total} (${elapsed}s)   `);
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, total) }, runner));
  process.stdout.write('\n');
}

// ---------- main ----------
(async () => {
  console.log(`Generating ${args.count} MP3 files into ${outDir} (concurrency=${args.concurrency})...`);
  await runPool(args.count, args.concurrency, generateOne);
  console.log('Done.');
})().catch((err) => {
  console.error('\nGeneration failed:', err);
  process.exit(1);
});
