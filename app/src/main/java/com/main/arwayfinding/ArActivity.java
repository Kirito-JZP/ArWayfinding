package com.main.arwayfinding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.sceneform.ux.ArFragment;
import com.main.arwayfinding.databinding.ActivityArBinding;

import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;

/**
 * Define the activity for AR navigation
 *
 * @author JIA
 * @author Last Modified By JIA
 * @version Revision: 0
 * Date: 2022/5/5 2:28
 */
public class ArActivity extends AppCompatActivity {
    private ArFragment arFragment;
    private ActivityArBinding binding;
    private ImageView arReturnBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        arReturnBtn = findViewById(R.id.arReturnBtn);
        arReturnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ArActivity.this, MainActivity.class);
                intent.putExtra("id",1);
                startActivity(intent);
            }
        });
    }

}