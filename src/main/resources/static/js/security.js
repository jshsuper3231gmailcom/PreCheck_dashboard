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
})();
