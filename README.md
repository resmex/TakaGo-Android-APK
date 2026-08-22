# TakaGo

TakaGo is a waste-collection system with two parts:

- Laravel web/API: `C:\xampp\htdocs\takago`
- Android app: `C:\Users\HP\AndroidStudioProjects\takaGo`

## Requirements

- XAMPP (Apache, PHP 8.2+, and MySQL if used)
- Composer
- Node.js and npm
- Android Studio with Android SDK 36
- JDK 11 or newer

## Run the Laravel web/API

Open PowerShell:

```powershell
cd C:\xampp\htdocs\takago
composer install
Copy-Item .env.example .env   # Run only when .env does not exist
php artisan key:generate
php artisan migrate --seed
npm install
npm run build
```

Then choose one way to run it.

### Option 1: XAMPP (recommended for a physical phone)

1. Open XAMPP Control Panel.
2. Start **Apache** (and **MySQL** if `.env` uses MySQL).
3. Open `http://localhost/takago/public` in the computer's browser.
4. On a phone connected to the same Wi-Fi, use:

   `http://YOUR_COMPUTER_IP/takago/public`

Find the computer's IPv4 address with:

```powershell
ipconfig
```

Allow Apache through Windows Firewall if the phone cannot connect.

### Option 2: Laravel development server

```powershell
cd C:\xampp\htdocs\takago
php artisan serve --host=0.0.0.0 --port=8000
```

Web URL: `http://localhost:8000`

API URL from a physical phone: `http://YOUR_COMPUTER_IP:8000/api`

Keep the terminal open while using the app.

For frontend development, run this in another terminal:

```powershell
cd C:\xampp\htdocs\takago
npm run dev
```

## Run the Android app

1. Open Android Studio.
2. Select **Open** and choose `C:\Users\HP\AndroidStudioProjects\takaGo`.
3. Wait for Gradle sync to finish.
4. In `local.properties`, keep the Android SDK path and add your own Google Maps key:

   ```properties
   sdk.dir=C\:\\Users\\HP\\AppData\\Local\\Android\\Sdk
   GOOGLE_MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
   ```

5. Open `app\src\main\java\com\takago\app\network\ApiClient.java` and set `BASE_URL`:

   - Physical phone with XAMPP: `http://YOUR_COMPUTER_IP/takago/public/api`
   - Android emulator with XAMPP: `http://10.0.2.2/takago/public/api`
   - Physical phone with `artisan serve`: `http://YOUR_COMPUTER_IP:8000/api`
   - Android emulator with `artisan serve`: `http://10.0.2.2:8000/api`

6. Connect a phone with USB debugging enabled, or start an emulator.
7. Select the device and click **Run**.

To build from PowerShell instead:

```powershell
cd C:\Users\HP\AndroidStudioProjects\takaGo
.\gradlew.bat assembleDebug
```

The APK is created at `app\build\outputs\apk\debug\app-debug.apk`.

## Quick troubleshooting

- **Android app cannot connect:** confirm the backend is running, both devices are on the same network, the IP in `ApiClient.java` is current, and Windows Firewall allows the connection.
- **Database error:** check the `DB_*` values in `.env`, then run `php artisan migrate --seed`.
- **Laravel configuration changed:** run `php artisan optimize:clear`.
- **Maps are blank:** enable the required Google Maps/Places APIs and use a valid key in `local.properties`.
- **Gradle error:** confirm Android SDK 36 and a compatible JDK are selected in Android Studio.

Do not commit `.env`, `local.properties`, or API keys to source control.
