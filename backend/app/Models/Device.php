<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Device extends Model
{
    use HasFactory;

    protected $fillable = [
        'device_name',
        'device_id',
        'model',
        'android_version',
        'fcm_token',
        'api_token',
        'battery_level',
        'last_lat',
        'last_lng',
        'status',
        'last_seen_at',
        'is_active',
    ];

    protected function casts(): array
    {
        return [
            'last_seen_at' => 'datetime',
            'is_active' => 'boolean',
            'last_lat' => 'decimal:7',
            'last_lng' => 'decimal:7',
        ];
    }

    public function screenshots(): HasMany
    {
        return $this->hasMany(Screenshot::class);
    }

    public function callLogs(): HasMany
    {
        return $this->hasMany(CallLog::class);
    }

    public function activityLogs(): HasMany
    {
        return $this->hasMany(ActivityLog::class);
    }
}
