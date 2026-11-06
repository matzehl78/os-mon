<?php
// Copy to config.php in THIS PRIVATE directory (outside the domain document root).
// chmod 600 config.php. Never commit/upload real credentials to GitHub.
return [
    'origin' => 'https://parents.ulmatti-studios.com',
    'host' => 'localhost',
    'port' => 3306,
    'database' => 'YOUR_DATABASE',
    'username' => 'YOUR_DATABASE_USER',
    'password' => 'REPLACE_WITH_NEW_DATABASE_PASSWORD',
    // Generate: php -r 'echo bin2hex(random_bytes(32)), PHP_EOL;'
    // Separate key for encrypted admin TOTP secrets (required only for /admin).
    'admin_key' => 'REPLACE_WITH_ANOTHER_64_RANDOM_HEX_CHARACTERS',
    'rate_secret' => 'REPLACE_WITH_64_RANDOM_HEX_CHARACTERS',
];
