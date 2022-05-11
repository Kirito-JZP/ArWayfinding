package com.main.arwayfinding.ui.map;

import static com.main.arwayfinding.utility.PlaceUtils.findLocationGeoMsg;
import static com.main.arwayfinding.utility.PlaceUtils.queryDetail;
import static com.main.arwayfinding.utility.StaticStringUtils.*;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.maps.model.TravelMode;
import com.main.arwayfinding.ArActivity;
import com.main.arwayfinding.R;
import com.main.arwayfinding.databinding.FragmentMapBinding;
import com.main.arwayfinding.dto.LocationDto;
import com.main.arwayfinding.dto.RouteDto;
import com.main.arwayfinding.logic.TrackerLogic;
import com.main.arwayfinding.utility.LatLngUtils;
import com.main.arwayfinding.utility.NavigationUtils;
import com.main.arwayfinding.utility.NoticeUtils;
import com.main.arwayfinding.utility.PlaceUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

import javadz.beanutils.BeanUtils;

/**
 * Define the fragment used for displaying map and dynamic way-finding
 *
 * @author JIA
 * @author Last Modified By JIA
 * @version Revision: 0
 * Date: 2022/4/30 13:50
 */

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentMapBinding binding;
    private Handler UIHandler = new Handler();

    // Map
    private GoogleMap map;
    private Marker marker;
    private FrameLayout bottomSheet;

    // Setting
    private int mapAnimDuration = 500;
    private TravelMode mode = TravelMode.WALKING;

    // EditText/TextView
    private EditText deptTxt;
    private EditText destTxt;
    private TextView btnLocNmTxt;
    private TextView btnLocDtlTxt;

    // ImageView (button)
    private ImageView addBtn;
    private ImageView exchangeBtn;
    private ImageView arBtn;
    private ImageView navigateBtn;
    private ImageView locateBtn;
    private ImageView accidentBtn;
    private ImageView deptTxtClearBtn;
    private ImageView destTxtClearBtn;
    private ImageView locationImg;
    private RelativeLayout setDeptBtn;
    private RelativeLayout setDestBtn;
    private RelativeLayout addWaypointBtn;
    private RelativeLayout publicBtn;
    private RelativeLayout walkBtn;
    private RelativeLayout cycBtn;

    // Dto
    private LocationDto currentLocDto;
    private LocationDto startLocDto;
    private LocationDto targetLocDto;
    private RouteDto currentRouteDto;
    private List<RouteDto> possibleRoutes;

    // Logic
    private TrackerLogic trackerLogic;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(getLayoutInflater());
        View root = binding.getRoot();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        return root;
    }

    /**
     * Method for setting actions after creating the view
     *
     * @param view
     * @param savedInstanceState
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindMapComponent(view);

        arBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), ArActivity.class);
                startActivity(intent);
            }
        });
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        trackerLogic = TrackerLogic.createInstance(getActivity());
        // ask for permissions
        trackerLogic.askForLocationPermissions(new TrackerLogic.LocationPermissionRequestCompleteCallback() {
            @Override
            public void onLocationPermissionRequestComplete(boolean isSuccessful) {
                if (isSuccessful) {
                    // Set map
                    map = googleMap;
                    map.setLocationSource(trackerLogic);
                    map.setMyLocationEnabled(true);
                    map.getUiSettings().setCompassEnabled(false);
                    map.getUiSettings().setMyLocationButtonEnabled(false);
                    PlaceUtils.SetMap(map);
                    map.setOnPoiClickListener(new GoogleMap.OnPoiClickListener() {
                        @Override
                        public void onPoiClick(@NonNull PointOfInterest pointOfInterest) {
                            LocationDto location = queryDetail(pointOfInterest.placeId);
                            if (location != null) {
                                showPlaceDetail(location);
                            }
                        }
                    });

                    // Create logic
//                        navigationLogic = new NavigationLogic(map, getContext());
//                        emergencyEventLogic = new EmergencyEventLogic();
                    // Create tracker object
                    trackerLogic = TrackerLogic.createInstance(getActivity());
                    // trackerLogic
                    trackerLogic.requestLastLocation(location -> resetCurrentPosition(location));
                    map.setLocationSource(trackerLogic);    // replace the default location
                    // source with

                    // Add map click listener
                    map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                        @Override
                        public void onMapClick(@NonNull LatLng latLng) {
                            LocationDto locationDto = findLocationGeoMsg(latLng);
                            // Only when the location exists in the map, change the maker.
                            showPlaceDetail(locationDto);
                        }
                    });

                    // Add map camera listeners
                    map.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                        @Override
                        public void onCameraMoveStarted(int i) {
                            //navigationLogic.setDraggingMap(true);
                        }
                    });

                    map.setOnCameraMoveCanceledListener(new GoogleMap.OnCameraMoveCanceledListener() {
                        @Override
                        public void onCameraMoveCanceled() {
                            //navigationLogic.setDraggingMap(false);
                        }
                    });

                    // Add map marker click listener
                    map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                        @Override
                        public boolean onMarkerClick(@NonNull Marker marker) {
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(),
                                    15));
                            return true;
                        }
                    });

                    // Get current location after clicking the position button
                    locateBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            trackerLogic.requestLastLocation(MapFragment.this::resetCurrentPosition);

                            // find routes if is navigating
//                                if (navigationLogic.isNavigating()) {
//                                    deptPlacesListView.setVisibility(View.INVISIBLE);
//                                    List<com.google.maps.model.LatLng> waypoints =
//                                            NavigationUtils.getLatLngFromWaypoints
//                                            (currentRouteDto);
//                                    if (startLocDto != null && targetLocDto != null) {
//                                        parseRouteData(NavigationUtils.findRoute(startLocDto,
//                                        targetLocDto, mode, waypoints));
//                                    }
//                                }
                        }
                    });
                } else {
                    trackerLogic.askForLocationPermissions(this);
                }
            }
        });
        //unpack data
        //if accepted data from broadcast, set destination and do corresponding process
        Bundle arguments = this.getArguments();
        if (arguments != null) {
            String keyword = arguments.getString("name");
            destTxt.setText(keyword);

//                targetLocDto = PlaceUtils.autocompletePlaces(keyword,
//                        startLocDto != null ? LatLngConverterUtils.getLatLngFromDto(startLocDto)
//                                : LatLngConverterUtils.getLatLngFromDto(currentLocDto)).get(0);
//                LatLng latlng = queryLatLng(targetLocDto.getGmPlaceID());
//                targetLocDto.setLatitude(latlng.latitude);
//                targetLocDto.setLongitude(latlng.longitude);
//                List<com.google.maps.model.LatLng> waypoints =
//                        NavigationUtils.getLatLngFromWaypoints(currentRouteDto);
//                if (startLocDto != null && targetLocDto != null) {
//                    parseRouteData(NavigationUtils.findRoute(startLocDto, targetLocDto, mode,
//                    waypoints));
//                }
        }
    }


    /**
     * Method for binding all the components on Map fragment.
     *
     * @param view
     */
    private void bindMapComponent(View view) {
        // ImageView
        addBtn = view.findViewById(R.id.add_btn);
        exchangeBtn = view.findViewById(R.id.exchange_btn);
        publicBtn = view.findViewById(R.id.public_btn);
        walkBtn = view.findViewById(R.id.walk_btn);
        cycBtn = view.findViewById(R.id.cyc_btn);
        navigateBtn = view.findViewById(R.id.navigate);
        locateBtn = view.findViewById(R.id.position);
        deptTxtClearBtn = view.findViewById(R.id.clear_dept_btn);
        destTxtClearBtn = view.findViewById(R.id.clear_dest_btn);
        setDeptBtn = view.findViewById(R.id.set_dept_btn);
        setDestBtn = view.findViewById(R.id.set_dest_btn);
        addWaypointBtn = view.findViewById(R.id.add_waypoint_btn);
        locationImg = view.findViewById(R.id.location_img);
        arBtn = view.findViewById(R.id.arBtn);
        //bottom sheet
        bottomSheet = view.findViewById(R.id.bottomsheet);
        // TextView
        deptTxt = view.findViewById(R.id.input_start);
        destTxt = view.findViewById(R.id.input_search);
        btnLocNmTxt = view.findViewById(R.id.loc_name_txt);
        btnLocDtlTxt = view.findViewById(R.id.loc_detail_txt);
    }

    /**
     * Method for getting and setting current position
     *
     * @param location
     */
    private void resetCurrentPosition(Location location) {
        // Add a marker in current location and move the camera
        currentLocDto = new LocationDto();
        currentLocDto.setName(getString(R.string.your_location));
        currentLocDto.setDate(new Date());
        if (location != null) {
            // reset Dto
            currentLocDto.setLatitude(location.getLatitude());
            currentLocDto.setLongitude(location.getLongitude());
            // reset view text
            deptTxt.setText(currentLocDto.getName());
        }
        try {
            startLocDto = new LocationDto();
            BeanUtils.copyProperties(startLocDto, currentLocDto);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error occurs while doing dto copy");
        }
        // move map camera
        // https://developers.google.com/maps/documentation/android-sdk/views#zoom
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLngUtils
                .getLatLngFromDto(currentLocDto), 16.0F), mapAnimDuration, null);
    }

    private void showPlaceDetail(LocationDto location) {
        if (StringUtils.isNotEmpty(location.getName())) {
            // clear the map
            map.clear();

            // remove the previous marker if it exists
            if (marker != null) {
                marker.setVisible(false);
                marker.remove();
            }
            marker = map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(),
                    location.getLongitude())));
            BottomSheetBehavior<FrameLayout> sheetBehavior = BottomSheetBehavior.from(bottomSheet);
            // collapse the bottom sheet
            if (sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            // obtain preview image if there is one
            if (StringUtils.isNotEmpty(location.getGmImgUrl())) {
                new Thread(() -> {
                    try {
                        // resolving the string into url
                        URL url = new URL(location.getGmImgUrl());
                        // Open the input stream
                        InputStream inputStream = url.openStream();
                        // Convert the online source to bitmap picture
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        UIHandler.post(() -> {
                            locationImg.setImageBitmap(bitmap);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                locationImg.setImageResource(R.drawable.ic_loc_img);
            }
            btnLocNmTxt.setText(location.getName());
            btnLocDtlTxt.setText(location.getAddress());
            // add listeners to buttons
            setDeptBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startLocDto = location;
                    deptTxt.setText(startLocDto.getName());
                    List<com.google.maps.model.LatLng> waypoints =
                            NavigationUtils.getLatLngFromWaypoints(currentRouteDto);
                    if (startLocDto != null && targetLocDto != null) {
                        parseRouteData(NavigationUtils.findRoute(startLocDto, targetLocDto, mode,
                                waypoints));

                    }
                }
            });
            setDestBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    targetLocDto = location;
                    destTxt.setText(targetLocDto.getName());
                    List<com.google.maps.model.LatLng> waypoints =
                            NavigationUtils.getLatLngFromWaypoints(currentRouteDto);
                    if (startLocDto != null && targetLocDto != null) {
                        parseRouteData(NavigationUtils.findRoute(startLocDto, targetLocDto, mode,
                                waypoints));
                    }
                }
            });
            // deactivate addWaypointBtn at first and activate it once a route is selected
