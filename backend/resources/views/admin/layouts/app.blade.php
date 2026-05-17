<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>@yield('title', 'Admin Panel') — Tracker</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css" rel="stylesheet">
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            --bg-primary: #0a0e1a;
            --bg-secondary: #111827;
            --bg-card: #1a1f35;
            --bg-card-hover: #232a45;
            --border: #2a3150;
            --text-primary: #f1f5f9;
            --text-secondary: #94a3b8;
            --text-muted: #64748b;
            --accent: #6366f1;
            --accent-hover: #818cf8;
            --accent-glow: rgba(99, 102, 241, 0.3);
            --success: #22c55e;
            --warning: #f59e0b;
            --danger: #ef4444;
            --info: #3b82f6;
            --radius: 12px;
            --radius-sm: 8px;
            --shadow: 0 4px 24px rgba(0,0,0,0.3);
        }

        body {
            font-family: 'Inter', -apple-system, sans-serif;
            background: var(--bg-primary);
            color: var(--text-primary);
            min-height: 100vh;
        }

        /* Sidebar */
        .sidebar {
            position: fixed;
            left: 0; top: 0; bottom: 0;
            width: 260px;
            background: var(--bg-secondary);
            border-right: 1px solid var(--border);
            display: flex; flex-direction: column;
            z-index: 100;
        }

        .sidebar-brand {
            padding: 24px 20px;
            border-bottom: 1px solid var(--border);
            display: flex; align-items: center; gap: 12px;
        }

        .sidebar-brand .logo {
            width: 40px; height: 40px;
            background: linear-gradient(135deg, var(--accent), #a855f7);
            border-radius: 10px;
            display: flex; align-items: center; justify-content: center;
            font-size: 18px; font-weight: 800; color: #fff;
        }

        .sidebar-brand h1 {
            font-size: 18px; font-weight: 700;
            background: linear-gradient(135deg, var(--accent), #a855f7);
            -webkit-background-clip: text; -webkit-text-fill-color: transparent;
        }

        .sidebar-nav { padding: 16px 12px; flex: 1; }

        .sidebar-nav a {
            display: flex; align-items: center; gap: 12px;
            padding: 12px 16px;
            color: var(--text-secondary);
            text-decoration: none;
            border-radius: var(--radius-sm);
            font-size: 14px; font-weight: 500;
            transition: all 0.2s;
            margin-bottom: 4px;
        }

        .sidebar-nav a:hover, .sidebar-nav a.active {
            background: var(--bg-card);
            color: var(--text-primary);
        }

        .sidebar-nav a.active {
            background: linear-gradient(135deg, rgba(99,102,241,0.15), rgba(168,85,247,0.1));
            color: var(--accent-hover);
            border: 1px solid rgba(99,102,241,0.2);
        }

        .sidebar-nav a i { width: 20px; text-align: center; font-size: 15px; }

        .sidebar-footer {
            padding: 16px 12px;
            border-top: 1px solid var(--border);
        }

        .sidebar-footer form button {
            width: 100%;
            display: flex; align-items: center; gap: 12px;
            padding: 12px 16px;
            background: none; border: none;
            color: var(--text-muted);
            font-size: 14px; font-weight: 500;
            cursor: pointer;
            border-radius: var(--radius-sm);
            transition: all 0.2s;
            font-family: inherit;
        }

        .sidebar-footer form button:hover {
            background: rgba(239,68,68,0.1);
            color: var(--danger);
        }

        /* Main Content */
        .main-content {
            margin-left: 260px;
            min-height: 100vh;
        }

        .top-bar {
            padding: 20px 32px;
            border-bottom: 1px solid var(--border);
            display: flex; align-items: center; justify-content: space-between;
            background: rgba(17, 24, 39, 0.6);
            backdrop-filter: blur(12px);
            position: sticky; top: 0; z-index: 50;
        }

        .top-bar h2 {
            font-size: 20px; font-weight: 700;
        }

        .top-bar .user-info {
            display: flex; align-items: center; gap: 10px;
            color: var(--text-secondary); font-size: 13px;
        }

        .top-bar .user-info .avatar {
            width: 32px; height: 32px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--accent), #a855f7);
            display: flex; align-items: center; justify-content: center;
            font-size: 13px; font-weight: 700; color: #fff;
        }

        .page-content { padding: 32px; }

        /* Stats Cards */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 20px;
            margin-bottom: 32px;
        }

        .stat-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            padding: 24px;
            transition: all 0.3s;
        }

        .stat-card:hover {
            border-color: var(--accent);
            box-shadow: 0 0 20px var(--accent-glow);
            transform: translateY(-2px);
        }

        .stat-card .stat-icon {
            width: 44px; height: 44px;
            border-radius: 10px;
            display: flex; align-items: center; justify-content: center;
            font-size: 18px;
            margin-bottom: 16px;
        }

        .stat-card .stat-icon.blue { background: rgba(59,130,246,0.15); color: var(--info); }
        .stat-card .stat-icon.green { background: rgba(34,197,94,0.15); color: var(--success); }
        .stat-card .stat-icon.purple { background: rgba(168,85,247,0.15); color: #a855f7; }
        .stat-card .stat-icon.orange { background: rgba(245,158,11,0.15); color: var(--warning); }

        .stat-card .stat-value {
            font-size: 28px; font-weight: 800;
            margin-bottom: 4px;
        }

        .stat-card .stat-label {
            font-size: 13px; color: var(--text-muted); font-weight: 500;
        }

        /* Table */
        .data-table-wrapper {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            overflow: hidden;
        }

        .data-table-header {
            padding: 20px 24px;
            border-bottom: 1px solid var(--border);
            display: flex; align-items: center; justify-content: space-between;
        }

        .data-table-header h3 { font-size: 16px; font-weight: 700; }

        table {
            width: 100%;
            border-collapse: collapse;
        }

        thead th {
            padding: 14px 20px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text-muted);
            text-align: left;
            border-bottom: 1px solid var(--border);
            background: rgba(0,0,0,0.15);
        }

        tbody td {
            padding: 14px 20px;
            font-size: 14px;
            border-bottom: 1px solid rgba(42,49,80,0.5);
        }

        tbody tr {
            transition: background 0.2s;
        }

        tbody tr:hover {
            background: var(--bg-card-hover);
        }

        /* Badges */
        .badge {
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.3px;
        }

        .badge-online { background: rgba(34,197,94,0.15); color: var(--success); }
        .badge-offline { background: rgba(100,116,139,0.15); color: var(--text-muted); }
        .badge-setup { background: rgba(245,158,11,0.15); color: var(--warning); }

        /* Buttons */
        .btn {
            padding: 10px 20px;
            border-radius: var(--radius-sm);
            font-size: 13px;
            font-weight: 600;
            border: none;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            transition: all 0.2s;
            text-decoration: none;
            font-family: inherit;
        }

        .btn-primary {
            background: linear-gradient(135deg, var(--accent), #7c3aed);
            color: #fff;
        }

        .btn-primary:hover {
            box-shadow: 0 0 20px var(--accent-glow);
            transform: translateY(-1px);
        }

        .btn-danger {
            background: rgba(239,68,68,0.15);
            color: var(--danger);
            border: 1px solid rgba(239,68,68,0.2);
        }

        .btn-danger:hover { background: rgba(239,68,68,0.25); }

        .btn-outline {
            background: transparent;
            color: var(--text-secondary);
            border: 1px solid var(--border);
        }

        .btn-outline:hover {
            border-color: var(--accent);
            color: var(--accent-hover);
        }

        .btn-sm { padding: 6px 14px; font-size: 12px; }

        .btn-icon {
            width: 36px; height: 36px;
            padding: 0;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border-radius: var(--radius-sm);
        }

        /* Screenshot Grid */
        .screenshot-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 16px;
        }

        .screenshot-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            overflow: hidden;
            transition: all 0.3s;
            cursor: pointer;
        }

        .screenshot-card:hover {
            border-color: var(--accent);
            transform: translateY(-3px);
            box-shadow: var(--shadow);
        }

        .screenshot-card img {
            width: 100%;
            height: 160px;
            object-fit: cover;
        }

        .screenshot-card .meta {
            padding: 12px;
        }

        .screenshot-card .meta .trigger {
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.3px;
        }

        .screenshot-card .meta .time {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 4px;
        }

        .trigger-spacebar { color: var(--info); }
        .trigger-call { color: var(--warning); }
        .trigger-admin_pull { color: #a855f7; }

        /* Tabs */
        .tabs {
            display: flex; gap: 4px;
            border-bottom: 1px solid var(--border);
            margin-bottom: 24px;
        }

        .tab {
            padding: 12px 20px;
            color: var(--text-muted);
            font-size: 14px; font-weight: 500;
            cursor: pointer;
            border-bottom: 2px solid transparent;
            transition: all 0.2s;
            background: none; border-top: none; border-left: none; border-right: none;
            font-family: inherit;
        }

        .tab:hover { color: var(--text-secondary); }
        .tab.active {
            color: var(--accent-hover);
            border-bottom-color: var(--accent);
        }

        /* Modal */
        .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.7);
            backdrop-filter: blur(4px);
            z-index: 200;
            display: none;
            align-items: center;
            justify-content: center;
        }

        .modal-overlay.active { display: flex; }

        .modal {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: var(--radius);
            max-width: 90vw;
            max-height: 90vh;
            overflow: auto;
        }

        .modal img {
            max-width: 100%;
            max-height: 80vh;
        }

        /* Animations */
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(8px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .animate-in {
            animation: fadeIn 0.4s ease forwards;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }

        .pulse { animation: pulse 2s ease-in-out infinite; }

        /* Toast */
        .toast-container {
            position: fixed;
            top: 24px; right: 24px;
            z-index: 300;
            display: flex; flex-direction: column; gap: 8px;
        }

        .toast {
            padding: 14px 20px;
            border-radius: var(--radius-sm);
            font-size: 13px; font-weight: 500;
            display: flex; align-items: center; gap: 10px;
            animation: fadeIn 0.3s ease;
            box-shadow: var(--shadow);
        }

        .toast-success { background: rgba(34,197,94,0.15); color: var(--success); border: 1px solid rgba(34,197,94,0.2); }
        .toast-error { background: rgba(239,68,68,0.15); color: var(--danger); border: 1px solid rgba(239,68,68,0.2); }
        .toast-info { background: rgba(59,130,246,0.15); color: var(--info); border: 1px solid rgba(59,130,246,0.2); }

        /* Empty State */
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-muted);
        }

        .empty-state i {
            font-size: 48px;
            margin-bottom: 16px;
            opacity: 0.3;
        }

        .empty-state p { font-size: 15px; }

        /* Responsive */
        @media (max-width: 768px) {
            .sidebar { display: none; }
            .main-content { margin-left: 0; }
            .stats-grid { grid-template-columns: 1fr 1fr; }
        }
    </style>
