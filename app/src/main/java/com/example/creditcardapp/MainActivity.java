package com.example.creditcardapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF_REQUEST = 1;
    private Spinner spinnerBank;
    private Button btnSelectPDF;
    private TextView tvStatus;
    private String selectedBank = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PDFBoxResourceLoader.init(getApplicationContext());
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        spinnerBank = findViewById(R.id.spinnerBank);
        btnSelectPDF = findViewById(R.id.btnSelectPDF);
        tvStatus = findViewById(R.id.tvStatus);


        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.bank_names,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBank.setAdapter(adapter);

        btnSelectPDF.setEnabled(false);

        spinnerBank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBank = parent.getItemAtPosition(position).toString();
                btnSelectPDF.setEnabled(!selectedBank.equals("Select Bank"));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnSelectPDF.setEnabled(false);
            }
        });

        btnSelectPDF.setOnClickListener(v -> selectPDF());
    }


    private void selectPDF() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(Intent.createChooser(intent, "Select PDF"), PICK_PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri pdfUri = data.getData();
            tvStatus.setText("PDF Selected Successfully!");
            extractDataFromPDF(pdfUri);
        }
    }

    private void extractDataFromPDF(Uri pdfUri) {
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Cannot open PDF", Toast.LENGTH_SHORT).show());
                    return;
                }

                try (PDDocument document = PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())) {

                    PDFTextStripper firstPageStripper = new PDFTextStripper();
                    firstPageStripper.setStartPage(1);
                    firstPageStripper.setEndPage(1);
                    String firstPageText = firstPageStripper.getText(document);

                    String detectedBank = detectBank(firstPageText);
                    
                    if (!selectedBank.equalsIgnoreCase(detectedBank) && !detectedBank.equals("Unknown")) {
                        runOnUiThread(() -> Toast.makeText(
                                this,
                                "Bank mismatch! Selected: " + selectedBank + ", Detected: " + detectedBank + ". Please select the correct bank.",
                                Toast.LENGTH_LONG
                        ).show());
                        return;
                    }


                    PDFTextStripper pdfStripper = new PDFTextStripper();
                    String fullText = pdfStripper.getText(document).replace("\u00A0", " ").replaceAll("\\s+", " ");

                    String cardLastDigits = extractCardLastDigits(fullText);
                    String billingCycle = extractPattern(fullText, "Billing(?: Period| Cycle)[:\\s]*([0-9/\\- ]+)");
                    String paymentDue = extractPattern(fullText, "Payment Due Date[:\\s]*([A-Za-z0-9/ ]+)");
                    String totalAmount = extractPattern(fullText, "Total(?: Amount Due| Due)[:\\s]*([$₹Rs]?\\s*[0-9,\\.]+)");


                    runOnUiThread(() -> {
                        Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                        intent.putExtra("bank", detectedBank);
                        intent.putExtra("card", cardLastDigits);
                        intent.putExtra("cycle", billingCycle);
                        intent.putExtra("due", paymentDue);
                        intent.putExtra("total", totalAmount);
                        startActivity(intent);

                        spinnerBank.setSelection(0);
                        btnSelectPDF.setEnabled(false);
                        tvStatus.setText("");
                        selectedBank = "";
                    });

                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error reading PDF", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String detectBank(String text) {
        if (text == null) return "Unknown";

        String t = text.toLowerCase();
        t = t.replaceAll("[^a-z]", ""); // remove spaces, numbers, symbols

        if (t.contains("hdfc")) return "HDFC";
        if (t.contains("sbicard") || t.contains("statebankofindia") || t.contains("sbi")) return "SBI";
        if (t.contains("icici")) return "ICICI";
        if (t.contains("axis")) return "Axis";
        if (t.contains("americanexpress") || t.contains("amex")) return "American Express";

        return "Unknown";
    }

    private String extractPattern(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();
        else return "Not Found";
    }

    private String extractCardLastDigits(String text) {
        if (text == null) return "Not Found";
        String t = text.replace("\u00A0", " ").replaceAll("\\s+", " ");

        String[] patterns = new String[]{
                "(?:\\*+|X+|#|●+)[\\s-]*([0-9]{4,5})",
                "(?:Card(?: Number)?(?:\\s*ending)?|ending(?: in|:)?)[:\\s-]*([0-9]{4,5})",
                "Card(?: Number| No\\.| No|#)?[:\\s-]*([0-9]{4,5})",
                "(?:[0-9]{4}[\\s-]){1,}[0-9]{4,5}\\b",
                "(?:last|ending|ends|card)[:\\s-]*([0-9]{4,5})",
                "\\b([0-9]{5})\\b"
        };

        for (String pat : patterns) {
            try {
                Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(t);
                if (m.find()) {
                    String g = (m.group(1) != null) ? m.group(1) : m.group(0);
                    String digitsOnly = g.replaceAll("\\D+", "");
                    if (digitsOnly.length() >= 4) {
                        return digitsOnly.substring(Math.max(0, digitsOnly.length() - 5));
                    }
                }
            } catch (Exception ignored) {}
        }
        return "Not Found";
    }
}
