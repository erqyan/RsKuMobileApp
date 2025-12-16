package com.example.myrs

import android.os.Parcelable
import com.google.firebase.database.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Hospital(
    var id: String = "", // RTDB Key

    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("address") @set:PropertyName("address")
    var address: String = "",

    @get:PropertyName("latitude") @set:PropertyName("latitude")
    var latitude: Double = 0.0,

    @get:PropertyName("longitude") @set:PropertyName("longitude")
    var longitude: Double = 0.0,

    @get:PropertyName("phone") @set:PropertyName("phone")
    var phone: String = "",

    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = "",

    @get:PropertyName("beds_total") @set:PropertyName("beds_total")
    var bedsTotal: Int = 0,

    @get:PropertyName("beds_available") @set:PropertyName("beds_available")
    var bedsAvailable: Int = 0,

    @get:PropertyName("icu_available") @set:PropertyName("icu_available")
    var icuAvailable: Int = 0,

    @get:PropertyName("er_queue") @set:PropertyName("er_queue")
    var erQueue: Int = 0,

    @get:PropertyName("city") @set:PropertyName("city")
    var city: String = "",

    @get:PropertyName("province") @set:PropertyName("province")
    var province: String = ""
) : Parcelable {
    val hasIcu: Boolean get() = icuAvailable > 0
}

// Model for Booking IGD (Registrasi)
// Pastikan hanya ada SATU definisi data class ini di file
data class ErRegistration(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",

    @get:PropertyName("user_id") @set:PropertyName("user_id")
    var userId: String = "",

    @get:PropertyName("hospital_id") @set:PropertyName("hospital_id")
    var hospitalId: String = "",

    // --- Data Pasien Baru ---
    @get:PropertyName("patient_name") @set:PropertyName("patient_name")
    var patientName: String = "",

    @get:PropertyName("nik") @set:PropertyName("nik")
    var nik: String = "",

    @get:PropertyName("phone") @set:PropertyName("phone")
    var phone: String = "",

    @get:PropertyName("gender") @set:PropertyName("gender")
    var gender: String = "",
    // -----------------------

    @get:PropertyName("status") @set:PropertyName("status")
    var status: String = "waiting", // waiting, confirmed, completed, cancelled

    @get:PropertyName("note") @set:PropertyName("note")
    var note: String = "", // Keluhan

    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Long = System.currentTimeMillis()
)
