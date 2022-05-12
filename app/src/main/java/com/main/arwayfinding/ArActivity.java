package com.main.arwayfinding;

import static com.main.arwayfinding.utility.PlaceUtils.queryLatLng;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
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

import com.google.android.gms.maps.model.LatLng;
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
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.maps.android.SphericalUtil;
import com.main.arwayfinding.databinding.ActivityArBinding;
import com.main.arwayfinding.dto.LocationDto;
import com.main.arwayfinding.logic.GPSTrackerLogic;
import com.main.arwayfinding.logic.TrackerLogic;
import com.main.arwayfinding.utility.ArLocationUtils;
import com.main.arwayfinding.utility.LatLngUtils;
import com.main.arwayfinding.utility.NavigationUtils;
import com.main.arwayfinding.utility.PlaceUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;
import uk.co.appoly.arcorelocation.sensor.DeviceLocationChanged;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;

/**
 * Define the activity for AR navigation
 *
 * @author JIA
 * @author Last Modified By Gang
 * @version Revision: 0
 * Date: 2022/5/11 21:28
 */
public class ArActivity extends AppCompatActivity implements SensorEventListener {
    private boolean installRequested;
    private boolean hasFinishedLoading = false;
    private Snackbar loadingMessageSnackbar = null;
    private ArSceneView arSceneView;
    private ModelRenderable andyRenderable;
    private ModelRenderable arrowRenderable;
    private ModelRenderable modelRenderable;
    private ViewRenderable layoutRenderable;
    private LocationScene locationScene;
    private ActivityArBinding binding;
    private ArrayList<LocationDto> list;
    //newlist for route dots
    private ArrayList<LatLng> waypoints;
    //address from map search
    private String destinationStr;
    private LocationDto destination;
    private ImageView arReturnBtn;
    private static boolean placed = false;
    private Location lastPosition;
    private boolean updateRequired;
    //sensor
    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private static float[] rotateDegree = new float[3];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        arSceneView = findViewById(R.id.ar_scene_view);
        arReturnBtn = findViewById(R.id.arReturnBtn);
        //transport values
        Intent intentNavi = this.getIntent();
        destinationStr = intentNavi.getStringExtra("targetLoc");
        waypoints = intentNavi.getParcelableArrayListExtra("waypoints");

        CompletableFuture<ViewRenderable> layout = ViewRenderable.builder().setView(this, R.layout.activity_ar_label).build();

