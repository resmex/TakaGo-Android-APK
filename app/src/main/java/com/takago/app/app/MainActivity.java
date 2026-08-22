 package com.takago.app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.takago.app.db.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            TextView tvWelcome = findViewById(R.id.tvWelcomeMessage);
            TextView tvRole = findViewById(R.id.tvRoleMessage);
            tvWelcome.setText("Welcome, " + session.getName());
            tvRole.setText(session.getRole() + " dashboard is coming soon");
        }
    }
}