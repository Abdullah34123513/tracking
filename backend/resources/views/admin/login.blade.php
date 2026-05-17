<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login — Tracker Admin</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css" rel="stylesheet">
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Inter', sans-serif;
            background: #0a0e1a;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            overflow: hidden;
        }

        /* Animated background */
        body::before {
            content: '';
            position: absolute;
            width: 600px; height: 600px;
            background: radial-gradient(circle, rgba(99,102,241,0.15), transparent 60%);
            top: -200px; right: -200px;
            border-radius: 50%;
            animation: float 8s ease-in-out infinite;
        }

        body::after {
            content: '';
            position: absolute;
            width: 400px; height: 400px;
            background: radial-gradient(circle, rgba(168,85,247,0.1), transparent 60%);
            bottom: -100px; left: -100px;
            border-radius: 50%;
            animation: float 6s ease-in-out infinite reverse;
        }

        @keyframes float {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-30px); }
        }

        .login-container {
            width: 420px;
            z-index: 10;
        }

        .login-brand {
            text-align: center;
            margin-bottom: 40px;
        }

        .login-brand .logo {
            width: 56px; height: 56px;
            background: linear-gradient(135deg, #6366f1, #a855f7);
            border-radius: 14px;
            display: inline-flex;
            align-items: center; justify-content: center;
            font-size: 24px; color: #fff;
            margin-bottom: 16px;
            box-shadow: 0 8px 32px rgba(99,102,241,0.3);
        }

        .login-brand h1 {
            font-size: 24px; font-weight: 800;
            background: linear-gradient(135deg, #6366f1, #a855f7);
            -webkit-background-clip: text; -webkit-text-fill-color: transparent;
        }

        .login-brand p {
            color: #64748b; font-size: 14px; margin-top: 8px;
        }

        .login-card {
            background: #1a1f35;
            border: 1px solid #2a3150;
            border-radius: 16px;
            padding: 36px;
            box-shadow: 0 8px 40px rgba(0,0,0,0.4);
        }

        .form-group {
            margin-bottom: 20px;
        }

        .form-group label {
            display: block;
            font-size: 13px; font-weight: 600;
            color: #94a3b8;
            margin-bottom: 8px;
        }

        .form-group .input-wrapper {
            position: relative;
        }

        .form-group .input-wrapper i {
            position: absolute;
            left: 14px; top: 50%;
            transform: translateY(-50%);
            color: #4b5563; font-size: 14px;
        }

        .form-group input {
            width: 100%;
            padding: 12px 16px 12px 42px;
            background: #111827;
            border: 1px solid #2a3150;
            border-radius: 10px;
            color: #f1f5f9;
            font-size: 14px;
            font-family: inherit;
            transition: all 0.2s;
            outline: none;
        }

        .form-group input:focus {
            border-color: #6366f1;
            box-shadow: 0 0 0 3px rgba(99,102,241,0.15);
        }

        .form-group input::placeholder { color: #4b5563; }

        .remember-row {
            display: flex; align-items: center; gap: 8px;
            margin-bottom: 24px;
        }

        .remember-row input[type="checkbox"] {
            accent-color: #6366f1;
        }

        .remember-row label {
            font-size: 13px; color: #94a3b8; cursor: pointer;
        }

        .btn-login {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #6366f1, #7c3aed);
            color: #fff;
            border: none;
            border-radius: 10px;
            font-size: 15px; font-weight: 700;
            cursor: pointer;
            transition: all 0.3s;
            font-family: inherit;
        }

        .btn-login:hover {
            box-shadow: 0 8px 24px rgba(99,102,241,0.4);
            transform: translateY(-1px);
        }

        .error-msg {
            background: rgba(239,68,68,0.1);
            border: 1px solid rgba(239,68,68,0.2);
            color: #ef4444;
            padding: 12px 16px;
            border-radius: 10px;
            font-size: 13px;
            margin-bottom: 20px;
            display: flex; align-items: center; gap: 8px;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-brand">
            <div class="logo"><i class="fas fa-shield-halved"></i></div>
            <h1>Tracker Admin</h1>
            <p>Sign in to your admin account</p>
        </div>

        <div class="login-card">
            @if ($errors->any())
                <div class="error-msg">
                    <i class="fas fa-circle-exclamation"></i>
                    {{ $errors->first() }}
                </div>
            @endif

            <form method="POST" action="{{ route('login.submit') }}">
                @csrf
                <div class="form-group">
                    <label for="email">Email Address</label>
                    <div class="input-wrapper">
                        <i class="fas fa-envelope"></i>
                        <input type="email" id="email" name="email" value="{{ old('email') }}" placeholder="admin@tracker.app" required autofocus>
                    </div>
                </div>

                <div class="form-group">
                    <label for="password">Password</label>
                    <div class="input-wrapper">
                        <i class="fas fa-lock"></i>
                        <input type="password" id="password" name="password" placeholder="••••••••" required>
                    </div>
                </div>

                <div class="remember-row">
                    <input type="checkbox" id="remember" name="remember">
                    <label for="remember">Remember me</label>
                </div>

                <button type="submit" class="btn-login">
                    <i class="fas fa-arrow-right-to-bracket"></i> Sign In
                </button>
            </form>
        </div>
    </div>
</body>
</html>
