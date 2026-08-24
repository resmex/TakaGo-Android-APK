package com.takago.app.notifications;

import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.Build;
import androidx.annotation.NonNull;import androidx.core.app.NotificationCompat;import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessaging;import com.google.firebase.messaging.FirebaseMessagingService;import com.google.firebase.messaging.RemoteMessage;
import com.takago.app.R;import com.takago.app.admin.*;import com.takago.app.db.SessionManager;import com.takago.app.driver.*;import com.takago.app.network.*;import com.takago.app.operator.*;import com.takago.app.resident.*;import org.json.JSONObject;import java.util.Locale;import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "takago_updates";
    public static final String SCHEDULE_CHANNEL_ID = "takago_schedule_reminders";

    public static void registerAuthenticatedDevice(Context context) {
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedIn() || session.getApiToken().isEmpty()) return;
        try { FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> uploadToken(context, token)); }
        catch (IllegalStateException ignored) { /* google-services.json has not been installed yet. */ }
    }
    public static void requestNotificationPermission(Activity activity) {
        createChannel(activity);
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 700);
    }

    private static void uploadToken(Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedIn() || session.getApiToken().isEmpty()) return;
        try { ApiClient.post("/device-token", session.getApiToken(), new JSONObject().put("token", token), new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { }
            public void onError(String message) { }
        }); } catch (Exception ignored) { }
    }

    @Override public void onNewToken(@NonNull String token) { super.onNewToken(token); uploadToken(this, token); }

    @Override public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String,String> data=remoteMessage.getData();
        String title=data.get("title"), body=data.get("body"), type=data.get("type");
        if(remoteMessage.getNotification()!=null){if(title==null)title=remoteMessage.getNotification().getTitle();if(body==null)body=remoteMessage.getNotification().getBody();}
        if(title==null||title.trim().isEmpty())title="takaGo update";if(body==null||body.trim().isEmpty())body="Open takaGo to view the latest update.";
        ServerSyncManager.syncAll(getApplicationContext()); // Keep the existing Laravel/SQLite notification record authoritative.
        showNotification(title,body,type,data);
    }

    private void showNotification(String title,String body,String type,Map<String,String> data){createChannel(this);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;Intent target=targetIntent(type,data);target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent tap=PendingIntent.getActivity(this,(int)System.currentTimeMillis(),target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);boolean schedule=type!=null&&type.toLowerCase(Locale.US).contains("schedule");String channel=schedule?SCHEDULE_CHANNEL_ID:CHANNEL_ID;NotificationCompat.Builder b=new NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_notifications).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(schedule?NotificationCompat.CATEGORY_REMINDER:NotificationCompat.CATEGORY_STATUS).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setDefaults(NotificationCompat.DEFAULT_ALL).setAutoCancel(true).setContentIntent(tap);NotificationManagerCompat.from(this).notify((int)System.currentTimeMillis(),b.build());}

    private Intent targetIntent(String type,Map<String,String> data){SessionManager s=new SessionManager(this);String role=s.getRole()==null?"":s.getRole().toLowerCase(Locale.US);String event=type==null?"":type.toLowerCase(Locale.US);Intent i;if(event.contains("complaint")&&"resident".equals(role))i=new Intent(this,ResidentComplaintsActivity.class);else if("resident".equals(role)&&event.contains("schedule"))i=new Intent(this,ResidentSchedulesActivity.class);else if("resident".equals(role))i=new Intent(this,ResidentHomeActivity.class);else if("driver".equals(role))i=new Intent(this,DriverHomeActivity.class);else if("operator".equals(role)||"truck_owner".equals(role))i=new Intent(this,TruckOwnerHomeActivity.class);else if("ward_admin".equals(role))i=new Intent(this,WardAdminHomeActivity.class);else if("municipal_admin".equals(role))i=new Intent(this,MunicipalAdminHomeActivity.class);else i=new Intent(this,NotificationActivity.class);String pickup=data.get("pickup_id");if(pickup!=null)try{i.putExtra("pickupId",Integer.parseInt(pickup));}catch(NumberFormatException ignored){}return i;}

    public static void createChannel(Context context){if(Build.VERSION.SDK_INT>=26){NotificationManager manager=context.getSystemService(NotificationManager.class);NotificationChannel c=new NotificationChannel(CHANNEL_ID,"takaGo updates",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Pickup, driver, payment and complaint status updates");c.enableVibration(true);manager.createNotificationChannel(c);NotificationChannel schedules=new NotificationChannel(SCHEDULE_CHANNEL_ID,"Collection schedule reminders",NotificationManager.IMPORTANCE_HIGH);schedules.setDescription("Upcoming waste collection schedules for your street and ward");schedules.enableVibration(true);schedules.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(schedules);}}
}
