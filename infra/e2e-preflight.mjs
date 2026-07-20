#!/usr/bin/env node
// E2E preflight: tear down any stale compose.e2e stack and ensure the single origin the
// ephemeral stack publishes is free before it starts. Publishing one origin (rather than
// separate SPA/API ports) keeps the browser same-origin and dodges port collisions with other
// projects on the same box.
//
// Non-fatal by design: it logs and continues, so it can be chained before `docker compose up`.
//
// Override the origin with E2E_HOST / E2E_PORT if 8090 is taken on your machine.
import { execSync } from 'node:child_process';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const compose = path.join(root, 'compose.e2e.yaml');
const host = process.env.E2E_HOST || '127.0.0.1';
const port = Number(process.env.E2E_PORT || 8090);

function sh(cmd) {
  try {
    return execSync(cmd, { stdio: ['ignore', 'pipe', 'pipe'] }).toString();
  } catch (error) {
    return String(error.stdout || '') + String(error.stderr || '');
  }
}

function portBusy(h, p) {
  return new Promise((resolve) => {
    const socket = net.connect({ host: h, port: p });
    socket.on('connect', () => { socket.destroy(); resolve(true); });
    socket.on('error', () => resolve(false));
    socket.setTimeout(1500, () => { socket.destroy(); resolve(false); });
  });
}

console.log('[e2e-preflight] tearing down any stale compose.e2e stack ...');
sh(`docker compose -f "${compose}" down -v`);

if (await portBusy(host, port)) {
  console.warn(`[e2e-preflight] ${host}:${port} still busy — removing containers publishing ${port}`);
  sh(`docker ps --filter "publish=${port}" -q`)
    .split('\n')
    .map((id) => id.trim())
    .filter(Boolean)
    .forEach((id) => sh(`docker rm -f ${id}`));
}

console.log('[e2e-preflight] done.');
