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
import com.example.myrs.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.UUID

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    // 1. UBAH googleMap MENJADI NULLABLE untuk mencegah crash
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val db = Firebase.database.reference
    private var hospitalList: List<Hospital> = listOf()
    private var currentUserLocation: Location? = null
    private var filterIcu: Boolean = false
    private var filterRadius5km: Boolean = false

    private val markerHospitalMap: MutableMap<Marker, Hospital> = mutableMapOf()
    private var isShowingNearest = false

    // 2. PINDAHKAN LISTENER FIREBASE KE PROPERTI KELAS
    private val hospitalValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val list = mutableListOf<Hospital>()
            snapshot.children.forEach { childSnapshot ->
                val h = childSnapshot.getValue(Hospital::class.java)
                if (h != null) {
                    h.id = childSnapshot.key ?: ""
                    list.add(h)
                }
            }
            hospitalList = list
            // Update peta hanya jika sudah siap
            applyFiltersAndUpdate()
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("RTDB", "Failed to read value.", error.toException())
            Toast.makeText(this@MainActivity, "Gagal memuat data.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUi()
        requestLocationPermission()
    }

    // 3. ATUR LISTENER PADA SIKLUS HIDUP ACTIVITY
    override fun onStart() {
        super.onStart()
        // Mulai mendengarkan data saat activity dimulai
        db.child("hospitals").addValueEventListener(hospitalValueEventListener)
    }

    override fun onStop() {
        super.onStop()
        // Hentikan listener untuk menghemat resource saat activity tidak terlihat
        db.child("hospitals").removeEventListener(hospitalValueEventListener)
    }

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        setupMap()
        // Setelah peta siap, kita bisa mencoba update marker dengan data yang mungkin sudah ada
        applyFiltersAndUpdate()
    }

    private fun getConsistentUserId(): String {
        val sharedPrefs = getSharedPreferences("MyRS_Prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("DEVICE_USER_ID", null)

        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("DEVICE_USER_ID", userId).apply()
        }
        return userId
    }

    private fun setupMap() {
        // 4. GUNAKAN 'let' UNTUK AKSES googleMap YANG AMAN
        googleMap?.let { map ->
            map.setOnMarkerClickListener { marker ->
                markerHospitalMap[marker]?.let { hospital ->
                    HospitalDetailActivity.start(this, hospital)
                }
                false
            }

            map.setOnMapClickListener { latLng ->
                val manualLocation = Location("manual").apply {
                    latitude = latLng.latitude
                    longitude = latLng.longitude
                }
                currentUserLocation = manualLocation
                binding.tvCurrentLocationText.text = "Lokasi Dipilih (Manual)"
                if (filterRadius5km) applyFiltersAndUpdate()
            }

            val initialLocation = LatLng(-7.7956, 110.3695) // Yogyakarta
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 11.5f))
            initLocationComponent()
        }
    }

    private fun setupUi() {
        binding.tvTitle.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        binding.btnMyBookings.setOnClickListener {
            val userId = getConsistentUserId()
            val intent = Intent(this, BookingStatusActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

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

        binding.btnChangeMapType.setOnClickListener {
            showMapTypeDialog()
        }
    }

    private fun showMapTypeDialog() {
        googleMap?.let { map ->
            val mapTypeOptions = arrayOf("Normal", "Satelit", "Terrain", "Hybrid")
            val checkedItem = when (map.mapType) {
                GoogleMap.MAP_TYPE_SATELLITE -> 1
                GoogleMap.MAP_TYPE_TERRAIN -> 2
                GoogleMap.MAP_TYPE_HYBRID -> 3
                else -> 0
            }

            AlertDialog.Builder(this)
                .setTitle("Pilih Tipe Peta")
                .setSingleChoiceItems(mapTypeOptions, checkedItem) { dialog, which ->
                    map.mapType = when (which) {
                        1 -> GoogleMap.MAP_TYPE_SATELLITE
                        2 -> GoogleMap.MAP_TYPE_TERRAIN
                        3 -> GoogleMap.MAP_TYPE_HYBRID
                        else -> GoogleMap.MAP_TYPE_NORMAL
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Batal", null)
                .show()
        } ?: Toast.makeText(this, "Peta belum siap, coba lagi sesaat.", Toast.LENGTH_SHORT).show()
    }

    private fun applyFiltersAndUpdate() {
        var filtered = hospitalList
        if (filterIcu) {
            filtered = filtered.filter { it.hasIcu }
        }
        if (filterRadius5km) {
            currentUserLocation?.let { loc ->
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

    private fun updateMarkers(hospitals: List<Hospital>) {
        googleMap?.let { map ->
            map.clear()
            markerHospitalMap.clear()

            hospitals.forEach { hospital ->
                val iconRes = if (hospital.hasIcu) R.drawable.ic_marker_icu else R.drawable.ic_marker_no_icu
                val iconColor = if (hospital.hasIcu) ContextCompat.getColor(this, R.color.red) else ContextCompat.getColor(this, R.color.grey)

                val markerBitmap = getBitmapFromVectorDrawable(iconRes, iconColor)
                val position = LatLng(hospital.latitude, hospital.longitude)

                val markerOptions = MarkerOptions()
                    .position(position)
                    .title(hospital.name)
                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap))
                    .anchor(0.5f, 1.0f)

                map.addMarker(markerOptions)?.let { createdMarker ->
                    markerHospitalMap[createdMarker] = hospital
                }
            }
        }
    }

    private fun showHospitalSearchDialog() {
        // ... (fungsi ini tidak perlu diubah, sudah cukup aman)
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
                val point = LatLng(selectedHospital.latitude, selectedHospital.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15.0f))
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
            val point = LatLng(nearest.latitude, nearest.longitude)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 14.5f))
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

        val initialLocation = LatLng(-7.7956, 110.3695)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 11.5f))
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

    @SuppressLint("MissingPermission")
    private fun initLocationComponent() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = false
        }
    }

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
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            // Izin diberikan
            initLocationComponent()
            getDeviceLocation()
        } else {
            // Izin ditolak
            Toast.makeText(this, "Izin lokasi dibutuhkan untuk fitur ini.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            // Izin sudah ada
            initLocationComponent()
            getDeviceLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentUserLocation = location
                binding.tvCurrentLocationText.text = "Lokasi Anda (GPS)"
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            } else {
                Toast.makeText(this, "Tidak bisa mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal mendapatkan lokasi: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
