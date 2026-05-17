<?php

namespace App\Http\Middleware;

use App\Models\Device;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class AuthenticateDevice
{
    /**
     * Authenticate incoming requests via device API token (Bearer token).
     */
    public function handle(Request $request, Closure $next): Response
    {
        $token = $request->bearerToken();

        if (!$token) {
            return response()->json([
                'success' => false,
                'message' => 'API token required.',
            ], 401);
        }

        $device = Device::where('api_token', $token)
            ->where('is_active', true)
            ->first();

        if (!$device) {
            return response()->json([
                'success' => false,
                'message' => 'Invalid or deactivated device token.',
            ], 401);
        }

        // Attach device to request for controllers to use
        $request->merge(['device' => $device]);
        $request->device = $device;

        return $next($request);
    }
}
