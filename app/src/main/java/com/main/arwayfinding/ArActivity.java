package com.main.arwayfinding;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.main.arwayfinding.databinding.ActivityArBinding;
import com.main.arwayfinding.dto.LocationDto;
import com.main.arwayfinding.logic.GPSTrackerLogic;
import com.main.arwayfinding.utility.ArLocationUtils;
import com.main.arwayfinding.utility.PlaceUtils;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;
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
    private boolean installRequested;
    private boolean hasFinishedLoading = false;
    private Snackbar loadingMessageSnackbar = null;
    private ArSceneView arSceneView;
    private ModelRenderable andyRenderable;
    private ModelRenderable arrowRenderable;
    private ViewRenderable layoutRenderable;
    private LocationScene locationScene;
    private ActivityArBinding binding;
    ArrayList<LocationDto> list;
    private ImageView arReturnBtn;
    private static boolean placed = false;
    private static float[] rotateDegree = new float[3];
    //gyroscope
    private SensorManager sensorManager;
    private Sensor gyroscopeSenser;
    private SensorEventListener gyroscopeEventListener;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private float timestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        arSceneView = findViewById(R.id.ar_scene_view);
        arReturnBtn = findViewById(R.id.arReturnBtn);
        //Gyroscope
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gyroscopeSenser = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        if (gyroscopeSenser == null){
            Toast.makeText(this, "The device has no Gyroscope !", Toast.LENGTH_SHORT).show();
            finish();
        }
        gyroscopeEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                System.out.println("方位角：" + (float) (Math.round(sensorEvent.values[0] * 100)) / 100);
                System.out.println("倾斜角：" + (float) (Math.round(sensorEvent.values[1] * 100)) / 100);
                System.out.println("滚动角：" + (float) (Math.round(sensorEvent.values[2] * 100)) / 100);
                rotateDegree[0] = (float) (Math.round(sensorEvent.values[0] * 100)) / 100;
                rotateDegree[1] = (float) (Math.round(sensorEvent.values[1] * 100)) / 100;
                rotateDegree[2] = (float) (Math.round(sensorEvent.values[2] * 100)) / 100;
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
        CompletableFuture<ViewRenderable> layout = ViewRenderable.builder().setView(this, R.layout.activity_ar_label).build();

        CompletableFuture<ModelRenderable> andyModel = andyRenderable.builder().setSource(this, R.raw.andy).build();
        CompletableFuture<ModelRenderable> arrowModel = andyRenderable.builder().setSource(this, R.raw.arrow).build();

        CompletableFuture.allOf(layout, andyModel).handle((notUsed, throwable) -> {
            // When build a Renderable, Sceneform loads its resources in the
            // background while
            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
            // before calling get().
            if (throwable != null) {
                ArLocationUtils.displayError(this, "Unable to load renderables", throwable);
                return null;
            }
            try {
                layoutRenderable = layout.get();
                andyRenderable = andyModel.get();
                arrowRenderable = arrowModel.get();
                hasFinishedLoading = true;
            } catch (InterruptedException | ExecutionException ex) {
                ArLocationUtils.displayError(this, "Unable to load renderables", ex);
            }
            return null;
        });

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (!hasFinishedLoading) {
                                return;
                            }
                            if (locationScene == null) {
                                // If our locationScene object hasn't been setup yet, this is a good time to do it
                                // We know that here, the AR components have been initiated.
                                locationScene = new LocationScene(this, this, arSceneView);
                                ArActivity thisActivity = this;
                                GPSTrackerLogic trackerLogic = new GPSTrackerLogic(thisActivity);
                                Location location = trackerLogic.getLocation(thisActivity);
                                list = PlaceUtils.getNearby(location);

                                // Now lets create our location markers.
                                // First, a layout
                                LocationMarker viewLocationMarker = new LocationMarker(
                                        list.get(0).getLongitude(),
                                        list.get(0).getLatitude(),
                                        createViewNode()
                                );

                                // Updates the layout with the markers distance
                                String name = list.get(0).getName();
                                viewLocationMarker.setRenderEvent(new LocationNodeRender() {
                                    @Override
                                    public void render(LocationNode node) {
                                        View eView = layoutRenderable.getView();
                                        TextView distanceTextView = eView.findViewById(R.id.loc_distance);
                                        TextView nameTextView = eView.findViewById(R.id.loc_name);
                                        nameTextView.setText(name);
                                        distanceTextView.setText(node.getDistance() + "M");
                                    }
                                });

                                LocationMarker modelLocationMarker = new LocationMarker(
                                        list.get(0).getLongitude(),
                                        list.get(0).getLatitude(),
                                        createModelNode());
                                modelLocationMarker.setRenderEvent(new LocationNodeRender() {
                                    @Override
                                    public void render(LocationNode node) {
                                        Objects.requireNonNull(node.getAnchor()).detach();
                                        System.out.println(list.get(0).getLongitude() + " 8====> " + list.get(0).getLatitude());
                                        System.out.println(node.getLocalPosition());
                                        node.setWorldPosition(new Vector3(0, 0, 0));
                                        System.out.println(node.getLocalPosition());
                                        System.out.println(node.getWorldPosition());
                                    }
                                });

                                // Adding the marker
                                locationScene.mLocationMarkers.add(viewLocationMarker);
                                // Adding a simple location marker of a 3D model
                                locationScene.mLocationMarkers.add(modelLocationMarker);
                            }

                            Frame frame = arSceneView.getArFrame();

                            if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                                if (!placed) {
                                    Pose pos = frame.getCamera().getPose().compose(Pose.makeTranslation(0, 0f, 0f));
                                    Anchor anchor = arSceneView.getSession().createAnchor(pos);
                                    AnchorNode anchorNode = new AnchorNode(anchor);
                                    anchorNode.setParent(arSceneView.getScene());

                                    // Create the arrow node and add it to the anchor.
                                    Node arrow = new Node();
                                    arrow.setLocalPosition((new Vector3(0.0f, 0.0f, -1.0f)));
                                    arSceneView.getScene().getCamera().addChild(arrow);
                                    //arrow.setParent(anchorNode);
                                    arrow.setRenderable(arrowRenderable);
                                    placed = true; //to place the arrow just once.
                                } else {
                                    Node arrow = arSceneView.getScene().getCamera().getChildren().get(0);
                                    //方位角,-倾斜角，-滚动角
                                    arrow.setLocalRotation(Quaternion.eulerAngles(new Vector3(rotateDegree[1], -rotateDegree[0], -rotateDegree[2])));
//                                    degree += 1;
//                                    System.out.println(degree);
//                                    if (degree > 360) {
//                                        degree = 0;
//                                    }
                                }
                            }

                            if (locationScene != null) {
                                locationScene.processFrame(frame);
                            }

                            if (loadingMessageSnackbar != null) {
                                for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                    if (plane.getTrackingState() == TrackingState.TRACKING) {
                                        hideLoadingMessage();
                                    }
                                }
                            }
                        });

        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.
        ARLocationPermissionHelper.requestPermission(this);


        arReturnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ArActivity.this, MainActivity.class);
                intent.putExtra("id", 1);
                startActivity(intent);
            }
        });
    }

    private Node createViewNode() {
        Node base = new Node();
        base.setRenderable(layoutRenderable);
        Context c = this;
        // Add  listeners etc here
        View eView = layoutRenderable.getView();
        eView.setOnTouchListener((v, event) -> {
            Toast.makeText(
                    c, "Location marker touched.", Toast.LENGTH_LONG)
                    .show();
            return false;
        });

        return base;
    }

    private Node createModelNode() {
        Node base = new Node();
        base.setRenderable(andyRenderable);
        Context c = this;
        base.setOnTapListener((v, event) -> {
            Toast.makeText(
                    c, "Andy touched.", Toast.LENGTH_LONG)
                    .show();
        });
        return base;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //gyroscope
        sensorManager.registerListener(gyroscopeEventListener,gyroscopeSenser,sensorManager.SENSOR_DELAY_FASTEST);

        if (locationScene != null) {
            locationScene.resume();
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Session session = ArLocationUtils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                ArLocationUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            ArLocationUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //gyroscope
        sensorManager.unregisterListener(gyroscopeEventListener);
        if (locationScene != null) {
            locationScene.pause();
        }

        arSceneView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        arSceneView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!ARLocationPermissionHelper.hasPermission(this)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(this);
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application",
                        Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        ArActivity.this.findViewById(android.R.id.content),
                        R.string.plane_finding,
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }
}