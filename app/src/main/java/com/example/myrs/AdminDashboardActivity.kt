package com.example.myrs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Pastikan Binding Class ini ada (layout xml harus sudah dibuat)
import com.example.myrs.databinding.ActivityAdminDashboardBinding
import com.example.myrs.databinding.DialogAddEditHospitalBinding
import com.example.myrs.databinding.ItemAdminHospitalBinding

// IMPORT FIREBASE YANG BENAR
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database // Penting untuk ekstensi .database
import com.google.firebase.ktx.Firebase        // Penting untuk akses Firebase root

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding

    // Inisialisasi Database Reference
    private val db = Firebase.database.reference

    private lateinit var adapter: AdminHospitalAdapter
    private val hospitalList = mutableListOf<Hospital>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchHospitals()

        binding.fabAddHospital.setOnClickListener {
            showAddEditDialog(null)
        }

        binding.btnViewRegistrations.setOnClickListener {
            Toast.makeText(this, "Fitur Registrations List belum diimplementasikan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = AdminHospitalAdapter(hospitalList,
            onEdit = { hospital -> showAddEditDialog(hospital) },
            onDelete = { hospital -> deleteHospital(hospital) }
        )
        binding.rvHospitals.layoutManager = LinearLayoutManager(this)
        binding.rvHospitals.adapter = adapter
    }

    private fun fetchHospitals() {
        // Ambil data dari node "hospitals" di Realtime Database
        db.child("hospitals").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                hospitalList.clear()
                for (child in snapshot.children) {
                    val h = child.getValue(Hospital::class.java)
                    if (h != null) {
                        h.id = child.key ?: "" // Simpan Key RTDB sebagai ID
                        hospitalList.add(h)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminDashboardActivity, "Gagal memuat data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddEditDialog(hospital: Hospital?) {
        // Pastikan layout 'dialog_add_edit_hospital.xml' sudah dibuat agar binding ini jalan
        val dialogBinding = DialogAddEditHospitalBinding.inflate(layoutInflater)

        if (hospital != null) {
            dialogBinding.etName.setText(hospital.name)
            dialogBinding.etAddress.setText(hospital.address)
            dialogBinding.etLat.setText(hospital.latitude.toString())
            dialogBinding.etLng.setText(hospital.longitude.toString())
            dialogBinding.etIcu.setText(hospital.icuAvailable.toString())
            dialogBinding.etBeds.setText(hospital.bedsAvailable.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (hospital == null) "Tambah RS" else "Edit RS")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etName.text.toString()
                val address = dialogBinding.etAddress.text.toString()
                val lat = dialogBinding.etLat.text.toString().toDoubleOrNull() ?: 0.0
                val lng = dialogBinding.etLng.text.toString().toDoubleOrNull() ?: 0.0
                val icu = dialogBinding.etIcu.text.toString().toIntOrNull() ?: 0
                val beds = dialogBinding.etBeds.text.toString().toIntOrNull() ?: 0

                val newHospital = Hospital(
                    id = hospital?.id ?: "",
                    name = name,
                    address = address,
                    latitude = lat,
                    longitude = lng,
                    icuAvailable = icu,
                    bedsAvailable = beds,
                    type = "General"
                )
                saveHospital(newHospital)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveHospital(hospital: Hospital) {
        if (hospital.id.isEmpty()) {
            // Create New Data
            val newRef = db.child("hospitals").push()
            hospital.id = newRef.key ?: ""
            newRef.setValue(hospital)
        } else {
            // Update Existing Data
            db.child("hospitals").child(hospital.id).setValue(hospital)
        }
    }

    private fun deleteHospital(hospital: Hospital) {
        AlertDialog.Builder(this)
            .setTitle("Hapus RS")
            .setMessage("Yakin hapus ${hospital.name}?")
            .setPositiveButton("Hapus") { _, _ ->
                db.child("hospitals").child(hospital.id).removeValue()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}

// RecyclerView Adapter
class AdminHospitalAdapter(
    private val list: List<Hospital>,
    private val onEdit: (Hospital) -> Unit,
    private val onDelete: (Hospital) -> Unit
) : RecyclerView.Adapter<AdminHospitalAdapter.VH>() {

    inner class VH(val binding: ItemAdminHospitalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAdminHospitalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.binding.tvName.text = item.name
        holder.binding.tvDetails.text = "ICU: ${item.icuAvailable} | Beds: ${item.bedsAvailable}"
        holder.binding.btnEdit.setOnClickListener { onEdit(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = list.size
}
