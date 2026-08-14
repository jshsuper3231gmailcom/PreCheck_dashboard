(function () {
    let currentPath = '';

    function pad2(v) { return String(v).padStart(2, '0'); }

    function formatNow() {
        const d = new Date();
        return `${d.getFullYear()}/${pad2(d.getMonth() + 1)}/${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatSize(size) {
        if (size == null) return '-';
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];
        let value = size;
        let unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return `${unitIndex === 0 ? value : value.toFixed(1)} ${units[unitIndex]}`;
    }

    function formatDateTime(raw) {
        if (!raw) return '-';
        return String(raw).replace('T', ' ').substring(0, 19);
    }

    function joinPath(base, name) {
        return base ? base + '/' + name : name;
    }

    async function fetchApi(url) {
        const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
        const json = await res.json();
        if (!json || json.success !== true) {
            throw new Error(json && json.message ? json.message : 'ERROR');
        }
        return json.data;
    }

    function showAlert(message) {
        const alertEl = document.getElementById('downloadAlert');
        const tableWrap = document.getElementById('downloadTableWrap');
        alertEl.textContent = message;
        alertEl.style.display = '';
        tableWrap.style.display = 'none';
    }

    function hideAlert() {
        const alertEl = document.getElementById('downloadAlert');
        const tableWrap = document.getElementById('downloadTableWrap');
        alertEl.style.display = 'none';
        tableWrap.style.display = '';
    }

    function renderBreadcrumb(path) {
        const parts = path ? path.split('/').filter(Boolean) : [];
        const container = document.getElementById('downloadBreadcrumb');
        let html = `<li class="breadcrumb-item"><a href="#" data-path="">Download</a></li>`;
        let accum = '';
        parts.forEach((part, i) => {
            accum += (i > 0 ? '/' : '') + part;
            const isLast = i === parts.length - 1;
            html += isLast
                ? `<li class="breadcrumb-item active" aria-current="page">${escapeHtml(part)}</li>`
                : `<li class="breadcrumb-item"><a href="#" data-path="${escapeHtml(accum)}">${escapeHtml(part)}</a></li>`;
        });
        container.innerHTML = html;
    }

    function renderTable(entries) {
        const tbody = document.getElementById('downloadTableBody');
        if (!entries.length) {
            tbody.innerHTML = `<tr><td colspan="4" class="loading-msg">표시할 항목이 없습니다.</td></tr>`;
            return;
        }
        tbody.innerHTML = entries.map(e => {
            if (e.directory) {
                return `<tr>
                    <td><i class="bi bi-folder-fill text-warning"></i> <a href="#" class="folder-link" data-name="${escapeHtml(e.name)}">${escapeHtml(e.name)}</a></td>
                    <td>-</td>
                    <td>${escapeHtml(formatDateTime(e.lastModified))}</td>
                    <td></td>
                </tr>`;
            }
            const href = '/dashboard/download/file?path=' + encodeURIComponent(joinPath(currentPath, e.name));
            return `<tr>
                <td><i class="bi bi-file-earmark"></i> ${escapeHtml(e.name)}</td>
                <td>${escapeHtml(formatSize(e.size))}</td>
                <td>${escapeHtml(formatDateTime(e.lastModified))}</td>
                <td><a href="${href}" class="btn btn-sm btn-outline-primary rounded-pill"><i class="bi bi-download"></i></a></td>
            </tr>`;
        }).join('');
    }

    async function loadPath(path) {
        const tbody = document.getElementById('downloadTableBody');
        tbody.innerHTML = `<tr><td colspan="4" class="loading-msg">데이터 로딩 중...</td></tr>`;
        try {
            const data = await fetchApi('/dashboard/api/download/list?path=' + encodeURIComponent(path));
            hideAlert();
            currentPath = data.path || '';
            renderBreadcrumb(currentPath);
            renderTable(data.entries || []);
        } catch (e) {
            renderBreadcrumb(currentPath);
            showAlert(e.message || '목록을 불러오지 못했습니다.');
        }
    }

    window.addEventListener('DOMContentLoaded', () => {
        const clockEl = document.getElementById('currentTime');
        if (clockEl) {
            clockEl.textContent = formatNow();
            setInterval(() => { clockEl.textContent = formatNow(); }, 1000);
        }

        document.getElementById('downloadBreadcrumb').addEventListener('click', (e) => {
            const a = e.target.closest('a[data-path]');
            if (!a) return;
            e.preventDefault();
            loadPath(a.dataset.path);
        });

        document.getElementById('downloadTableBody').addEventListener('click', (e) => {
            const a = e.target.closest('a.folder-link');
            if (!a) return;
            e.preventDefault();
            loadPath(joinPath(currentPath, a.dataset.name));
        });

        loadPath('');
    });
})();
