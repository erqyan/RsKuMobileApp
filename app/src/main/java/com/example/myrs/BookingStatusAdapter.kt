package com.example.myrs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myrs.databinding.ItemBookingStatusBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingStatusAdapter(
    private val bookingList: MutableList<Pair<ErRegistration, String>>, // Pair: Data Booking & Nama RS
    private val onCancelClick: (ErRegistration) -> Unit
) : RecyclerView.Adapter<BookingStatusAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemBookingStatusBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookingStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (booking, hospitalName) = bookingList[position]

        holder.binding.tvHospitalName.text = hospitalName
        holder.binding.tvPatientName.text = "Pasien: ${booking.patientName}"
        holder.binding.tvQueueInfo.text = "Kode: ${booking.id.takeLast(6).uppercase()}"

        // Format Tanggal
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        holder.binding.tvBookingDate.text = sdf.format(Date(booking.createdAt))

        // Atur Status Badge
        when (booking.status) {
            "waiting" -> {
                holder.binding.tvStatusBadge.text = "Menunggu Konfirmasi"
                holder.binding.tvStatusBadge.setTextColor(Color.parseColor("#E65100")) // Orange
                holder.binding.tvStatusBadge.background.setTint(Color.parseColor("#FFF3E0"))
                holder.binding.btnCancel.isEnabled = true
            }
            "confirmed" -> {
                holder.binding.tvStatusBadge.text = "Dikonfirmasi"
                holder.binding.tvStatusBadge.setTextColor(Color.parseColor("#1B5E20")) // Hijau
                holder.binding.tvStatusBadge.background.setTint(Color.parseColor("#E8F5E9"))
                holder.binding.btnCancel.isEnabled = false // Tidak bisa batal jika sudah confirm
                holder.binding.btnCancel.alpha = 0.5f
            }
            "completed" -> {
                holder.binding.tvStatusBadge.text = "Selesai"
                holder.binding.tvStatusBadge.setTextColor(Color.parseColor("#0D47A1")) // Biru
                holder.binding.tvStatusBadge.background.setTint(Color.parseColor("#E3F2FD"))
                holder.binding.btnCancel.visibility = android.view.View.GONE
            }
            "cancelled" -> {
                holder.binding.tvStatusBadge.text = "Dibatalkan"
                holder.binding.tvStatusBadge.setTextColor(Color.parseColor("#B71C1C")) // Merah
                holder.binding.tvStatusBadge.background.setTint(Color.parseColor("#FFEBEE"))
                holder.binding.btnCancel.visibility = android.view.View.GONE
            }
        }

        holder.binding.btnCancel.setOnClickListener {
            onCancelClick(booking)
        }
    }

    override fun getItemCount() = bookingList.size
}
