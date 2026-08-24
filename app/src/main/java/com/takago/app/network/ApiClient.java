package com.takago.app.network;

import com.takago.app.data.model.UserAccount;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    public static String BASE_URL = "http://192.168.1.196/takago/public/api";
    private ApiClient() {}

    public static String profileImageUrl(int userId) {
        return profileImageUrl(userId, "");
    }

    public static String profileImageUrl(int userId, String version) {
        String root = BASE_URL.endsWith("/api") ? BASE_URL.substring(0, BASE_URL.length() - 4) : BASE_URL;
        String safeVersion = version == null ? "" : version.replaceAll("[^A-Za-z0-9]", "");
        return root + "/profile-image/" + userId + (safeVersion.isEmpty() ? "" : "?v=" + safeVersion);
    }

    public static String pickupImageUrl(int pickupId, String type, String version) {
        String root = BASE_URL.endsWith("/api") ? BASE_URL.substring(0, BASE_URL.length() - 4) : BASE_URL;
        String safeVersion = version == null ? "" : version.replaceAll("[^A-Za-z0-9]", "");
        return root + "/pickup-image/" + pickupId + "/" + type
                + (safeVersion.isEmpty() ? "" : "?v=" + safeVersion);
    }

    public static void uploadPickupImage(String token, int pickupId, String type,
                                         String photoPath, JsonCallback cb) {
        new Thread(() -> {
            String boundary = "TakaGo-" + System.currentTimeMillis();
            HttpURLConnection connection = null;
            try {
                File photo = photoPath == null ? null : new File(photoPath);
                if (photo == null || !photo.isFile()) throw new Exception("Selected image is unavailable.");
                if (photo.length() > com.takago.app.common.ImageUtils.MAX_PICKUP_IMAGE_BYTES) throw new Exception("Photo must be 4 MB or smaller.");
                connection = (HttpURLConnection) new URL(BASE_URL + "/pickups/" + pickupId + "/images").openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(10000); connection.setReadTimeout(30000);
                connection.setDoOutput(true); connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    writePart(out, boundary, "type", type); writeFilePart(out, boundary, "image", photo);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code=connection.getResponseCode();InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();StringBuilder response=new StringBuilder();
                if(input!=null)try(BufferedReader reader=new BufferedReader(new InputStreamReader(input))){String line;while((line=reader.readLine())!=null)response.append(line);}
                JSONObject json=response.length()==0?new JSONObject():new JSONObject(response.toString());if(code>=400)throw new Exception(json.optString("message","Server error ("+code+")"));cb.onSuccess(json);
            } catch(Exception error){cb.onError(friendly(error));} finally{if(connection!=null)connection.disconnect();}
        }).start();
    }

    public interface LoginCallback {
        void onSuccess(UserAccount user, String token);
        void onError(String message);
    }
    public interface JsonCallback {
        void onSuccess(JSONObject json);
        void onError(String message);
    }

    public static void login(String login, String password, LoginCallback cb) {
        authenticate("/login", new JSONObjectBuilder().put("login", login).put("password", password).build(), cb);
    }

    public static void register(String name, String email, String phone, String ward,
                                double latitude, double longitude, String password, LoginCallback cb) {
        authenticate("/register", new JSONObjectBuilder().put("name", name).put("email", email)
                .put("phone", phone).put("terms_accepted", true).put("ward_name", ward).put("latitude", latitude)
                .put("longitude", longitude).put("password", password).build(), cb);
    }

    private static void authenticate(String path, JSONObject body, LoginCallback cb) {
        new Thread(() -> {
            try {
                JSONObject res = request("POST", path, null, body);
                cb.onSuccess(parseUser(res.getJSONObject("user")), res.getString("token"));
            } catch (Exception e) {
                cb.onError(friendly(e));
            }
        }).start();
    }

    private static UserAccount parseUser(JSONObject j) {
        UserAccount u = new UserAccount();
        u.id = j.optInt("id", -1);
        u.name = j.optString("name", "");
        u.email = j.optString("email", "");
        u.phone = j.optString("phone", "");
        u.role = j.optString("role", "");
        u.status = j.optString("status", "");
        u.profileImagePath = j.optString("profile_image_url", "");
        u.municipalityId = j.optInt("municipality_id", -1);
        u.wardId = j.optInt("ward_id", -1);
        u.ward = j.optString("ward_name", "");
        u.operatorId = j.optInt("operator_id", -1);
        u.availabilityStatus = j.optString("availability_status", "");
        return u;
    }

    public static void get(String p, String t, JsonCallback cb) { call("GET", p, t, null, cb); }
    public static void post(String p, String t, JSONObject b, JsonCallback cb) { call("POST", p, t, b, cb); }
    public static void patch(String p, String t, JSONObject b, JsonCallback cb) { call("PATCH", p, t, b, cb); }
    public static void delete(String p, String t, JsonCallback cb) { call("DELETE", p, t, null, cb); }

    public static void updateProfile(String token, String name, String phone, String email,
                                     String password, String photoPath, JsonCallback cb) {
        new Thread(() -> {
            String boundary = "TakaGo-" + System.currentTimeMillis();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + "/me").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    writePart(out, boundary, "name", name);
                    writePart(out, boundary, "phone", phone);
                    writePart(out, boundary, "email", email);
                    if (password != null && !password.isEmpty()) {
                        writePart(out, boundary, "password", password);
                        writePart(out, boundary, "password_confirmation", password);
                    }
                    File photo = photoPath == null ? null : new File(photoPath);
                    if (photo != null && photo.isFile() && photo.length() > com.takago.app.common.ImageUtils.MAX_PROFILE_IMAGE_BYTES) throw new Exception("Profile photo must be 3 MB or smaller.");
                    if (photo != null && photo.isFile()) writeFilePart(out, boundary, "profile_image", photo);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                StringBuilder response = new StringBuilder();
                if (input != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                    String line; while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONObject json = response.length() == 0 ? new JSONObject() : new JSONObject(response.toString());
                if (code >= 400) throw new Exception(json.optString("message", "Server error (" + code + ")"));
                cb.onSuccess(json);
            } catch (Exception e) { cb.onError(friendly(e)); }
            finally { if (connection != null) connection.disconnect(); }
        }).start();
    }

    private static void writePart(OutputStream out, String boundary, String name, String value) throws Exception {
        if (value == null) return;
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(OutputStream out, String boundary, String name, File file) throws Exception {
        String lower = file.getName().toLowerCase();
        String mime = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp")
                ? "image/webp" : "image/jpeg";
        String extension = lower.endsWith(".png") ? ".png" : lower.endsWith(".webp")
                ? ".webp" : lower.endsWith(".jpeg") ? ".jpeg" : ".jpg";
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"profile" + extension + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void call(String method, String path, String token, JSONObject body, JsonCallback cb) {
        new Thread(() -> {
            try { cb.onSuccess(request(method, path, token, body)); }
            catch (Exception e) { cb.onError(friendly(e)); }
        }).start();
    }

    private static JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder response = new StringBuilder();
        if (input != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        JSONObject json = response.length() == 0 ? new JSONObject() : new JSONObject(response.toString());
        if (code >= 400) throw new Exception(json.optString("message", "Server error (" + code + ")"));
        return json;
    }

    private static String friendly(Exception e) {
        String message = e.getMessage();
        if (message != null && (message.toLowerCase().contains("connect") || message.toLowerCase().contains("failed") || message.toLowerCase().contains("timeout")))
            return "Cannot reach TakaGo server. Check Wi-Fi, XAMPP and API address.";
        return message == null ? "Network request failed" : message;
    }

    private static final class JSONObjectBuilder {
        private final JSONObject object = new JSONObject();
        JSONObjectBuilder put(String key, Object value) {
            try { object.put(key, value); } catch (Exception ignored) {}
            return this;
        }
        JSONObject build() { return object; }
    }
}
