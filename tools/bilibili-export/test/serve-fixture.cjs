'use strict';
// Loopback only; explicit routes; no proxies and no external API calls.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const routes = {
    '/': ['browser-fixture.html', 'text/html;charset=utf-8'],
    '/bilibili-fav-export.user.js': ['../bilibili-fav-export.user.js', 'text/javascript;charset=utf-8'],
};
const server = http.createServer((request, response) => {
    const route = routes[request.url];
    if (!route || request.method !== 'GET') { response.writeHead(404); response.end(); return; }
    response.writeHead(200, { 'Content-Type': route[1], 'Cache-Control': 'no-store' });
    response.end(fs.readFileSync(path.join(__dirname, route[0])));
});
server.listen(18765, '127.0.0.1', () => console.log('Offline fixture: http://127.0.0.1:18765'));
