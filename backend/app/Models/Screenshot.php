<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Screenshot extends Model
{
    use HasFactory;

    protected $fillable = [
        'device_id',
        'file_path',
        'file_size',
        'trigger_type',
        'call_session_id',
        'latitude',
        'longitude',
        'captured_at',
    ];

    protected function casts(): array
    {
        return [
            'captured_at' => 'datetime',
            'latitude' => 'decimal:7',
            'longitude' => 'decimal:7',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(Device::class);
    }
}
