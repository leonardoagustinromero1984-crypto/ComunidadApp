import fs from "node:fs";
import path from "node:path";

const nextDir = ".next";
const standaloneNextDir = path.join(nextDir, "standalone", ".next");
const skipEntries = new Set(["cache", "standalone", "diagnostics", "trace", "types"]);

function copyEntry(source, destination) {
  const stat = fs.statSync(source);
  if (stat.isDirectory()) {
    copyDirectoryIfExists(source, destination);
    return;
  }
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
}

function copyDirectoryIfExists(source, destination) {
  if (!fs.existsSync(source)) {
    return;
  }

  fs.mkdirSync(destination, { recursive: true });

  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const from = path.join(source, entry.name);
    const to = path.join(destination, entry.name);

    if (entry.isDirectory()) {
      copyDirectoryIfExists(from, to);
    } else {
      fs.copyFileSync(from, to);
    }
  }
}

/** Mirrors `.next` into `.next/standalone/.next` for OpenNext when output:standalone is unavailable. */
export function prepareOpenNextStandalone() {
  fs.mkdirSync(standaloneNextDir, { recursive: true });

  for (const entry of fs.readdirSync(nextDir, { withFileTypes: true })) {
    if (skipEntries.has(entry.name)) {
      continue;
    }

    copyEntry(path.join(nextDir, entry.name), path.join(standaloneNextDir, entry.name));
  }
}

prepareOpenNextStandalone();
