<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class CallLog extends Model
{
    use HasFactory;

    protected $fillable = [
        'device_id',
        'call_type',
        'source',
        'phone_number',
        'contact_name',
        'duration',
        'call_date',
    ];

    protected function casts(): array
    {
        return [
            'call_date' => 'datetime',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(Device::class);
    }
}
