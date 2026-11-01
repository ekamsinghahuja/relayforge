#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';

const apiBase = (process.env.RELAYFORGE_API_URL ?? 'http://localhost:8081').replace(/\/$/, '');
const receiverBase = (process.env.RELAYFORGE_RECEIVER_URL ?? 'http://localhost:9091').replace(/\/$/, '');
const endpointBase = (process.env.RELAYFORGE_RECEIVER_ENDPOINT_BASE ?? 'http://receiver:9090').replace(/\/$/, '');
const args = process.argv.slice(2);
const [command, action, extra] = args;

function usage() {
  console.log(`RelayForge local automation

Commands:
  ./rf up                         Build and start the full Docker stack
  ./rf down                       Stop the stack and preserve the database volume
  ./rf logs [service]             Follow Compose logs
  ./rf receiver accept            Show the always-200 receiver URL
  ./rf receiver fail              Show the always-503 retry-test URL
  ./rf receiver status <200-599>  Show a URL that returns that HTTP status
  ./rf receiver requests          Print captured webhook requests
  ./rf receiver reset             Clear the receiver's in-memory request log
  ./rf test accept                Run the Robot Framework success scenario
  ./rf test retry                 Run the Robot Framework retry scenario
  ./rf test all                   Run the Robot Framework suite

The Docker receiver is published on localhost:9091; the backend target remains receiver:9090.
For a host-run backend, set RELAYFORGE_RECEIVER_ENDPOINT_BASE=http://localhost:9091.
For a host-run backend API, set RELAYFORGE_API_URL=http://localhost:8080.`);
}

function run(commandName, commandArgs, options = {}) {
  const result = spawnSync(commandName, commandArgs, { stdio: 'inherit', ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

async function waitFor(url, label, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (response.ok) {
        console.log(`${label} is ready`);
        return;
      }
    } catch { /* wait while containers initialize */ }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error(`${label} did not become ready. Check: ./rf logs`);
}

async function receiver(path, init) {
  const response = await fetch(`${receiverBase}${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
    signal: AbortSignal.timeout(10_000),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(`Receiver ${path} returned ${response.status}`);
  return body;
}

try {
  if (!command || command === 'help' || command === '--help') {
    usage();
  } else if (command === 'up') {
    run('docker', ['compose', 'up', '--detach', '--build']);
    await Promise.all([
      waitFor(`${apiBase}/api/v1/dashboard/summary`, 'RelayForge API'),
      waitFor(`${receiverBase}/health`, 'Retry receiver'),
      waitFor('http://localhost:3000/api/environment', 'RelayForge Console'),
    ]);
    console.log('Open http://localhost:3000. The console is prefilled for the Docker API.');
  } else if (command === 'down') {
    run('docker', ['compose', 'down']);
  } else if (command === 'logs') {
    const child = spawn('docker', ['compose', 'logs', '--follow', ...(action ? [action] : [])], { stdio: 'inherit' });
    child.on('exit', code => process.exit(code ?? 0));
  } else if (command === 'receiver') {
    if (action === 'accept') console.log(`HTTP 200 accept URL for endpoint registration: ${endpointBase}/accept`);
    else if (action === 'fail') console.log(`HTTP 503 fail URL for retry testing: ${endpointBase}/fail`);
    else if (action === 'status') {
      const status = Number(extra);
      if (!Number.isInteger(status) || status < 200 || status > 599) throw new Error('Use an HTTP status code from 200 through 599.');
      console.log(`HTTP ${status} endpoint URL for registration: ${endpointBase}/status/${status}`);
    } else if (action === 'requests') console.log(JSON.stringify(await receiver('/requests'), null, 2));
    else if (action === 'reset') console.log(JSON.stringify(await receiver('/requests', { method: 'DELETE' })));
    else usage();
  } else {
    usage();
    process.exitCode = 2;
  }
} catch (error) {
  console.error(`RelayForge automation failed: ${error instanceof Error ? error.message : error}`);
  process.exitCode = 1;
}