        CompletableFuture<ModelRenderable> andyModel = ModelRenderable.builder().setSource(this, R.raw.andy).build();
        CompletableFuture<ModelRenderable> arrowModel = ModelRenderable.builder().setSource(this, R.raw.arrow).build();

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
                                locationScene.setLocationChangedEvent(new DeviceLocationChanged() {
                                    @Override
                                    public void onChange(Location arLocation) {
//                                        GPSTrackerLogic trackerLogic = new GPSTrackerLogic(thisActivity);
//                                        Location location = trackerLogic.getLocation();
//                                        renderAR(location);
                                        TrackerLogic trackerLogic = TrackerLogic.createInstance(thisActivity);
                                        trackerLogic.requestLastLocation(new TrackerLogic.RequestLocationCompleteCallback() {
                                            @Override
                                            public void onRequestLocationComplete(Location location) {
                                                if(destinationStr!=null){
                                                    destination = PlaceUtils.autocompletePlaces(destinationStr, new LatLng(location.getLatitude(), location.getLongitude())).get(0);
                                                    LatLng latlng = queryLatLng(destination.getGmPlaceID());
                                                    destination.setLatitude(latlng.latitude);
                                                    destination.setLongitude(latlng.longitude);
                                                }
                                                renderAR_nearBy(location);
                                            }
                                        });
                                    }
                                });
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
//                                    if (Math.round(Math.toDegrees(orientationAngles[1]) / 15) * 15 % 90 != 0) {
//                                        arrow.setLocalRotation(Quaternion.eulerAngles(new Vector3(
//                                                Math.round(Math.toDegrees(orientationAngles[1]) / 20) * 20,
//                                                Math.round(Math.toDegrees(orientationAngles[2]) / 20) * 20,
//                                                Math.round(Math.toDegrees(orientationAngles[0]) / 20) * 20)));
//                                    }
                                    arrow.setLocalRotation(Quaternion.eulerAngles(new Vector3(rotateDegree[1], 0.0F, -rotateDegree[2])));
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
        return base;
    }

    private Node createModelNode() {
        Node base = new Node();
        base.setRenderable(modelRenderable);
        Context c = this;
        return base;
    }

    private <T extends Renderable> Node createNode(T renderable) {
        Node base = new Node();
        base.setRenderable(renderable);
        return base;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //sensor
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor orientation = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        if (orientation != null) {
            sensorManager.registerListener(this, orientation,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }

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
        //sensor
        sensorManager.unregisterListener(this);

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

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType()==Sensor.TYPE_ORIENTATION){
            System.out.println("方位角：" + (float) (Math.round(sensorEvent.values[0] * 100)) / 100);
            System.out.println("倾斜角：" + (float) (Math.round(sensorEvent.values[1] * 100)) / 100);
            System.out.println("滚动角：" + (float) (Math.round(sensorEvent.values[2] * 100)) / 100);
            rotateDegree[0] = (float) (Math.round(sensorEvent.values[0] * 100)) / 100;
            rotateDegree[1] = (float) (Math.round(sensorEvent.values[1] * 100)) / 100;
            rotateDegree[2] = (float) (Math.round(sensorEvent.values[2] * 100)) / 100;
        }else{
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                        0, accelerometerReading.length);
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                        0, magnetometerReading.length);
            }
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private Vector3 getPositionVector(float azimuth, LatLng latLng) {
        GPSTrackerLogic trackerLogic = new GPSTrackerLogic(this);
        Location location = trackerLogic.getLocation();
        LatLng placeLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        double heading = SphericalUtil.computeHeading(latLng, placeLatLng);
        float r = -2f;
        float x = (float) (r * Math.sin(azimuth + heading));
        float y = 0f;
        float z = (float) (r * Math.cos(azimuth + heading));
        return new Vector3(x, y, z);
    }

    private void renderAR(Location location) {
        locationScene.deviceLocation.currentBestLocation.setLatitude(location.getLatitude());
        locationScene.deviceLocation.currentBestLocation.setLongitude(location.getLongitude());
        for (int i = 0; i < waypoints.size(); i++) {
            float[] result = new float[3];
            Location.distanceBetween(waypoints.get(i).latitude, waypoints.get(i).longitude, location.getLatitude(), location.getLongitude(), result);
            if (result[0] <= 10) {
                if (i > 0) {
                    waypoints.subList(0, i).clear();
                    lastPosition = location;
                    locationScene.clearMarkers();
                    updateRequired = true;
                }
                break;
            }
        }

        if (lastPosition == null) {
            lastPosition = location;
            updateRequired = true;
        }
        if (updateRequired) {
            // draw destination ------------
            LocationMarker viewLocationMarker = new LocationMarker(
                    destination.getLongitude(),
                    destination.getLatitude(),
                    createNode(layoutRenderable)
            );
            // Updates the layout with the markers distance
            String name = destination.getName();
            viewLocationMarker.setRenderEvent(new LocationNodeRender() {
                @Override
                public void render(LocationNode node) {
                    // height fix
                    Node nodeObject = node.getChildren().get(0);
                    Vector3 worldPosition = nodeObject.getWorldPosition();
                    worldPosition.y = 0;
                    nodeObject.setWorldPosition(worldPosition);

                    View eView = layoutRenderable.getView();
                    TextView distanceTextView = eView.findViewById(R.id.loc_distance);
                    TextView nameTextView = eView.findViewById(R.id.loc_name);
                    nameTextView.setText(name);
                    distanceTextView.setText(node.getDistance() + "M");
                }
            });
            // Adding the marker
            locationScene.mLocationMarkers.add(viewLocationMarker);
            // draw routine -----------
//            int count = 0;
            for (LatLng waypoint : waypoints) {
//                count++;
//                if(count>=6){
//                    break;
//                }
                CompletableFuture<ViewRenderable> layout = ViewRenderable.builder().setView(this, R.layout.activity_ar_label).build();
                CompletableFuture<ModelRenderable> model = ModelRenderable.builder().setSource(this, R.raw.ball).build();
                CompletableFuture.allOf(layout, model).handle((notUsed, throwable) -> {
                    // init renderable
                    if (throwable != null) {
                        ArLocationUtils.displayError(this, "Unable to load renderables", throwable);
                        return null;
                    }
                    try {
                        layoutRenderable = layout.get();
                        modelRenderable = model.get();
                        hasFinishedLoading = true;
                    } catch (InterruptedException | ExecutionException ex) {
                        ArLocationUtils.displayError(this, "Unable to load renderables", ex);
                    }
                    //-----------------------------
                    ModelRenderable copyOfArrowRenderable = modelRenderable.makeCopy();
                    LocationMarker modelLocationMarker = new LocationMarker(
                            waypoint.longitude,
                            waypoint.latitude,
                            createNode(copyOfArrowRenderable));

                    modelLocationMarker.setRenderEvent(new LocationNodeRender() {
                        @Override
                        public void render(LocationNode node) {
                            // height fix
                            Node nodeObject = node.getChildren().get(0);
                            Vector3 worldPosition = nodeObject.getWorldPosition();
                            worldPosition.y = -5;
                            nodeObject.setWorldPosition(worldPosition);
                            nodeObject.setLocalScale(new Vector3(0.8f, 0.8f, 0.8f));
                        }
                    });
                    // Adding a simple location marker of a 3D model
                    locationScene.mLocationMarkers.add(modelLocationMarker);
                    return null;
                });
            }
            updateRequired = false;
        }
        if (location.distanceTo(lastPosition) >= 15) {
            lastPosition = location;
            locationScene.clearMarkers();
            updateRequired = true;
        }
    }

    private void renderAR_nearBy(Location location) {
        locationScene.deviceLocation.currentBestLocation.setLatitude(location.getLatitude());
        locationScene.deviceLocation.currentBestLocation.setLongitude(location.getLongitude());
        if (lastPosition == null) {
            lastPosition = location;
            list = PlaceUtils.getNearby(location);
            updateRequired = true;
        }
        if (updateRequired) {
            for (LocationDto locationDto : list) {
                CompletableFuture<ViewRenderable> layout = ViewRenderable.builder().setView(this, R.layout.activity_ar_label).build();
                CompletableFuture<ModelRenderable> model = ModelRenderable.builder().setSource(this, R.raw.andy).build();
                CompletableFuture.allOf(layout, model).handle((notUsed, throwable) -> {
                    // init renderable
                    if (throwable != null) {
                        ArLocationUtils.displayError(this, "Unable to load renderables", throwable);
                        return null;
                    }
                    try {
                        layoutRenderable = layout.get();
                        modelRenderable = model.get();
                        hasFinishedLoading = true;
                    } catch (InterruptedException | ExecutionException ex) {
                        ArLocationUtils.displayError(this, "Unable to load renderables", ex);
                    }
                    //-----------------------------
                    // create location markers.
                    // layout
                    LocationMarker layoutLocationMarker = new LocationMarker(
                            locationDto.getLongitude(),
                            locationDto.getLatitude(),
                            createViewNode()
                    );
                    // Updates the layout with the markers distance
                    ViewRenderable copyOfRenderable = layoutRenderable.makeCopy();
                    String name = locationDto.getName();
                    layoutLocationMarker.setRenderEvent(new LocationNodeRender() {
                        @Override
                        public void render(LocationNode node) {
                            // height fix
                            Node viewNode = node.getChildren().get(0);
                            Vector3 worldPosition = viewNode.getWorldPosition();
                            worldPosition.y = 0;
                            viewNode.setWorldPosition(worldPosition);

                            View eView = copyOfRenderable.getView();
                            TextView distanceTextView = eView.findViewById(R.id.loc_distance);
                            TextView nameTextView = eView.findViewById(R.id.loc_name);
                            nameTextView.setText(name);
                            distanceTextView.setText(node.getDistance() + "M");
                        }
                    });
                    // Adding the marker
                    locationScene.mLocationMarkers.add(layoutLocationMarker);
                    ModelRenderable copyOfArrowRenderable = modelRenderable.makeCopy();
                    LocationMarker modelLocationMarker = new LocationMarker(
                            locationDto.getLongitude(),
                            locationDto.getLatitude(),
                            createNode(copyOfArrowRenderable));

                    modelLocationMarker.setRenderEvent(new LocationNodeRender() {
                        @Override
                        public void render(LocationNode node) {
                            // height fix
                            Node viewNode = node.getChildren().get(0);
                            Vector3 worldPosition = viewNode.getWorldPosition();
                            worldPosition.y = 1.5f;
                            viewNode.setWorldPosition(worldPosition);

                        }
                    });
                    // Adding a simple location marker of a 3D model
                    locationScene.mLocationMarkers.add(modelLocationMarker);
                    return null;
                });

            }
            updateRequired = false;
        }
        if (location.distanceTo(lastPosition) >= 15) {
            lastPosition = location;
            list = PlaceUtils.getNearby(location);
            locationScene.clearMarkers();
            updateRequired = true;
        }


    }

    private void singleComp(Location location) {
        list = PlaceUtils.getNearby(location);
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
}