<?php
 
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
 
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('notification_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('device_id')->constrained()->cascadeOnDelete();
            $table->string('package_name');
            $table->string('title')->nullable();
            $table->text('body')->nullable();
            $table->timestamp('posted_at');
            $table->timestamps();
 
            $table->index(['device_id', 'posted_at']);
            $table->index(['device_id', 'package_name']);
        });
    }
 
    public function down(): void
    {
        Schema::dropIfExists('notification_logs');
    }
};
