package com.broadcast.networkmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.telephony.CellSignalStrengthLte
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class MainActivity : ComponentActivity() {
    private lateinit var checkSignalButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var signalStrengthRef: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var wifiSignalStrengthTextView: TextView
    private lateinit var mobileSignalStrengthTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var signoutButton: Button

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signal_strength)
        auth = FirebaseAuth.getInstance()
        // Initialize views
        wifiSignalStrengthTextView = findViewById(R.id.wifiSignalStrengthTextView)
        mobileSignalStrengthTextView = findViewById(R.id.mobileSignalStrengthTextView)
        checkSignalButton = findViewById(R.id.checkSignalButton)
        progress = findViewById(R.id.progress_bar)
        signoutButton = findViewById(R.id.signOut)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkSignalButton.setOnClickListener{
            initializeSignalStrengthListener()
        }

        signoutButton.setOnClickListener{
            auth.signOut()
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
            finish()
        }
        val database = FirebaseDatabase.getInstance()
        signalStrengthRef = database.reference.child("signalStrength")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                REQUEST_PERMISSION
            )
        } else {
            initializeSignalStrengthListener()
        }
    }
    private fun initializeSignalStrengthListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (telephonyManager == null || wifiManager == null) {
            Toast.makeText(this, "Telephony or WiFi service not available", Toast.LENGTH_SHORT).show()
            return
        }
        val phoneStateListener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)
                val mobileSignalDetails = signalStrength?.let { getCellularSignalDetails(it) }
                val wifiSignalDetails = getWifiSignalDetails(wifiManager)
                if (mobileSignalDetails != null) {
                    updateUI(mobileSignalDetails, wifiSignalDetails)
                }
                mobileSignalDetails?.let { storeSignalStrength(wifiSignalDetails, it) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
            )
        } else {
            Toast.makeText(this, "This feature requires Android Q or later", Toast.LENGTH_SHORT).show()
        }
    }
    private fun getWifiSignalDetails(wifiManager: WifiManager): Pair<String, Int> {
        val wifiInfo = wifiManager.connectionInfo
        val wifiDbm = wifiInfo.rssi
        val wifiDescription = when {
            wifiDbm > -50 -> "Excellent signal"
            wifiDbm > -70 -> "Good signal"
            wifiDbm > -90 -> "Fair signal"
            else -> "Poor signal"
        }
        return Pair(wifiDescription, wifiDbm)
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getCellularSignalDetails(signalStrength: SignalStrength): Pair<String, Int> {
        val cellSignalStrengths = signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java)
        val dBm = cellSignalStrengths?.getOrNull(0)?.rsrp ?: -1

        val description = when {
            dBm > -90 -> "Excellent signal"
            dBm > -105 -> "Good signal"
            dBm > -120 -> "Fair signal"
            else -> "Poor signal"
        }

        return Pair(description, dBm)
    }
    private fun getSignalStrengthDbm(signalStrength: SignalStrength): Int {
        // Use reflection to access dbm value
        return try {
            val method = signalStrength.javaClass.getMethod("getDbm")
            method.invoke(signalStrength) as Int
        } catch (e: Exception) {
            -1
        }
    }
    @SuppressLint("MissingPermission")
    private fun getGPSLocation(callback: (latitude: Double, longitude: Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1234
            )
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                callback(it.latitude, it.longitude)
            }
        }
    }
    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    //add data to firebase
    private fun storeSignalStrength(wifiDetails: Pair<String, Int>, mobileDetails: Pair<String, Int>) {
        getGPSLocation { latitude, longitude ->
            val user = FirebaseAuth.getInstance().currentUser
            val androidId = user?.uid ?: "NaN"
            val signalStrengthEntryRef = signalStrengthRef.child(androidId)
            val signalStrengths = hashMapOf(
                "WiFi" to hashMapOf(
                    "SignalLevel" to wifiDetails.first,
                    "dBm" to wifiDetails.second
                ),
                "Mobile" to hashMapOf(
                    "SignalLevel" to mobileDetails.first,
                    "dBm" to mobileDetails.second
                ),
                "DateTime" to getCurrentDateTime(),
                "Latitude" to latitude,
                "Longitude" to longitude
            )
            if (user != null) {
                user.email?.let {
                    signalStrengths["AndroidId"] = it
                }
            }
            updateFirebase(signalStrengthEntryRef, signalStrengths)
            updateUI(wifiDetails, mobileDetails)
        }
    }
    private fun updateFirebase(signalStrengthEntryRef: DatabaseReference, signalStrengths: HashMap<String, Serializable>) {
        signalStrengthEntryRef.setValue(signalStrengths).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Firebase update successful")
            } else {
                Log.e(TAG, "Error updating Firebase: ${task.exception?.message}")
            }
        }
    }
    private fun updateUI(wifiDetails: Pair<String, Int>, mobileDetails: Pair<String, Int>) {
        wifiSignalStrengthTextView.text = "WiFi Signal Level: ${wifiDetails.first} (dBm: ${wifiDetails.second})"
        mobileSignalStrengthTextView.text = "Mobile Signal Level: ${mobileDetails.first} (dBm: ${mobileDetails.second})"
    }
    @RequiresApi(Build.VERSION_CODES.Q)

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1234 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted, get the location
                    getGPSLocation { _, _ -> }
                } else {
                    // Permission denied, show a message or handle as needed
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }
    companion object {
        private const val REQUEST_PERMISSION = 1
    }
}