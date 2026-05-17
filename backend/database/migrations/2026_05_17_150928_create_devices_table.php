<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('devices', function (Blueprint $table) {
            $table->id();
            $table->string('device_name');
            $table->string('device_id')->unique(); // Android device unique ID
            $table->string('model')->nullable();
            $table->string('android_version')->nullable();
            $table->string('fcm_token')->nullable();
            $table->string('api_token')->unique(); // Sanctum-like simple token for device auth
            $table->integer('battery_level')->nullable();
            $table->decimal('last_lat', 10, 7)->nullable();
            $table->decimal('last_lng', 10, 7)->nullable();
            $table->enum('status', ['online', 'offline', 'setup'])->default('setup');
            $table->timestamp('last_seen_at')->nullable();
            $table->boolean('is_active')->default(true);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('devices');
    }
};
