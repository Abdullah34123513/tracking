<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('activity_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('device_id')->constrained()->cascadeOnDelete();
            $table->string('event_type'); // spacebar_press, call_started, call_ended, admin_pull, app_opened
            $table->json('metadata')->nullable(); // flexible payload
            $table->timestamp('event_at');
            $table->timestamps();

            $table->index(['device_id', 'event_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('activity_logs');
    }
};
