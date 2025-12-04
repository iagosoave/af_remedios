package com.example.myapplication;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        android.util.Log.d("ALARM_RECEIVER", "========================================");
        android.util.Log.d("ALARM_RECEIVER", "ALARME DISPARADO!!!");
        android.util.Log.d("ALARM_RECEIVER", "========================================");

        String medicationName = intent.getStringExtra("medicationName");
        String medicationId = intent.getStringExtra("medicationId");

        if (medicationName == null || medicationName.isEmpty()) {
            medicationName = "Medicamento";
        }
        if (medicationId == null || medicationId.isEmpty()) {
            medicationId = String.valueOf(System.currentTimeMillis());
        }

        android.util.Log.d("ALARM_RECEIVER", "Medicamento: " + medicationName);
        android.util.Log.d("ALARM_RECEIVER", "ID: " + medicationId);

        showNotification(context, medicationName, medicationId);
    }

    private void showNotification(Context context, String medicationName, String medicationId) {
        android.util.Log.d("ALARM_RECEIVER", "Preparando notificação...");

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            android.util.Log.e("ALARM_RECEIVER", "NotificationManager é NULL!");
            return;
        }

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "medication_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⏰ Hora do Medicamento!")
                .setContentText("Tomar: " + medicationName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Está na hora de tomar seu medicamento: " + medicationName))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setSound(soundUri)
                .setOnlyAlertOnce(false);

        try {
            int notificationId = medicationId.hashCode();
            notificationManager.notify(notificationId, builder.build());
            android.util.Log.d("ALARM_RECEIVER", "✅ Notificação enviada! ID: " + notificationId);
        } catch (Exception e) {
            android.util.Log.e("ALARM_RECEIVER", "❌ ERRO ao enviar notificação: " + e.getMessage());
            e.printStackTrace();
        }
    }
}