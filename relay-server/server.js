const http = require('http');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8765;

/** @type {Map<string, { host: WebSocket|null, client: WebSocket|null }>} */
const rooms = new Map();

function send(ws, payload) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(payload));
    }
}

function notifyPaired(roomId) {
    const room = rooms.get(roomId);
    if (!room?.host || !room?.client) return;
    send(room.host, { type: 'paired' });
    send(room.client, { type: 'paired' });
}

function cleanupSocket(ws) {
    const roomId = ws.roomId;
    const role = ws.role;
    if (!roomId || !rooms.has(roomId)) return;

    const room = rooms.get(roomId);
    if (role === 'host' && room.host === ws) room.host = null;
    if (role === 'client' && room.client === ws) room.client = null;

    const peer = role === 'host' ? room.client : room.host;
    send(peer, { type: 'peer_disconnected' });

    if (!room.host && !room.client) {
        rooms.delete(roomId);
    }
}

function isValidRoom(room) {
    return typeof room === 'string' && /^[0-9]{6}$/.test(room);
}

const server = http.createServer((req, res) => {
    if (req.url === '/relay-config') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            relay: process.env.RELAY_WSS_URL || 'wss://appremote-07ys.onrender.com'
        }));
        return;
    }
    if (req.url === '/' || req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'ok',
            service: 'appremote-relay',
            rooms: rooms.size
        }));
        return;
    }
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
});

const wss = new WebSocket.Server({ server, maxPayload: 16 * 1024 * 1024 });

const PING_INTERVAL_MS = 25_000;

setInterval(() => {
    wss.clients.forEach((client) => {
        if (client.isAlive === false) {
            return client.terminate();
        }
        client.isAlive = false;
        client.ping();
    });
}, PING_INTERVAL_MS);

wss.on('connection', (ws) => {
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (raw) => {
        let msg;
        try {
            msg = JSON.parse(raw.toString());
        } catch {
            send(ws, { type: 'error', message: 'Invalid JSON' });
            return;
        }

        switch (msg.type) {
            case 'register': {
                const roomId = msg.room;
                const role = msg.role;

                if (!isValidRoom(roomId)) {
                    send(ws, { type: 'error', message: 'Room must be 6 digits' });
                    return;
                }
                if (role !== 'host' && role !== 'client') {
                    send(ws, { type: 'error', message: 'Invalid role' });
                    return;
                }

                if (!rooms.has(roomId)) {
                    rooms.set(roomId, { host: null, client: null });
                }
                const room = rooms.get(roomId);

                if (role === 'host') {
                    if (room.host && room.host !== ws) {
                        send(ws, { type: 'error', message: 'Room already has a host' });
                        return;
                    }
                    room.host = ws;
                } else {
                    if (room.client && room.client !== ws) {
                        send(ws, { type: 'error', message: 'Room already has a client' });
                        return;
                    }
                    room.client = ws;
                }

                ws.roomId = roomId;
                ws.role = role;
                send(ws, { type: 'registered', room: roomId });

                if (room.host && room.client) {
                    notifyPaired(roomId);
                }
                break;
            }

            case 'command': {
                const room = rooms.get(ws.roomId);
                if (ws.role !== 'client' || !room?.host) {
                    send(ws, { type: 'error', message: 'No host connected' });
                    return;
                }
                send(room.host, { type: 'command', data: msg.data });
                break;
            }

            case 'response': {
                const room = rooms.get(ws.roomId);
                if (ws.role !== 'host' || !room?.client) return;
                send(room.client, { type: 'response', data: msg.data });
                break;
            }

            case 'screen_frame': {
                const room = rooms.get(ws.roomId);
                if (ws.role !== 'host' || !room?.client) return;
                send(room.client, {
                    type: 'screen_frame',
                    data: msg.data,
                    width: msg.width,
                    height: msg.height
                });
                break;
            }

            default:
                send(ws, { type: 'error', message: 'Unknown message type' });
        }
    });

    ws.on('close', () => cleanupSocket(ws));
    ws.on('error', () => cleanupSocket(ws));
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`AppRemote relay server running on port ${PORT}`);
});
