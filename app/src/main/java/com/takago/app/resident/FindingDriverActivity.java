package com.takago.app.resident;

import com.takago.app.data.model.PickupRow;
import com.takago.app.R;
import com.takago.app.*;
import com.takago.app.admin.*;
import com.takago.app.app.*;
import com.takago.app.auth.*;
import com.takago.app.common.*;
import com.takago.app.driver.*;
import com.takago.app.location.*;
import com.takago.app.notifications.*;
import com.takago.app.operator.*;
import com.takago.app.resident.*;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ServerSyncManager;

/**
 * Shown right after the resident confirms a pickup, instead of an immediate "Pickup confirmed"
 * screen. Reflects the real dispatch status (auto-assignment already ran synchronously in
 * DatabaseHelper.createPickupRequest) and only moves on once a driver has actually accepted -
 * not merely been proposed the job.
 */
public class FindingDriverActivity extends AppCompatActivity {

    private static final long POLL_INTERVAL_MS = 2500;

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private int pickupId;
    private TextView tvFindingTitle, tvFindingSubtitle;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private AnimatorSet pulseAnimator;
    private boolean resolved;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            checkStatus();
            if (!resolved) {
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finding_driver);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        pickupId = getIntent().getIntExtra("pickupId", -1);

        tvFindingTitle = findViewById(R.id.tvFindingTitle);
        tvFindingSubtitle = findViewById(R.id.tvFindingSubtitle);

        findViewById(R.id.tvCancelSearch).setOnClickListener(v -> finish());

        startPulseAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkStatus();
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }

    private void checkStatus() {
        if (resolved || pickupId == -1) {
            return;
        }

        ServerSyncManager.syncAll(this);
        PickupRow pickup = dbHelper.getActivePickupForResident(session.getUserId());
        if (pickup == null) {
            return;
        }

        switch (pickup.status) {
            case "On the way":
                resolved = true;
                goToTracking();
                break;
            case "Cancelled":
                resolved = true;
                showCancelled();
                break;
            case "Assigned":
                resolved = true;
                startActivity(new Intent(this, ResidentHomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                break;
            default: // Pending - still searching (or was rejected and is being reassigned)
                tvFindingTitle.setText("Finding the nearest driver...");
                tvFindingSubtitle.setText("Matching you with a driver in your ward. This usually takes a moment.");
                break;
        }
    }

    private void goToTracking() {
        Intent intent = new Intent(this, ResidentTrackActivity.class);
        startActivity(intent);
        finish();
    }

    private void showCancelled() {
        new AlertDialog.Builder(this)
                .setTitle("Pickup cancelled")
                .setMessage("This pickup request was cancelled.")
                .setPositiveButton("OK", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void startPulseAnimation() {
        pulseAnimator = new AnimatorSet();
        Animator outer = createRingPulse(findViewById(R.id.pulseRingOuter), 0);
        Animator inner = createRingPulse(findViewById(R.id.pulseRingInner), 900);
        pulseAnimator.playTogether(outer, inner);
        pulseAnimator.start();
    }

    private Animator createRingPulse(View ring, long startDelay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 0.5f, 1.3f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 0.5f, 1.3f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.7f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(1800);
        set.setStartDelay(startDelay);
        set.setInterpolator(new LinearInterpolator());

        // Loop indefinitely by restarting the set each time it ends.
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isFinishing() && !isDestroyed()) {
                    set.setStartDelay(0);
                    set.start();
                }
            }
        });
        return set;
    }
}