//            addWaypointBtn.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    navigationLogic.addWayPoint(location);
//                    // collapse the bottom sheet
//                    BottomSheetBehavior<FrameLayout> sheetBehavior =
//                            BottomSheetBehavior.from(bottomSheet);
//                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
//                    // remove marker
//                    marker.remove();
//                }
//            });
            addWaypointBtn.setActivated(false);
            // re-open the bottom sheet to display a new place and set a delay of 0.1s after folding
            // the bottom sheet
            // the operation is done to avoid some cases where the bottom sheet fails to show up
            // if conducted
            // in the main thread
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                UIHandler.post(() -> {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                });
            }).start();
        }
    }

    private void parseRouteData(Pair<List<RouteDto>, LatLngBounds> data) {
        BottomSheetBehavior<FrameLayout> sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        // collapse the bottom sheet if it's currently expanded
        if (sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        if (data != null) {
            possibleRoutes = data.first;
            LatLngBounds bounds = data.second;
            // use the quickest route by default
            long minTime = Long.MAX_VALUE;
            RouteDto bestRoute = possibleRoutes.get(0);
            for (RouteDto r : possibleRoutes) {
                long totalTime = 0;
                for (RouteDto.RouteStep step : r.getSteps()) {
                    totalTime += step.getEstimatedTime();
                }
                if (totalTime < minTime) {
                    minTime = totalTime;
                    bestRoute = r;
                }
            }
            currentRouteDto = bestRoute;
            // activate addWaypointBtn
            addWaypointBtn.setActivated(true);
            // update map UI
            map.clear();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), mapAnimDuration,
                    null);
            NavigationUtils.updatePolylinesUI(currentRouteDto, map);
        } else {
            // no routes found
            NoticeUtils.createToast(getContext(), NO_AVAILABLE_ROUTE);
            map.clear();
        }
    }

}