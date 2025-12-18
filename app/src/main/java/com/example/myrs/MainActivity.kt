package com.example.myrs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import androidx.core.graphics.red
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

    private val db = Firebase.database.reference
    private var hospitalList: List<Hospital> = listOf()
    private var currentUserLocation: Location? = null
    private var filterIcu: Boolean = false
    private var filterRadius5km: Boolean = false

    private val annotationHospitalMap: MutableMap<String, Hospital> = mutableMapOf()

    private var isShowingNearest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupUi()
        requestLocationPermission()

        binding.tvTitle.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        binding.btnMyBookings.setOnClickListener {
            val userId = getConsistentUserId()
            val intent = Intent(this, BookingStatusActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }

    private fun getConsistentUserId(): String {
        val sharedPrefs = getSharedPreferences("MyRS_Prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("DEVICE_USER_ID", null)

        if (userId == null) {
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
                    .zoom(11.5)
                    .build()
            )
            fetchHospitalsFromRTDB()
        }
    }

    private fun setupUi() {
        binding.tvTitle.text = "Temukan RS"
        binding.tvSubtitle.text = "Bantuan medis darurat di sekitarmu"

        binding.btnPickLocation.setOnClickListener {
            Toast.makeText(this, "Mendeteksi lokasi GPS...", Toast.LENGTH_SHORT).show()
            getDeviceLocation()
        }

        binding.btnPickDestination.setOnClickListener {
            showHospitalSearchDialog()
        }

        binding.btnFindNearest.setOnClickListener {
            if (isShowingNearest) {
                resetAllFilters()
            } else {
                moveToNearestHospital()
            }
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

        if (isShowingNearest) {
            updateButtonState(false)
        }
    }

    // --- PEMBARUAN LOGIKA UTAMA ADA DI SINI ---
    private fun updateMarkers(hospitals: List<Hospital>) {
        if (!::pointAnnotationManager.isInitialized) return
        pointAnnotationManager.deleteAll()
        annotationHospitalMap.clear()

        hospitals.forEach { hospital ->
            // Tentukan ikon dan warna berdasarkan ketersediaan ICU
            val iconRes: Int
            val iconColor: Int
            if (hospital.hasIcu) {
                iconRes = R.drawable.ic_marker_icu
                iconColor = ContextCompat.getColor(this, R.color.red) // Warna Merah
            } else {
                iconRes = R.drawable.ic_marker_no_icu
                iconColor = ContextCompat.getColor(this, R.color.grey) // Warna Abu-abu
            }

            // Buat bitmap dari drawable dengan warna yang sudah ditentukan
            val markerBitmap = getBitmapFromVectorDrawable(iconRes, iconColor)

            val point = Point.fromLngLat(hospital.longitude, hospital.latitude)

            val markerOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(markerBitmap) // Gunakan bitmap yang sudah diwarnai
                .withTextField(hospital.name)
                .withTextOffset(listOf(0.0, 2.0)) // Sesuaikan posisi teks di bawah marker
                .withTextColor("#000000") // Warna teks nama RS
                .withTextSize(10.0)
                .withTextHaloColor("#FFFFFF")
                .withTextHaloWidth(1.5)

            val createdAnnotation = pointAnnotationManager.create(markerOptions)
            annotationHospitalMap[createdAnnotation.id] = hospital
        }
    }

    private fun showHospitalSearchDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val searchInput = EditText(context).apply { hint = "Ketik nama rumah sakit..." }
        val listView = ListView(context)
        layout.addView(searchInput)
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
        if (loc == null) {
            Toast.makeText(this, "Lokasi Anda belum terdeteksi.", Toast.LENGTH_SHORT).show()
            getDeviceLocation()
            return
        }
        val nearest = hospitalList.minByOrNull { hospital ->
            calculateDistance(loc.latitude, loc.longitude, hospital.latitude, hospital.longitude)
        }
        if (nearest != null) {
            val point = Point.fromLngLat(nearest.longitude, nearest.latitude)
            mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(14.5).build())
            Toast.makeText(this, "RS terdekat: ${nearest.name}", Toast.LENGTH_SHORT).show()
            updateMarkers(listOf(nearest))
            updateButtonState(true)
        }
    }

    private fun resetAllFilters() {
        filterIcu = false
        filterRadius5km = false
        binding.btnFilterIcu.isChecked = false
        binding.btnFilterRadius.isChecked = false

        applyFiltersAndUpdate()

        mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(110.3695, -7.7956))
                .zoom(11.5)
                .build()
        )
        updateButtonState(false)
    }

    private fun updateButtonState(isShowingNearest: Boolean) {
        this.isShowingNearest = isShowingNearest
        if (isShowingNearest) {
            binding.btnFindNearest.text = "Tampilkan Semua RS"
            binding.btnFindNearest.setIconResource(R.drawable.ic_show_all)
        } else {
            binding.btnFindNearest.text = "Tampilkan RS Terdekat"
            binding.btnFindNearest.setIconResource(R.drawable.ic_mylocation)
        }
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = binding.mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.pulsingEnabled = true
        }
    }

    // --- FUNGSI HELPER INI DIPERBARUI UNTUK MENERIMA WARNA ---
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getBitmapFromVectorDrawable(drawableId: Int, tintColor: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        // Terapkan filter warna
        drawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
                    binding.tvCurrentLocationText.text = "Lokasi kamu (GPS)"
                    mapboxMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(location.longitude, location.latitude)).zoom(12.5).build())
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
