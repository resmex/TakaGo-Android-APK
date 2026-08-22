package com.takago.app.common;
import com.takago.app.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Loads a resident/driver's saved profile photo, falling back to a clean default icon. */
public class ImageUtils {
    private static final LruCache<String, Bitmap> AVATAR_CACHE = new LruCache<>(20);

    public static void loadAvatar(ImageView imageView, String profileImagePath) {
        if (profileImagePath == null || profileImagePath.trim().isEmpty()) {
            imageView.setTag(null);
            imageView.setImageResource(R.drawable.ic_person_placeholder);
            return;
        }
        if (profileImagePath.startsWith("http://") || profileImagePath.startsWith("https://")) {
            String remoteUrl = profileImagePath;
            if (remoteUrl.equals(imageView.getTag())) return;
            Object previousTag = imageView.getTag();
            imageView.setTag(remoteUrl);
            Bitmap cached = AVATAR_CACHE.get(remoteUrl);
            if (cached != null) {
                imageView.setImageBitmap(cached);
                return;
            }
            if (previousTag == null) imageView.setImageDrawable(null);
            new Thread(() -> {
                try (InputStream input = new java.net.URL(remoteUrl).openStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        AVATAR_CACHE.put(remoteUrl, bitmap);
                        imageView.post(() -> {
                            if (remoteUrl.equals(imageView.getTag())) imageView.setImageBitmap(bitmap);
                        });
                    }
                } catch (IOException ignored) {
                    // Keep the default profile icon when the server image is unavailable.
                }
            }).start();
            return;
        }
        File file = new File(profileImagePath);
        if (file.exists()) {
            imageView.setTag(profileImagePath);
            Bitmap bitmap = BitmapFactory.decodeFile(profileImagePath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * Photos picked from the gallery/files app or taken with the camera arrive as a
     * content:// Uri - this copies the bytes into our own app-private folder and returns
     * the resulting file's absolute path (or null if the copy failed).
     */
    public static String copyUriToAppFile(Context context, Uri sourceUri, String filePrefix) {
        try {
            InputStream input = context.getContentResolver().openInputStream(sourceUri);
            if (input == null) {
                return null;
            }
            String mime = context.getContentResolver().getType(sourceUri);
            String extension = mime != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) : null;
            if (extension == null || extension.trim().isEmpty()) extension = "jpg";
            File destFile = new File(context.getExternalFilesDir("Pictures"),
                    filePrefix + "_" + System.currentTimeMillis() + "." + extension.toLowerCase());
            FileOutputStream output = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.close();
            input.close();
            return destFile.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }
}
