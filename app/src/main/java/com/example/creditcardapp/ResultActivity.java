package com.example.creditcardapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.creditcardapp.R;

public class ResultActivity extends AppCompatActivity {

    private TextView tvBank, tvCard, tvCycle, tvDue, tvTotal;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        View mainView = findViewById(R.id.main); // Make sure ID exists in XML
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
            tvBank = findViewById(R.id.tvBank);
            tvCard = findViewById(R.id.tvCard);
            tvCycle = findViewById(R.id.tvCycle);
            tvDue = findViewById(R.id.tvDue);
            tvTotal = findViewById(R.id.tvTotal);

            tvBank.setText("Bank: " + getIntent().getStringExtra("bank"));
            tvCard.setText("Card Last 4: " + getIntent().getStringExtra("card"));
            tvCycle.setText("Billing Cycle: " + getIntent().getStringExtra("cycle"));
            tvDue.setText("Payment Due Date: " + getIntent().getStringExtra("due"));
            tvTotal.setText("Total Amount Due: " + getIntent().getStringExtra("total"));
        }
    }

}