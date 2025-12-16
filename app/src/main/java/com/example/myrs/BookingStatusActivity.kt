package com.example.myrs

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myrs.databinding.ActivityBookingStatusBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class BookingStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingStatusBinding
    private val db = Firebase.database.reference
    private lateinit var adapter: BookingStatusAdapter
    private val bookingDataList = mutableListOf<Pair<ErRegistration, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val userId = intent.getStringExtra("USER_ID")
        if (userId == null) {
            Toast.makeText(this, "User ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        fetchUserBookings(userId)
    }

    private fun setupRecyclerView() {
        adapter = BookingStatusAdapter(bookingDataList) { booking ->
            showCancelDialog(booking)
        }
        binding.rvBookingStatus.layoutManager = LinearLayoutManager(this)
        binding.rvBookingStatus.adapter = adapter
    }

    private fun fetchUserBookings(userId: String) {
        // Menggunakan filter manual (Client Side) sementara untuk memastikan data muncul
        // meskipun Rules Index belum diset di Firebase Console.
        db.child("registrations")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tempBookings = mutableListOf<ErRegistration>()

                    for (child in snapshot.children) {
                        val booking = child.getValue(ErRegistration::class.java)
                        // FILTER MANUAL: Hanya ambil yang userId nya cocok
                        if (booking != null && booking.userId == userId) {
                            booking.id = child.key ?: ""
                            tempBookings.add(booking)
                        }
                    }

                    tempBookings.sortByDescending { it.createdAt }

                    if (tempBookings.isEmpty()) {
                        showEmptyState(true)
                    } else {
                        fetchHospitalNamesAndDisplay(tempBookings)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BookingStatusActivity, "Gagal memuat data: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchHospitalNamesAndDisplay(bookings: List<ErRegistration>) {
        bookingDataList.clear()

        // Isi list dengan placeholder dulu agar urutan benar
        bookings.forEach { booking ->
            bookingDataList.add(Pair(booking, "Memuat nama RS..."))
        }

        // Notify adapter awal
        adapter.notifyDataSetChanged()
        showEmptyState(false)

        // Ambil nama RS satu per satu
        bookings.forEachIndexed { index, booking ->
            db.child("hospitals").child(booking.hospitalId).child("name").get()
                .addOnSuccessListener { dataSnapshot ->
                    val hospitalName = dataSnapshot.value as? String ?: "RS Tidak Dikenal"

                    // Update item di index yang sesuai
                    if (index < bookingDataList.size) {
                        bookingDataList[index] = Pair(booking, hospitalName)
                        adapter.notifyItemChanged(index)
                    }
                }
        }
    }

    private fun showCancelDialog(booking: ErRegistration) {
        AlertDialog.Builder(this)
            .setTitle("Batalkan Pendaftaran?")
            .setMessage("Apakah Anda yakin ingin membatalkan pendaftaran ini?")
            .setPositiveButton("Ya, Batalkan") { _, _ ->
                cancelBooking(booking.id)
            }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun cancelBooking(bookingId: String) {
        db.child("registrations").child(bookingId).child("status").setValue("cancelled")
            .addOnSuccessListener {
                Toast.makeText(this, "Pendaftaran dibatalkan", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal membatalkan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvBookingStatus.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvBookingStatus.visibility = View.VISIBLE
        }
    }
}
