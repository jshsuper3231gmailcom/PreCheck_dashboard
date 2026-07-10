/* ============================================================
 * chart-spike.js
 * 스파이크 차트 스타일 세로 크로스헤어 라인.
 * 활성 툴팁이 있는 지점에 세로 점선을 그린다. Chart.js 전역 플러그인으로
 * 등록해 x/y 카테시안 축이 있는 모든 차트(라인차트 등)에 자동 적용된다.
 * 도넛처럼 x축이 없는 차트는 scales.x가 없어 자연히 아무것도 그리지 않는다.
 * ============================================================ */
(function () {
    var spikeLinePlugin = {
        id: 'spikeLine',
        afterDraw: function (chart) {
            var active = chart.tooltip && chart.tooltip._active;
            if (!active || !active.length) return;
            var ctx = chart.ctx;
            var chartArea = chart.chartArea;
            var x = chart.scales && chart.scales.x;
            if (!x || !chartArea) return;

            var xPos = active[0].element.x;
            var dark = document.documentElement.getAttribute('data-bs-theme') === 'dark';

            ctx.save();
            ctx.beginPath();
            ctx.moveTo(xPos, chartArea.top);
            ctx.lineTo(xPos, chartArea.bottom);
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);
            ctx.strokeStyle = dark ? 'rgba(255,255,255,.35)' : 'rgba(0,0,0,.35)';
            ctx.stroke();
            ctx.restore();
        }
    };

    if (window.Chart) {
        window.Chart.register(spikeLinePlugin);
    }
})();
