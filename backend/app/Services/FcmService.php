<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class FcmService
{
    private string $projectId;
    private array $serviceAccount;

    public function __construct()
    {
        $path = storage_path('app/firebase-service-account.json');

        if (file_exists($path)) {
            $this->serviceAccount = json_decode(file_get_contents($path), true);
            $this->projectId = $this->serviceAccount['project_id'] ?? '';
        } else {
            $this->serviceAccount = [];
            $this->projectId = '';
            Log::error('Firebase service account file not found at: ' . $path);
        }
    }

    /**
     * Send a silent high-priority FCM data message using Firebase v1 API.
     */
    public function sendSilentPush(string $fcmToken, string $command, array $extraData = []): bool
    {
        if (empty($this->serviceAccount)) {
            Log::error('FCM: No service account configured.');
            return false;
        }

        $accessToken = $this->getAccessToken();
        if (!$accessToken) {
            Log::error('FCM: Failed to get access token.');
            return false;
        }

        $url = "https://fcm.googleapis.com/v1/projects/{$this->projectId}/messages:send";

        $payload = [
            'message' => [
                'token' => $fcmToken,
                'data' => array_merge([
                    'command' => $command,
                    'timestamp' => now()->toIso8601String(),
                ], array_map('strval', $extraData)),
                'android' => [
                    'priority' => 'high',
                ],
            ],
        ];

        try {
            $response = Http::withHeaders([
                'Authorization' => 'Bearer ' . $accessToken,
                'Content-Type' => 'application/json',
            ])->post($url, $payload);

            if ($response->successful()) {
                Log::info("FCM v1 sent to token: {$command}");
                return true;
            }

            Log::error("FCM v1 failed: " . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error("FCM v1 exception: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Get an OAuth2 access token from the service account (cached for 50 min).
     */
    private function getAccessToken(): ?string
    {
        return Cache::remember('fcm_access_token', 3000, function () {
            try {
                $now = time();
                $header = base64url_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
                $claim = base64url_encode(json_encode([
                    'iss' => $this->serviceAccount['client_email'],
                    'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
                    'aud' => $this->serviceAccount['token_uri'],
                    'iat' => $now,
                    'exp' => $now + 3600,
                ]));

                $signature = '';
                $key = openssl_pkey_get_private($this->serviceAccount['private_key']);
                openssl_sign("$header.$claim", $signature, $key, OPENSSL_ALGO_SHA256);
                $signature = base64url_encode($signature);

                $jwt = "$header.$claim.$signature";

                $response = Http::asForm()->post($this->serviceAccount['token_uri'], [
                    'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                    'assertion' => $jwt,
                ]);

                if ($response->successful()) {
                    return $response->json('access_token');
                }

                Log::error('FCM token exchange failed: ' . $response->body());
                return null;
            } catch (\Exception $e) {
                Log::error('FCM access token error: ' . $e->getMessage());
                return null;
            }
        });
    }
}

/**
 * URL-safe base64 encoding (no padding).
 */
if (!function_exists('base64url_encode')) {
    function base64url_encode(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
}
