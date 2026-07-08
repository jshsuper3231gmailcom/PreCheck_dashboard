/* ============================================================
 * precheck-theme.js
 * 라이트/다크 테마 토글 + FOUC(깜빡임) 방지.
 *
 * <head>에서 동기 로드되어, body 렌더 전에 저장된 테마를 <html>에 적용한다.
 * 기본값은 dark(시안: 24시간 상시 모니터링 다크 대시보드).
 * 토글 버튼은 [data-theme-toggle] 속성으로 표시하며, 클릭 시
 * localStorage에 저장하고 precheck-theme-change 이벤트를 발행한다.
 * (차트 재렌더는 각 화면 스크립트가 이 이벤트를 구독해 처리)
 * ============================================================ */
(function () {
    var KEY = 'precheck-theme';

    function read() {
        try { return localStorage.getItem(KEY); } catch (e) { return null; }
    }
    function write(theme) {
        try { localStorage.setItem(KEY, theme); } catch (e) { /* private mode 등 무시 */ }
    }

    // FOUC 방지: 즉시 적용 (기본 dark)
    var theme = read() || 'dark';
    document.documentElement.setAttribute('data-bs-theme', theme);

    function apply(next) {
        document.documentElement.setAttribute('data-bs-theme', next);
        write(next);
        window.dispatchEvent(new CustomEvent('precheck-theme-change', { detail: { theme: next } }));
    }

    window.PrecheckTheme = {
        current: function () {
            return document.documentElement.getAttribute('data-bs-theme');
        },
        isDark: function () {
            return document.documentElement.getAttribute('data-bs-theme') === 'dark';
        },
        toggle: function () {
            apply(window.PrecheckTheme.isDark() ? 'light' : 'dark');
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        var btns = document.querySelectorAll('[data-theme-toggle]');
        btns.forEach(function (btn) {
            var sync = function () {
                var dark = window.PrecheckTheme.isDark();
                var icon = btn.querySelector('i');
                if (icon) icon.className = dark ? 'bi bi-sun' : 'bi bi-moon-stars';
                var label = btn.querySelector('[data-theme-label]');
                if (label) label.textContent = dark ? '라이트' : '다크';
            };
            sync();
            btn.addEventListener('click', function () {
                window.PrecheckTheme.toggle();
                sync();
            });
        });
    });
})();
