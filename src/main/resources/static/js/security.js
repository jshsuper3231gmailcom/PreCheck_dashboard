(function () {
    'use strict';

    // --- copy-paste 차단 ---
    document.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    document.addEventListener('keydown', function (e) {
        var k = e.key ? e.key.toLowerCase() : '';
        if (e.ctrlKey && ['c', 'a', 'v', 's', 'p', 'u', 'x'].includes(k)) { e.preventDefault(); return false; }
        if (['f12', 'printscreen'].includes(k)) { e.preventDefault(); return false; }
    });
    document.addEventListener('copy',  function (e) { e.preventDefault(); });
    document.addEventListener('cut',   function (e) { e.preventDefault(); });
    document.addEventListener('dragstart', function (e) { e.preventDefault(); });

    // --- 워터마크 오버레이 ---
    var username = document.body.getAttribute('data-username') || '';
    if (!username) return;

    function buildWatermarkImage(text) {
        var canvas = document.createElement('canvas');
        canvas.width  = 320;
        canvas.height = 160;
        var ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.save();
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate(-25 * Math.PI / 180);
        ctx.font = '16px sans-serif';
        ctx.fillStyle = 'rgba(80, 80, 80, 0.13)';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, 0, 0);
        ctx.restore();
        return canvas.toDataURL();
    }

    function getNow() {
        var d = new Date();
        var pad = function (n) { return String(n).padStart(2, '0'); };
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    var overlay = document.createElement('div');
    overlay.id = 'wm-overlay';
    overlay.style.cssText = [
        'position:fixed', 'inset:0', 'z-index:99999',
        'pointer-events:none', 'user-select:none',
        '-webkit-user-select:none'
    ].join(';');
    document.body.appendChild(overlay);

    function refreshWatermark() {
        var text = username + '  ' + getNow();
        overlay.style.backgroundImage = 'url(' + buildWatermarkImage(text) + ')';
        overlay.style.backgroundRepeat = 'repeat';
        overlay.style.backgroundSize   = '320px 160px';
    }

    refreshWatermark();
    setInterval(refreshWatermark, 60000); // 1분마다 시각 갱신
})();
