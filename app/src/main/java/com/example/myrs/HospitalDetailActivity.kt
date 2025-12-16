package com.example.myrs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.myrs.databinding.ActivityHospitalDetailBinding
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.UUID

class HospitalDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_HOSPITAL = "EXTRA_HOSPITAL"
        fun start(context: Context, hospital: Hospital) {
            val intent = Intent(context, HospitalDetailActivity::class.java).apply {
                putExtra(EXTRA_HOSPITAL, hospital)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityHospitalDetailBinding
    private var hospital: Hospital? = null
    private val db = Firebase.database.reference

    // --- VARIABEL UNTUK SLIDESHOW ---
    private var currentPhotoIndex = 0
    private var photoList: List<Photo> = listOf()
    private val sliderHandler = Handler(Looper.getMainLooper())
    private val sliderRunnable = object : Runnable {
        override fun run() {
            if (photoList.isNotEmpty()) {
                // Pindah ke foto berikutnya
                currentPhotoIndex = (currentPhotoIndex + 1) % photoList.size
                updatePhotoDisplay()

                // Jadwalkan ulang untuk 3 detik lagi
                sliderHandler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHospitalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hospital = intent.getParcelableExtra(EXTRA_HOSPITAL)
        if (hospital == null) {
            Toast.makeText(this, "Data rumah sakit tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val h = hospital!!

        setupUI(h)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnSubmitBooking.setOnClickListener { handleBookingSubmission(h) }
        binding.btnOpenInMap.setOnClickListener { openGoogleMaps(h) }
    }

    private fun setupUI(h: Hospital) {
        binding.tvHospitalName.text = h.name
        binding.tvIcuStatus.text = if (h.hasIcu) "ICU Tersedia (${h.icuAvailable})" else "ICU Penuh"
        binding.tvLatLng.text = "${h.address}\nTipe: ${h.type} | Antrean IGD: ${h.erQueue}"

        // --- SETUP SLIDESHOW FOTO ---
        photoList = h.photos

        if (photoList.isNotEmpty()) {
            // Tampilkan foto pertama
            updatePhotoDisplay()

            // Tampilkan tombol navigasi
            binding.btnNextPhoto.visibility = View.VISIBLE
            binding.btnPrevPhoto.visibility = View.VISIBLE
            binding.tvPhotoIndicator.visibility = View.VISIBLE

            // Mulai Auto Scroll
            sliderHandler.postDelayed(sliderRunnable, 3000)

            // Logic Tombol Next (Manual)
            binding.btnNextPhoto.setOnClickListener {
                // Hentikan timer sementara agar tidak bentrok
                sliderHandler.removeCallbacks(sliderRunnable)

                // Pindah Next
                currentPhotoIndex = (currentPhotoIndex + 1) % photoList.size
                updatePhotoDisplay()

                // Mulai lagi timer 3 detik dari sekarang
                sliderHandler.postDelayed(sliderRunnable, 3000)
            }

            // Logic Tombol Prev (Manual)
            binding.btnPrevPhoto.setOnClickListener {
                sliderHandler.removeCallbacks(sliderRunnable)

                // Pindah Prev (Cek agar index tidak minus)
                if (currentPhotoIndex - 1 < 0) {
                    currentPhotoIndex = photoList.size - 1
                } else {
                    currentPhotoIndex--
                }
                updatePhotoDisplay()

                sliderHandler.postDelayed(sliderRunnable, 3000)
            }

        } else {
            // Jika tidak ada foto, sembunyikan tombol navigasi & set default
            binding.ivHospitalImage.setImageResource(android.R.drawable.ic_menu_gallery)
            binding.btnNextPhoto.visibility = View.GONE
            binding.btnPrevPhoto.visibility = View.GONE
            binding.tvPhotoIndicator.visibility = View.GONE
        }
    }

    // Fungsi Helper untuk Update Gambar di ImageView
    private fun updatePhotoDisplay() {
        if (photoList.isEmpty()) return

        val url = photoList[currentPhotoIndex].url

        // Update Indicator Text (misal: 1/3)
        binding.tvPhotoIndicator.text = "${currentPhotoIndex + 1}/${photoList.size}"

        Glide.with(this)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade()) // Efek transisi halus
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)
            .into(binding.ivHospitalImage)
    }

    // Penting: Hentikan Handler saat Activity dihancurkan agar tidak memory leak
    override fun onDestroy() {
        super.onDestroy()
        sliderHandler.removeCallbacks(sliderRunnable)
    }

    // ... (Sisa fungsi handleBookingSubmission, submitBookingToRTDB, dll biarkan SAMA SEPERTI SEBELUMNYA) ...

    private fun handleBookingSubmission(hospital: Hospital) {
        // ... (Kode sama) ...
        val name = binding.etPatientName.text.toString().trim()
        val nik = binding.etNik.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val keluhan = binding.etKeluhan.text.toString().trim()
        val selectedGenderId = binding.rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            findViewById<RadioButton>(selectedGenderId).text.toString()
        } else { "" }

        if (name.isEmpty()) { binding.etPatientName.error = "Wajib diisi"; return }
        if (nik.isEmpty()) { binding.etNik.error = "Wajib diisi"; return }
        if (phone.isEmpty()) { binding.etPhone.error = "Wajib diisi"; return }
        if (gender.isEmpty()) { Toast.makeText(this, "Pilih Gender", Toast.LENGTH_SHORT).show(); return }
        if (keluhan.isEmpty()) { binding.etKeluhan.error = "Wajib diisi"; return }

        binding.btnSubmitBooking.isEnabled = false
        submitBookingToRTDB(hospital, name, nik, phone, gender, keluhan)
    }

    // Pastikan copy function submitBookingToRTDB, showBookingConfirmationDialog, openGoogleMaps
    // dari kode sebelumnya ke sini. Saya persingkat agar fokus di fitur foto.

    private fun submitBookingToRTDB(hospital: Hospital, name: String, nik: String, phone: String, gender: String, keluhan: String) {
        val sharedPrefs = getSharedPreferences("MyRS_Prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("DEVICE_USER_ID", null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("DEVICE_USER_ID", userId).apply()
        }
        val newRef = db.child("registrations").push()
        val regId = newRef.key ?: ""
        val registration = ErRegistration(
            id = regId, userId = userId!!, hospitalId = hospital.id,
            patientName = name, nik = nik, phone = phone, gender = gender,
            status = "waiting", note = keluhan, createdAt = System.currentTimeMillis()
        )
        newRef.setValue(registration).addOnSuccessListener {
            binding.btnSubmitBooking.isEnabled = true
            showBookingConfirmationDialog(hospital, regId)
        }.addOnFailureListener {
            binding.btnSubmitBooking.isEnabled = true
            Toast.makeText(this, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBookingConfirmationDialog(hospital: Hospital, regCode: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Berhasil").setMessage("Kode: ${regCode.takeLast(6)}")
        builder.setPositiveButton("OK") { d, _ -> openGoogleMaps(hospital); d.dismiss() }
        builder.show()
    }

    private fun openGoogleMaps(hospital: Hospital) {
        val uri = Uri.parse("google.navigation:q=${hospital.latitude},${hospital.longitude}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
