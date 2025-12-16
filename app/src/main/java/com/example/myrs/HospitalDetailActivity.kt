package com.example.myrs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    // RTDB Reference
    private val db = Firebase.database.reference

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

        binding.btnSubmitBooking.setOnClickListener {
            handleBookingSubmission(h)
        }

        binding.btnOpenInMap.setOnClickListener { openGoogleMaps(h) }
    }

    private fun setupUI(h: Hospital) {
        binding.tvHospitalName.text = h.name
        binding.tvIcuStatus.text = if (h.hasIcu) "ICU Tersedia (${h.icuAvailable})" else "ICU Penuh"
        binding.tvLatLng.text = "${h.address}\nTipe: ${h.type} | Antrean IGD: ${h.erQueue}"
    }

    private fun handleBookingSubmission(hospital: Hospital) {
        val name = binding.etPatientName.text.toString().trim()
        val nik = binding.etNik.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val keluhan = binding.etKeluhan.text.toString().trim()

        val selectedGenderId = binding.rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            findViewById<RadioButton>(selectedGenderId).text.toString()
        } else {
            ""
        }

        if (name.isEmpty()) {
            binding.etPatientName.error = "Nama wajib diisi"
            binding.etPatientName.requestFocus()
            return
        }
        if (nik.isEmpty() || nik.length != 16) {
            binding.etNik.error = "NIK harus 16 digit"
            binding.etNik.requestFocus()
            return
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Nomor Telepon wajib diisi"
            binding.etPhone.requestFocus()
            return
        }
        if (gender.isEmpty()) {
            Toast.makeText(this, "Silakan pilih jenis kelamin", Toast.LENGTH_SHORT).show()
            return
        }
        if (keluhan.isEmpty()) {
            binding.etKeluhan.error = "Mohon isi keluhan utama"
            binding.etKeluhan.requestFocus()
            return
        }

        binding.btnSubmitBooking.isEnabled = false
        binding.btnSubmitBooking.text = "Mengirim Data..."

        submitBookingToRTDB(hospital, name, nik, phone, gender, keluhan)
    }

    private fun submitBookingToRTDB(
        hospital: Hospital,
        name: String,
        nik: String,
        phone: String,
        gender: String,
        keluhan: String
    ) {
        // --- PERBAIKAN UTAMA: AMBIL ID KONSISTEN DARI PREFERENCES ---
        val sharedPrefs = getSharedPreferences("MyRS_Prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("DEVICE_USER_ID", null)

        // Jika null (sangat jarang terjadi), buat baru dan SIMPAN
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("DEVICE_USER_ID", userId).apply()
        }
        // -------------------------------------------------------------

        val newRef = db.child("registrations").push()
        val regId = newRef.key ?: ""

        val registration = ErRegistration(
            id = regId,
            userId = userId!!, // Menggunakan ID yang konsisten
            hospitalId = hospital.id,
            patientName = name,
            nik = nik,
            phone = phone,
            gender = gender,
            status = "waiting",
            note = keluhan,
            createdAt = System.currentTimeMillis()
        )

        newRef.setValue(registration)
            .addOnSuccessListener {
                binding.btnSubmitBooking.isEnabled = true
                binding.btnSubmitBooking.text = "DAFTAR SEKARANG"

                binding.etPatientName.text?.clear()
                binding.etNik.text?.clear()
                binding.etPhone.text?.clear()
                binding.etKeluhan.text?.clear()
                binding.rgGender.clearCheck()

                showBookingConfirmationDialog(hospital, regId)
            }
            .addOnFailureListener { e ->
                binding.btnSubmitBooking.isEnabled = true
                binding.btnSubmitBooking.text = "DAFTAR SEKARANG"
                Toast.makeText(this, "Gagal mendaftar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBookingConfirmationDialog(hospital: Hospital, regCode: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pendaftaran Berhasil!")
        builder.setMessage("Data Anda telah masuk ke sistem IGD ${hospital.name}.\n\nKode Booking: ${regCode.takeLast(6).uppercase()}\n\nSilakan segera menuju lokasi.")
        builder.setPositiveButton("Buka Rute Maps") { dialog, _ ->
            openGoogleMaps(hospital)
            dialog.dismiss()
        }
        builder.setNegativeButton("Tutup") { dialog, _ ->
            dialog.dismiss()
        }
        builder.setCancelable(false)
        builder.create().show()
    }

    private fun openGoogleMaps(hospital: Hospital) {
        val gmmIntentUri = Uri.parse("google.navigation:q=${hospital.latitude},${hospital.longitude}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${hospital.latitude},${hospital.longitude}"))
            startActivity(browserIntent)
        }
    }
}
