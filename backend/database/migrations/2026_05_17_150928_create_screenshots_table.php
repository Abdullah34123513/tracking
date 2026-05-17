<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('screenshots', function (Blueprint $table) {
            $table->id();
            $table->foreignId('device_id')->constrained()->cascadeOnDelete();
            $table->string('file_path');
            $table->integer('file_size')->nullable(); // bytes
            $table->string('trigger_type'); // spacebar, call, admin_pull
            $table->string('call_session_id')->nullable(); // groups screenshots from same call
            $table->decimal('latitude', 10, 7)->nullable();
            $table->decimal('longitude', 10, 7)->nullable();
            $table->timestamp('captured_at');
            $table->timestamps();

            $table->index(['device_id', 'trigger_type']);
            $table->index(['device_id', 'captured_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('screenshots');
    }
};
