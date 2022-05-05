package com.main.arwayfinding;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.main.arwayfinding.databinding.ActivityArBinding;

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

    private ModelRenderable arrowRenderable;

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

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> arrowRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load arrow renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (arrowRenderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    //arrowRenderable.getMaterial().setFloat3("baseColorTint",
                    //        new Color(android.graphics.Color.rgb(255,0,0)));

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode arrow = new TransformableNode(arFragment.getTransformationSystem());
                    arrow.setParent(anchorNode);
                    arrow.setRenderable(arrowRenderable);
                    arrow.select();
                });
    }

}