const http = require('http');
const { spawn } = require('child_process');
const { WebSocketServer } = require('ws');

const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('HospitalER Bridge Running');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
    console.log('Frontend connected!');
    
    // Spawns your Java backend cleanly using compiled classes in 'src'
    const javaProcess = spawn('java', ['-cp', 'src', 'Main']);

    javaProcess.stdout.on('data', (data) => {
        ws.send(data.toString());
    });

    javaProcess.stderr.on('data', (data) => {
        ws.send(data.toString());
    });

    ws.on('message', (message) => {
        javaProcess.stdin.write(message);
    });

    ws.on('close', () => {
        javaProcess.kill();
    });
});

server.listen(8080, () => {
    console.log('Bridge server running on http://localhost:8080');
});