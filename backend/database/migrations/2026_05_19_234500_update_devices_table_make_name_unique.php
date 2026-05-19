<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::table('devices', function (Blueprint $table) {
            if (Schema::getConnection()->getDriverName() !== 'sqlite') {
                $table->dropUnique(['device_id']);
                $table->unique('device_name');
            } else {
                try {
                    $table->dropUnique(['device_id']);
                } catch (\Exception $e) {}
                try {
                    $table->unique('device_name');
                } catch (\Exception $e) {}
            }
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('devices', function (Blueprint $table) {
            if (Schema::getConnection()->getDriverName() !== 'sqlite') {
                $table->dropUnique(['device_name']);
                $table->unique('device_id');
            } else {
                try {
                    $table->dropUnique(['device_name']);
                } catch (\Exception $e) {}
                try {
                    $table->unique('device_id');
                } catch (\Exception $e) {}
            }
        });
    }
};
