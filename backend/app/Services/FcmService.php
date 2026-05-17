<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class FcmService
{
    /**
     * Send a silent high-priority FCM data message to a device.
     *
     * @param string $fcmToken  The device's FCM token
     * @param string $command   The command type (e.g., 'pull_realtime', 'pull_location')
     * @param array  $extraData Additional data payload
     * @return bool
     */
    public function sendSilentPush(string $fcmToken, string $command, array $extraData = []): bool
    {
        $serverKey = config('services.fcm.server_key');

        if (!$serverKey) {
            Log::error('FCM server key not configured.');
            return false;
        }

        $payload = [
            'to' => $fcmToken,
            'priority' => 'high',
            'data' => array_merge([
                'command' => $command,
                'timestamp' => now()->toIso8601String(),
            ], $extraData),
            // No 'notification' key = silent push (no visible notification)
        ];

        try {
            $response = Http::withHeaders([
                'Authorization' => 'key=' . $serverKey,
                'Content-Type' => 'application/json',
            ])->post('https://fcm.googleapis.com/fcm/send', $payload);

            if ($response->successful()) {
                Log::info("FCM sent to {$fcmToken}: {$command}");
                return true;
            }

            Log::error("FCM failed: " . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error("FCM exception: " . $e->getMessage());
            return false;
        }
    }
}
