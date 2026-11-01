import { createServer } from 'node:http';

const host = process.env.RETRY_RECEIVER_HOST ?? '127.0.0.1';
const port = Number(process.env.RETRY_RECEIVER_PORT ?? 9090);
const requests = [];

function sendJson(response, status, body) {
  if ([204, 205, 304].includes(status)) {
    response.writeHead(status);
    response.end();
    return;
  }
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let body = '';
    request.setEncoding('utf8');
    request.on('data', chunk => {
      body += chunk;
      if (body.length > 1_000_000) {
        reject(new Error('Request body exceeds 1 MB'));
        request.destroy();
      }
    });
    request.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error('Request body must be valid JSON'));
      }
    });
    request.on('error', reject);
  });
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host ?? `${host}:${port}`}`);

  if (request.method === 'GET' && url.pathname === '/health') {
    sendJson(response, 200, { status: 'ok', host, port });
    return;
  }

  if (request.method === 'GET' && url.pathname === '/requests') {
    sendJson(response, 200, requests);
    return;
  }

  if (request.method === 'DELETE' && url.pathname === '/requests') {
    requests.length = 0;
    sendJson(response, 200, { cleared: true });
    return;
  }

  const statusMatch = url.pathname.match(/^\/status\/(\d{3})$/);
  const respondsWithStatus = statusMatch ? Number(statusMatch[1]) : null;
  if (request.method === 'POST'
      && (url.pathname === '/accept' || url.pathname === '/fail'
          || (respondsWithStatus !== null && respondsWithStatus >= 200 && respondsWithStatus <= 599))) {
    let body;
    try {
      body = await readJson(request);
    } catch (error) {
      sendJson(response, 400, { error: error.message });
      return;
    }

    const attempt = requests.length + 1;
    const status = respondsWithStatus ?? (url.pathname === '/fail' ? 503 : 200);
    const record = {
      attempt,
      receivedAt: new Date().toISOString(),
      status,
      method: request.method,
      path: url.pathname,
      headers: {
        'content-type': request.headers['content-type'] ?? null,
        'x-relayforge-event': request.headers['x-relayforge-event'] ?? null,
        'x-relayforge-delivery': request.headers['x-relayforge-delivery'] ?? null,
        'x-relayforge-timestamp': request.headers['x-relayforge-timestamp'] ?? null,
        'x-relayforge-signature': request.headers['x-relayforge-signature'] ?? null,
      },
      body,
    };
    requests.push(record);
    console.log(`request #${attempt}: ${url.pathname} -> ${status}`);
    sendJson(response, status, { received: true, attempt, path: url.pathname, status });
    return;
  }

  sendJson(response, 404, { error: 'Not found' });
});

server.listen(port, host, () => {
  console.log(`Retry test receiver listening at http://${host}:${port}`);
  console.log(`Accept endpoint: http://${host}:${port}/accept`);
  console.log(`Fail endpoint (HTTP 503): http://${host}:${port}/fail`);
  console.log(`Custom response endpoint: http://${host}:${port}/status/<100-599>`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
