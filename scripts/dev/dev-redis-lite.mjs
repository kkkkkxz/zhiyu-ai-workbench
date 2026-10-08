import net from 'node:net';

const port = Number(process.env.REDIS_LITE_PORT || 6379);
const store = new Map();

function now() {
  return Date.now();
}

function getEntry(key) {
  const item = store.get(key);
  if (!item) return null;
  if (item.expiresAt && item.expiresAt <= now()) {
    store.delete(key);
    return null;
  }
  return item;
}

function setValue(key, value, ttlSeconds = null) {
  store.set(key, {
    value: String(value),
    expiresAt: ttlSeconds ? now() + ttlSeconds * 1000 : null,
  });
}

function encode(value) {
  if (value === null || value === undefined) return '$-1\r\n';
  if (typeof value === 'number') return `:${value}\r\n`;
  if (value === 'OK' || value === 'PONG') return `+${value}\r\n`;
  const text = String(value);
  return `$${Buffer.byteLength(text)}\r\n${text}\r\n`;
}

function encodeArray(values) {
  return `*${values.length}\r\n${values.map(encode).join('')}`;
}

function parseRespCommands(buffer) {
  const text = buffer.toString('utf8');
  if (!text.startsWith('*')) {
    return [text.trim().split(/\s+/).filter(Boolean)];
  }
  const commands = [];
  let offset = 0;
  while (offset < text.length) {
    if (text[offset] !== '*') break;
    const lineEnd = text.indexOf('\r\n', offset);
    if (lineEnd < 0) break;
    const count = Number(text.slice(offset + 1, lineEnd));
    offset = lineEnd + 2;
    const out = [];
    for (let n = 0; n < count && offset < text.length; n += 1) {
      const markerEnd = text.indexOf('\r\n', offset);
      if (markerEnd < 0) break;
      const marker = text.slice(offset, markerEnd);
      offset = markerEnd + 2;
      const type = marker[0];
      if (type === '$') {
        const len = Number(marker.slice(1));
        out.push(text.slice(offset, offset + len));
        offset += len + 2;
      } else if (type === ':' || type === '+') {
        out.push(marker.slice(1));
      }
    }
    if (out.length) commands.push(out);
  }
  return commands;
}

function runEval(args) {
  const keyCount = Number(args[1] || 0);
  const keys = args.slice(2, 2 + keyCount);
  const argv = args.slice(2 + keyCount);
  const key = keys[0];
  const limit = Number(argv[0] || 0);
  const windowSeconds = Number(argv[1] || 60);
  const current = Number(getEntry(key)?.value || 0) + 1;
  setValue(key, current, current === 1 ? windowSeconds : null);
  return current > limit ? 0 : 1;
}

function handle(parts) {
  const cmd = (parts[0] || '').toUpperCase();
  const args = parts.slice(1);

  switch (cmd) {
    case 'PING':
      return encode('PONG');
    case 'HELLO':
      return encodeArray(['server', 'redis', 'version', '7.0.0', 'proto', 3, 'id', 1, 'mode', 'standalone', 'role', 'master']);
    case 'COMMAND':
      return encodeArray([]);
    case 'CLIENT':
      return encode('OK');
    case 'SELECT':
      return encode('OK');
    case 'GET': {
      return encode(getEntry(args[0])?.value ?? null);
    }
    case 'SET': {
      const key = args[0];
      const value = args[1] ?? '';
      let ttl = null;
      for (let i = 2; i < args.length; i += 1) {
        if (args[i].toUpperCase() === 'EX') ttl = Number(args[i + 1]);
        if (args[i].toUpperCase() === 'PX') ttl = Math.ceil(Number(args[i + 1]) / 1000);
      }
      setValue(key, value, ttl);
      return encode('OK');
    }
    case 'EXISTS':
      return encode(args.reduce((sum, key) => sum + (getEntry(key) ? 1 : 0), 0));
    case 'DEL': {
      let deleted = 0;
      for (const key of args) {
        if (store.delete(key)) deleted += 1;
      }
      return encode(deleted);
    }
    case 'INCR': {
      const key = args[0];
      const current = Number(getEntry(key)?.value || 0) + 1;
      setValue(key, current);
      return encode(current);
    }
    case 'EXPIRE': {
      const item = getEntry(args[0]);
      if (!item) return encode(0);
      item.expiresAt = now() + Number(args[1] || 0) * 1000;
      return encode(1);
    }
    case 'PEXPIRE': {
      const item = getEntry(args[0]);
      if (!item) return encode(0);
      item.expiresAt = now() + Number(args[1] || 0);
      return encode(1);
    }
    case 'GETDEL': {
      const key = args[0];
      const item = getEntry(key);
      store.delete(key);
      return encode(item?.value ?? null);
    }
    case 'EVAL':
    case 'EVALSHA':
      return encode(runEval(args));
    default:
      return `-ERR unsupported command ${cmd}\r\n`;
  }
}

const server = net.createServer((socket) => {
  socket.on('error', (err) => {
    console.warn(`[dev-redis-lite] socket error: ${err.code || err.message}`);
  });

  socket.on('data', (chunk) => {
    try {
      for (const parts of parseRespCommands(chunk)) {
        console.log(`[dev-redis-lite] ${parts.join(' ')}`);
        if (!socket.destroyed) socket.write(handle(parts));
      }
    } catch (err) {
      if (!socket.destroyed) socket.write(`-ERR ${err.message}\r\n`);
    }
  });
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[dev-redis-lite] listening on 127.0.0.1:${port}`);
});