</head>
<body>

    <!-- Sidebar -->
    <nav class="sidebar">
        <div class="sidebar-brand">
            <div class="logo"><i class="fas fa-shield-halved"></i></div>
            <h1>Tracker</h1>
        </div>
        <div class="sidebar-nav">
            <a href="{{ route('admin.dashboard') }}" class="{{ request()->routeIs('admin.dashboard') ? 'active' : '' }}">
                <i class="fas fa-grid-2"></i> Dashboard
            </a>
        </div>
        <div class="sidebar-footer">
            <form action="{{ route('logout') }}" method="POST">
                @csrf
                <button type="submit">
                    <i class="fas fa-arrow-right-from-bracket"></i> Logout
                </button>
            </form>
        </div>
    </nav>

    <!-- Main Content -->
    <div class="main-content">
        <div class="top-bar">
            <h2>@yield('page-title', 'Dashboard')</h2>
            <div class="user-info">
                <span>{{ Auth::user()->name }}</span>
                <div class="avatar">{{ strtoupper(substr(Auth::user()->name, 0, 1)) }}</div>
            </div>
        </div>
        <div class="page-content">
            @yield('content')
        </div>
    </div>

    <!-- Toast Container -->
    <div class="toast-container" id="toastContainer"></div>

    <!-- Screenshot Modal -->
    <div class="modal-overlay" id="screenshotModal" onclick="closeModal()">
        <div class="modal" onclick="event.stopPropagation()">
            <img id="modalImage" src="" alt="Screenshot">
        </div>
    </div>

    <script>
        // CSRF for AJAX
        const csrfToken = document.querySelector('meta[name="csrf-token"]').content;

        function showToast(message, type = 'info') {
            const container = document.getElementById('toastContainer');
            const toast = document.createElement('div');
            toast.className = `toast toast-${type}`;
            const icons = { success: 'check-circle', error: 'xmark-circle', info: 'info-circle' };
            toast.innerHTML = `<i class="fas fa-${icons[type] || 'info-circle'}"></i> ${message}`;
            container.appendChild(toast);
            setTimeout(() => toast.remove(), 4000);
        }

        function openScreenshot(url) {
            document.getElementById('modalImage').src = url;
            document.getElementById('screenshotModal').classList.add('active');
        }

        function closeModal() {
            document.getElementById('screenshotModal').classList.remove('active');
        }

        async function apiPost(url, body = {}) {
            const res = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': csrfToken,
                    'Accept': 'application/json',
                },
                body: JSON.stringify(body),
            });
            return await res.json();
        }

        async function apiDelete(url) {
            const res = await fetch(url, {
                method: 'DELETE',
                headers: {
                    'X-CSRF-TOKEN': csrfToken,
                    'Accept': 'application/json',
                },
            });
            return await res.json();
        }

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeModal();
        });
    </script>

    @yield('scripts')
</body>
</html>
