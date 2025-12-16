package com.example.myrs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myrs.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapboxMap: MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // REALTIME DATABASE REFERENCE
    private val db = Firebase.database.reference

    private var hospitalList: List<Hospital> = listOf()
    private var currentUserLocation: Location? = null
    private var filterIcu: Boolean = false
    private var filterRadius5km: Boolean = false

    private val annotationHospitalMap: MutableMap<String, Hospital> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupUi()
        requestLocationPermission()

        // Admin Access (Hidden on Title Click)
        binding.tvTitle.setOnClickListener {
            // Secret Admin Access
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        // --- NEW: LOGIC TOMBOL MY BOOKINGS ---
        binding.btnMyBookings.setOnClickListener {
            val userId = getConsistentUserId()

            // Buka halaman status dengan ID yang konsisten
            val intent = Intent(this, BookingStatusActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }

    // --- FUNGSI PENTING: MENDAPATKAN ID KONSISTEN ---
    private fun getConsistentUserId(): String {
        val sharedPrefs = getSharedPreferences("MyRS_Prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("DEVICE_USER_ID", null)

        if (userId == null) {
            // Jika belum ada ID, buat ID baru dan simpan selamanya
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("DEVICE_USER_ID", userId).apply()
        }
        return userId!!
    }

    private fun setupMap() {
        mapboxMap = binding.mapView.mapboxMap
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) {
            val annotationApi: AnnotationPlugin = binding.mapView.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            initLocationComponent()

            pointAnnotationManager.addClickListener { annotation ->
                val hospital = annotationHospitalMap[annotation.id]
                if (hospital != null) {
                    HospitalDetailActivity.start(this, hospital)
                }
                true
            }

            mapboxMap.addOnMapClickListener { point ->
                val manualLocation = Location("manual").apply {
                    latitude = point.latitude()
                    longitude = point.longitude()
                }
                currentUserLocation = manualLocation
                binding.tvCurrentLocationText.text = "Lokasi Dipilih (Manual)"
                if (filterRadius5km) applyFiltersAndUpdate()
                true
            }

            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(110.3695, -7.7956))
                    .zoom(12.0)
                    .build()
            )

            // FETCH DATA FROM RTDB
            fetchHospitalsFromRTDB()
        }
    }

    private fun setupUi() {
        binding.tvTitle.text = "RS Darurat"
        binding.tvSubtitle.text = "Klik judul untuk Admin Panel."

        binding.btnPickLocation.setOnClickListener {
            Toast.makeText(this, "Mendeteksi lokasi GPS...", Toast.LENGTH_SHORT).show()
            getDeviceLocation()
        }

        binding.btnPickDestination.setOnClickListener {
            showHospitalSearchDialog()
        }

        binding.btnFindNearest.setOnClickListener {
            moveToNearestHospital()
        }

        binding.btnFilterIcu.setOnClickListener {
            filterIcu = !filterIcu
            binding.btnFilterIcu.isChecked = filterIcu
            applyFiltersAndUpdate()
        }

        binding.btnFilterRadius.setOnClickListener {
            filterRadius5km = !filterRadius5km
            binding.btnFilterRadius.isChecked = filterRadius5km
            if (filterRadius5km && currentUserLocation == null) {
                getDeviceLocation()
            }
            applyFiltersAndUpdate()
        }
    }

    private fun fetchHospitalsFromRTDB() {
        db.child("hospitals").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Hospital>()
                for (childSnapshot in snapshot.children) {
                    val h = childSnapshot.getValue(Hospital::class.java)
                    if (h != null) {
                        h.id = childSnapshot.key ?: ""
                        list.add(h)
                    }
                }
                hospitalList = list
                applyFiltersAndUpdate()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RTDB", "Failed to read value.", error.toException())
                Toast.makeText(this@MainActivity, "Gagal memuat data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun applyFiltersAndUpdate() {
        var filtered = hospitalList
        if (filterIcu) {
            filtered = filtered.filter { it.hasIcu }
        }
        if (filterRadius5km) {
            val loc = currentUserLocation
            if (loc != null) {
                filtered = filtered.filter { hospital ->
                    val distanceMeters = calculateDistance(
                        loc.latitude, loc.longitude,
                        hospital.latitude, hospital.longitude
                    )
                    distanceMeters <= 5000
                }
            }
        }
        updateMarkers(filtered)
    }

    private fun updateMarkers(hospitals: List<Hospital>) {
        if (!::pointAnnotationManager.isInitialized) return
        pointAnnotationManager.deleteAll()
        annotationHospitalMap.clear()

        hospitals.forEach { hospital ->
            val iconRes = if (hospital.hasIcu) android.R.drawable.star_big_on else android.R.drawable.star_big_off
            val point = Point.fromLngLat(hospital.longitude, hospital.latitude)

            val marker = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(getBitmapFromVectorDrawable(iconRes))
                .withTextField(hospital.name)

            val created = pointAnnotationManager.create(marker)
            annotationHospitalMap[created.id] = hospital
        }
    }

    private fun showHospitalSearchDialog() {
        val context = this
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)
        val searchInput = EditText(context)
        searchInput.hint = "Ketik nama rumah sakit..."
        layout.addView(searchInput)
        val listView = ListView(context)
        layout.addView(listView)

        val hospitalNames = hospitalList.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, hospitalNames.toMutableList())
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(context)
            .setTitle("Cari Rumah Sakit")
            .setView(layout)
            .setNegativeButton("Batal", null)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter.filter(s) }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position)
            val selectedHospital = hospitalList.find { it.name == selectedName }
            if (selectedHospital != null) {
                binding.tvDestinationText.text = selectedHospital.name
                val point = Point.fromLngLat(selectedHospital.longitude, selectedHospital.latitude)
                mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(15.0).build())
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun moveToNearestHospital() {
        val loc = currentUserLocation
        if (loc == null) { getDeviceLocation(); return }
        val nearest = hospitalList.minByOrNull { hospital ->
            calculateDistance(loc.latitude, loc.longitude, hospital.latitude, hospital.longitude)
        }
        if (nearest != null) {
            val point = Point.fromLngLat(nearest.longitude, nearest.latitude)
            mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(14.0).build())
            Toast.makeText(this, "RS terdekat: ${nearest.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = binding.mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.pulsingEnabled = true
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

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

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
                if (location != null) {
                    currentUserLocation = location
                } else {
                    Toast.makeText(this, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); binding.mapView.onDestroy() }
}
