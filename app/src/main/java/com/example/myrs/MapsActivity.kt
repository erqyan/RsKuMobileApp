package com.example.myrs

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

class MapsActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapboxMap: MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentUserLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMapbox()
        setupFilterButton()
        requestLocationPermission()
    }

    private fun setupMapbox() {
        mapView = findViewById(R.id.mapView)
        mapboxMap = mapView.mapboxMap

        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) {
            val annotationApi: AnnotationPlugin = mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()

            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(110.3695, -7.7956))
                    .zoom(11.0)
                    .build()
            )

            addHospitalMarkers()
        }
    }

    private fun addHospitalMarkers() {
        // Kosongkan dulu atau minta data dari parameter jika ingin dipakai
        // pointAnnotationManager.deleteAll()

        // allHospitals sudah dihapus, jadi baris di bawah ini menyebabkan error:
        // allHospitals.forEach { ... }

        Toast.makeText(this, "Peta siap. Logika data ada di MainActivity.", Toast.LENGTH_SHORT).show()
    }



    private fun setupFilterButton() {
        findViewById<FloatingActionButton>(R.id.filterFab)
            .setOnClickListener {
                Toast.makeText(this, "Filter akan aktif setelah UI siap", Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getBitmapFromVectorDrawable(drawableId: Int): android.graphics.Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // Lokasi (masih sama)
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getDeviceLocation()
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getDeviceLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                currentUserLocation = location
            }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
}
