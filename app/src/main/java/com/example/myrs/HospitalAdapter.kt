package com.example.myrs

// HospitalAdapter.kt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HospitalAdapter(
    private var hospitals: List<Hospital>,
    private val onItemClicked: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.hospitalItemRoot)
        val name: TextView = view.findViewById(R.id.tvHospitalName)
        val details: TextView = view.findViewById(R.id.tvHospitalDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hospital, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = hospitals[position]
        holder.name.text = hospital.name

        val icuStatus = if (hospital.hasIcu) "ICU Tersedia ✅" else "Tanpa ICU ❌"
        // Placeholder Jarak
        val distance = "Jarak: ${String.format("%.1f", Math.random() * 5)} km"

        holder.details.text = "$icuStatus | $distance"

        holder.root.setOnClickListener {
            onItemClicked(hospital)
        }
    }

    override fun getItemCount() = hospitals.size

    fun updateData(newHospitals: List<Hospital>) {
        this.hospitals = newHospitals
        notifyDataSetChanged()
    }
}