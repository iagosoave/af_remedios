package com.example.myapplication;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MedicationAdapter adapter;
    private List<Medication> medicationList;
    private FloatingActionButton fabAddMedication;
    private TextView tvEmptyState;
    private FrameLayout dialogContainer;
    private AutoCompleteTextView etMedicationName;
    private TextInputEditText etMedicationDescription;
    private Button btnSelectTime, btnSave, btnCancel;
    private TextView tvSelectedTime, tvDialogTitle, tvApiInfo;
    private ProgressBar progressBar;

    private DatabaseReference databaseReference;
    private String editingMedicationId = null;
    private int selectedHour = 8;
    private int selectedMinute = 0;
    private ExecutorService executorService;
    private List<String> drugSuggestions;
    private ArrayAdapter<String> autoCompleteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadLocale();
        setContentView(R.layout.activity_main);

        databaseReference = FirebaseDatabase.getInstance().getReference("medications");
        executorService = Executors.newSingleThreadExecutor();
        drugSuggestions = new ArrayList<>();

        createNotificationChannel();
        requestNotificationPermission();

        initializeViews();
        setupRecyclerView();
        setupListeners();
        loadMedications();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewMedications);
        fabAddMedication = findViewById(R.id.fabAddMedication);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        dialogContainer = findViewById(R.id.dialogContainer);
        etMedicationName = findViewById(R.id.etMedicationName);
        etMedicationDescription = findViewById(R.id.etMedicationDescription);
        btnSelectTime = findViewById(R.id.btnSelectTime);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        tvDialogTitle = findViewById(R.id.tvDialogTitle);
        tvApiInfo = findViewById(R.id.tvApiInfo);
        progressBar = findViewById(R.id.progressBar);

        autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, drugSuggestions);
        etMedicationName.setAdapter(autoCompleteAdapter);
    }

    private void setupRecyclerView() {
        medicationList = new ArrayList<>();
        adapter = new MedicationAdapter(medicationList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        fabAddMedication.setOnClickListener(v -> showAddEditDialog(null));

        btnSelectTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    MainActivity.this,
                    (view, hourOfDay, minute) -> {
                        selectedHour = hourOfDay;
                        selectedMinute = minute;
                        tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                    },
                    selectedHour,
                    selectedMinute,
                    true
            );
            timePickerDialog.show();
        });

        btnCancel.setOnClickListener(v -> hideDialog());
        btnSave.setOnClickListener(v -> saveMedication());
        dialogContainer.setOnClickListener(v -> hideDialog());
        findViewById(R.id.cardDialog).setOnClickListener(v -> {});

        etMedicationName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 2) {
                    searchDrugInfo(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });



    }

    private void searchDrugInfo(String query) {
        tvApiInfo.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                String urlString = "https://api.fda.gov/drug/label.json?search=openfda.brand_name:" +
                        query.replace(" ", "+") + "&limit=5";
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if (jsonResponse.has("results")) {
                        JSONArray results = jsonResponse.getJSONArray("results");

                        drugSuggestions.clear();
                        StringBuilder infoText = new StringBuilder();

                        for (int i = 0; i < results.length() && i < 5; i++) {
                            JSONObject drug = results.getJSONObject(i);

                            if (drug.has("openfda")) {
                                JSONObject openfda = drug.getJSONObject("openfda");

                                if (openfda.has("brand_name")) {
                                    JSONArray brandNames = openfda.getJSONArray("brand_name");
                                    if (brandNames.length() > 0) {
                                        String brandName = brandNames.getString(0);
                                        drugSuggestions.add(brandName);
                                    }
                                }

                                if (i == 0) {
                                    if (openfda.has("manufacturer_name")) {
                                        JSONArray manufacturers = openfda.getJSONArray("manufacturer_name");
                                        if (manufacturers.length() > 0) {
                                            infoText.append(getString(R.string.manufacturer)).append(": ")
                                                    .append(manufacturers.getString(0)).append("\n");
                                        }
                                    }

                                    if (openfda.has("generic_name")) {
                                        JSONArray genericNames = openfda.getJSONArray("generic_name");
                                        if (genericNames.length() > 0) {
                                            infoText.append(getString(R.string.generic_name)).append(": ")
                                                    .append(genericNames.getString(0));
                                        }
                                    }
                                }
                            }
                        }

                        String finalInfo = infoText.toString();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            autoCompleteAdapter.notifyDataSetChanged();
                            if (!finalInfo.isEmpty()) {
                                tvApiInfo.setText(finalInfo);
                                tvApiInfo.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        });
    }

    private void showAddEditDialog(Medication medication) {
        editingMedicationId = medication != null ? medication.getId() : null;

        if (medication != null) {
            tvDialogTitle.setText(R.string.edit_medication);
            etMedicationName.setText(medication.getName());
            etMedicationDescription.setText(medication.getDescription());

            String[] timeParts = medication.getTime().split(":");
            selectedHour = Integer.parseInt(timeParts[0]);
            selectedMinute = Integer.parseInt(timeParts[1]);
            tvSelectedTime.setText(medication.getTime());
        } else {
            tvDialogTitle.setText(R.string.add_medication);
            etMedicationName.setText("");
            etMedicationDescription.setText("");
            selectedHour = 8;
            selectedMinute = 0;
            tvSelectedTime.setText("--:--");
        }

        tvApiInfo.setVisibility(View.GONE);
        dialogContainer.setVisibility(View.VISIBLE);
    }

    private void hideDialog() {
        dialogContainer.setVisibility(View.GONE);
    }

    private void saveMedication() {
        String name = etMedicationName.getText().toString().trim();
        String description = etMedicationDescription.getText().toString().trim();
        String time = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.enter_medication_name, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingMedicationId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("description", description);
            updates.put("time", time);
            updates.put("taken", false);

            databaseReference.child(editingMedicationId).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(MainActivity.this, R.string.medication_updated, Toast.LENGTH_SHORT).show();
                        hideDialog();
                        scheduleNotification(editingMedicationId, name, time);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, R.string.error_updating, Toast.LENGTH_SHORT).show()
                    );
        } else {
            String id = databaseReference.push().getKey();
            Medication medication = new Medication(id, name, description, time, false);

            databaseReference.child(id).setValue(medication)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(MainActivity.this, R.string.medication_added, Toast.LENGTH_SHORT).show();
                        hideDialog();
                        scheduleNotification(id, name, time);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, R.string.error_adding, Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private void loadMedications() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                medicationList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Medication medication = dataSnapshot.getValue(Medication.class);
                    if (medication != null) {
                        medicationList.add(medication);
                    }
                }
                adapter.notifyDataSetChanged();
                tvEmptyState.setVisibility(medicationList.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, R.string.error_loading, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteMedication(String id) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_medication)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    databaseReference.child(id).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(MainActivity.this, R.string.medication_deleted, Toast.LENGTH_SHORT).show();
                                cancelNotification(id);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(MainActivity.this, R.string.error_deleting, Toast.LENGTH_SHORT).show()
                            );
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleTaken(Medication medication) {
        databaseReference.child(medication.getId()).child("taken").setValue(!medication.isTaken());
    }

    private void scheduleNotification(String medicationId, String medicationName, String time) {
        android.util.Log.d("SCHEDULE_NOTIFICATION", "========================================");
        android.util.Log.d("SCHEDULE_NOTIFICATION", "AGENDANDO NOTIFICAÇÃO");
        android.util.Log.d("SCHEDULE_NOTIFICATION", "Medicamento: " + medicationName);
        android.util.Log.d("SCHEDULE_NOTIFICATION", "Horário: " + time);
        android.util.Log.d("SCHEDULE_NOTIFICATION", "ID: " + medicationId);

        try {
            String[] timeParts = time.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                android.util.Log.d("SCHEDULE_NOTIFICATION", "Horário já passou, agendando para amanhã");
            }

            long triggerTime = calendar.getTimeInMillis();
            android.util.Log.d("SCHEDULE_NOTIFICATION", "Tempo do disparo: " + triggerTime);
            android.util.Log.d("SCHEDULE_NOTIFICATION", "Tempo atual: " + System.currentTimeMillis());
            android.util.Log.d("SCHEDULE_NOTIFICATION", "Diferença (ms): " + (triggerTime - System.currentTimeMillis()));

            Intent intent = new Intent(this, MedicationAlarmReceiver.class);
            intent.setAction("com.example.myapplication.MEDICATION_ALARM");
            intent.putExtra("medicationName", medicationName);
            intent.putExtra("medicationId", medicationId);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    medicationId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        android.util.Log.d("SCHEDULE_NOTIFICATION", " Alarme agendado com setExactAndAllowWhileIdle");
                        Toast.makeText(this, " Lembrete agendado para " + time, Toast.LENGTH_SHORT).show();
                    } else {
                        android.util.Log.e("SCHEDULE_NOTIFICATION", " Sem permissão para alarmes exatos!");
                        Toast.makeText(this, "Permissão de alarme necessária!", Toast.LENGTH_LONG).show();
                        Intent permIntent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        startActivity(permIntent);
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    android.util.Log.d("SCHEDULE_NOTIFICATION", " Alarme agendado com setExact");
                    Toast.makeText(this, " Lembrete agendado para " + time, Toast.LENGTH_SHORT).show();
                }
            } else {
                android.util.Log.e("SCHEDULE_NOTIFICATION", "AlarmManager é NULL!");
            }

            android.util.Log.d("SCHEDULE_NOTIFICATION", "========================================");

        } catch (Exception e) {
            android.util.Log.e("SCHEDULE_NOTIFICATION", " ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendImmediateNotification(String medicationName) {
        android.util.Log.d("IMMEDIATE_NOTIFICATION", "========================================");
        android.util.Log.d("IMMEDIATE_NOTIFICATION", "ENVIANDO NOTIFICAÇÃO IMEDIATA");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            android.util.Log.e("IMMEDIATE_NOTIFICATION", "NotificationManager é null!");
            Toast.makeText(this, "ERRO: NotificationManager null", Toast.LENGTH_LONG).show();
            return;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "medication_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(" NOTIFICAÇÃO DE TESTE")
                .setContentText("Medicamento: " + medicationName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Esta é uma notificação de teste para: " + medicationName))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setSound(soundUri);

        try {
            notificationManager.notify(999, builder.build());
            android.util.Log.d("IMMEDIATE_NOTIFICATION", "Notificação enviada com sucesso!");
            Toast.makeText(this, "Notificação enviada! Verifique a barra de status", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.util.Log.e("IMMEDIATE_NOTIFICATION", "ERRO: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "ERRO: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        android.util.Log.d("IMMEDIATE_NOTIFICATION", "========================================");
    }

    private void cancelNotification(String medicationId) {
        Intent intent = new Intent(this, MedicationAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                medicationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void createNotificationChannel() {
        android.util.Log.d("NOTIFICATION_CHANNEL", "Criando canal de notificação...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Lembretes de Medicamentos";
            String description = "Notificações para lembrar de tomar medicamentos";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel("medication_channel", name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            channel.enableLights(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                android.util.Log.d("NOTIFICATION_CHANNEL", "Canal criado com sucesso!");
            } else {
                android.util.Log.e("NOTIFICATION_CHANNEL", "NotificationManager null!");
            }
        }
    }

    private void requestNotificationPermission() {
        android.util.Log.d("PERMISSIONS", "Verificando permissões...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                android.util.Log.d("PERMISSIONS", "Solicitando permissão POST_NOTIFICATIONS");
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            } else {
                android.util.Log.d("PERMISSIONS", "Permissão POST_NOTIFICATIONS já concedida");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                android.util.Log.d("PERMISSIONS", "Solicitando permissão de alarmes exatos");
                Toast.makeText(this, "Por favor, permita alarmes exatos nas configurações", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            } else {
                android.util.Log.d("PERMISSIONS", "Permissão de alarmes exatos já concedida");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("PERMISSIONS", "Permissão concedida pelo usuário");
                Toast.makeText(this, "Permissão de notificação concedida!", Toast.LENGTH_SHORT).show();
            } else {
                android.util.Log.e("PERMISSIONS", "Permissão negada pelo usuário");
                Toast.makeText(this, "Permissão negada! As notificações não funcionarão.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_language_en) {
            setLocale("en");
            return true;
        } else if (id == R.id.menu_language_pt) {
            setLocale("pt");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);

        Context context = createConfigurationContext(config);
        getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());

        SharedPreferences.Editor editor = getSharedPreferences("Settings", MODE_PRIVATE).edit();
        editor.putString("Language", langCode);
        editor.apply();

        recreate();
    }

    private void loadLocale() {
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String language = prefs.getString("Language", "");
        if (!language.isEmpty()) {
            Locale locale = new Locale(language);
            Locale.setDefault(locale);

            Configuration config = new Configuration(getResources().getConfiguration());
            config.setLocale(locale);

            Context context = createConfigurationContext(config);
            getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static class Medication {
        private String id;
        private String name;
        private String description;
        private String time;
        private boolean taken;

        public Medication() {}

        public Medication(String id, String name, String description, String time, boolean taken) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.time = time;
            this.taken = taken;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public boolean isTaken() { return taken; }
        public void setTaken(boolean taken) { this.taken = taken; }
    }

    private class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {
        private List<Medication> medications;

        public MedicationAdapter(List<Medication> medications) {
            this.medications = medications;
        }

        @NonNull
        @Override
        public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medication, parent, false);
            return new MedicationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
            Medication medication = medications.get(position);
            holder.bind(medication);
        }

        @Override
        public int getItemCount() {
            return medications.size();
        }

        class MedicationViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName, tvDescription, tvTime;
            private ImageButton btnCheck, btnEdit, btnDelete;
            private CardView cardView;

            public MedicationViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvMedicationName);
                tvDescription = itemView.findViewById(R.id.tvMedicationDescription);
                tvTime = itemView.findViewById(R.id.tvMedicationTime);
                btnCheck = itemView.findViewById(R.id.btnCheck);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                cardView = itemView.findViewById(R.id.cardMedication);
            }

            public void bind(Medication medication) {
                tvName.setText(medication.getName());
                tvDescription.setText(medication.getDescription());
                tvTime.setText(medication.getTime());

                if (medication.isTaken()) {
                    tvName.setPaintFlags(tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    cardView.setCardBackgroundColor(0xFFE8F5E9);
                    btnCheck.setImageResource(android.R.drawable.checkbox_on_background);
                } else {
                    tvName.setPaintFlags(tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    cardView.setCardBackgroundColor(0xFFFFFFFF);
                    btnCheck.setImageResource(android.R.drawable.checkbox_off_background);
                }

                btnCheck.setOnClickListener(v -> toggleTaken(medication));
                btnEdit.setOnClickListener(v -> showAddEditDialog(medication));
                btnDelete.setOnClickListener(v -> deleteMedication(medication.getId()));

                itemView.setOnClickListener(v -> showAddEditDialog(medication));
            }
        }
    }
}