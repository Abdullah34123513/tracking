<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('call_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('device_id')->constrained()->cascadeOnDelete();
            $table->string('call_type'); // incoming, outgoing, missed
            $table->string('source'); // phone, whatsapp, imo, telegram, etc.
            $table->string('phone_number')->nullable();
            $table->string('contact_name')->nullable();
            $table->integer('duration')->nullable(); // seconds
            $table->timestamp('call_date');
            $table->timestamps();

            $table->index(['device_id', 'call_date']);
            $table->index(['device_id', 'source']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('call_logs');
    }
};
