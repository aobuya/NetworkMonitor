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
import java.text.SimpleDateFormat
import java.util.*

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





        checkSignalButton.setOnClickListener {
            checkSignalStrength()

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
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val phoneStateListener = object : PhoneStateListener() {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)

                // Get mobile signal details
                val mobileSignalDetails = signalStrength?.let { getCellularSignalDetails(it) }

                // Get WiFi signal details
                val wifiInfo = wifiManager.connectionInfo
                val wifiDbm = wifiInfo.rssi
                val wifiDescription = when {
                    wifiDbm > -50 -> "Excellent signal"
                    wifiDbm > -70 -> "Good signal"
                    wifiDbm > -90 -> "Fair signal"
                    else -> "Poor signal"
                }
                val wifiSignalDetails = Pair(wifiDescription, wifiDbm)

                // Determine the signal status for mobile and Wi-Fi
                val mobileSignalStatus = if (mobileSignalDetails != null) {
                    "Mobile Signal Level: ${mobileSignalDetails.first} (dBm: ${mobileSignalDetails.second})"
                } else {
                    "No Data Connected"
                }

                val wifiSignalStatus = if (isConnectedToWiFi()) {
                    "Wi-Fi Signal Level: ${wifiSignalDetails.first} (RSSI: ${wifiSignalDetails.second} dBm)"
                } else {
                    "No Wi-Fi Connection"
                }

                // Display the signal statuses
                wifiSignalStrengthTextView.text = wifiSignalStatus
                mobileSignalStrengthTextView.text = mobileSignalStatus

                if (mobileSignalDetails != null) {
                    storeSignalStrength(wifiSignalDetails, mobileSignalDetails)
                } else {
                    Toast.makeText(this@MainActivity, "No signal", Toast.LENGTH_SHORT).show()
                }
            }
        }

        telephonyManager.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
        )
    }


    //Get location lat and lan
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getCellularSignalDetails(signalStrength: SignalStrength): Pair<String, Int> {
        val cellSignalStrengths = signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java)
        val dBm = cellSignalStrengths?.getOrNull(0)?.rsrp ?: Int.MIN_VALUE

        val description = when {
            dBm > -65 -> "Excellent signal"
            dBm > -75 -> "Good signal"
            dBm > -90 -> "Fair signal"
            dBm == Int.MIN_VALUE -> "Signal Strength Unavailable"
            else -> "Poor signal"
        }

        return Pair(description, dBm)
    }



    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    private fun storeSignalStrength(wifiDetails: Pair<String, Int>, mobileDetails: Pair<String, Int>) {
        getGPSLocation { latitude, longitude ->
            // Get the device's Android ID as the unique identifier
            //val androidId = Secure.getString(contentResolver, Secure.ANDROID_ID)
            val user = FirebaseAuth.getInstance().currentUser
            val androidId = user?.uid?:""

            // val currentUser

            val signalStrengthEntryRef = signalStrengthRef.child(androidId)

            // Create a SignalStrength object to hold the data
            val signalStrength = HashMap<String, Any>()

            // WiFi Data
            val wifiData = HashMap<String, Any>()
            wifiData["SignalLevel"] = wifiDetails.first
            wifiData["dBm"] = wifiDetails.second
            signalStrength["WiFi"] = wifiData

            // Mobile Data
            val mobileData = HashMap<String, Any>()
            mobileData["SignalLevel"] = mobileDetails.first
            mobileData["dBm"] = mobileDetails.second
            signalStrength["Mobile"] = mobileData

            // Add the Android ID as a unique identifier for the device
            if (user != null) {
                signalStrength["AndroidId"] = user.email!!
            }


            signalStrength["DateTime"] = getCurrentDateTime()
            signalStrength["Latitude"] = latitude
            signalStrength["Longitude"] = longitude

            Log.d(TAG, "Updating Firebase with signal data")

            // Set the data for the specified Android ID
            signalStrengthEntryRef.setValue(signalStrength).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Update complete")
                } else {
                    Log.e(TAG, "Error updating data: ${task.exception?.message}")
                }
            }

            wifiSignalStrengthTextView.text = "WiFi Signal Level: ${wifiDetails.first} (dBm: ${wifiDetails.second})"
            mobileSignalStrengthTextView.text = "Mobile Signal Level: ${mobileDetails.first} (dBm: ${mobileDetails.second})"
        }
    }




    private fun isConnectedToWiFi(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkSignalStrength() {
        progress.visibility = View.VISIBLE

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val wifiDbm = wifiInfo.rssi

        val wifiDescription = when {
            wifiDbm > -50 -> "Excellent signal"
            wifiDbm > -70 -> "Good signal"
            wifiDbm > -90 -> "Fair signal"
            else -> "Poor signal"
        }
        val wifiSignalDetails = Pair(wifiDescription, wifiDbm)

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val signalStrength = telephonyManager.signalStrength
        val mobileSignalDetails = signalStrength?.let { getCellularSignalDetails(it) }

        wifiSignalStrengthTextView.text = "Wi-Fi Signal Level: ${wifiSignalDetails.first} (RSSI: ${wifiSignalDetails.second} dBm)"
        mobileSignalStrengthTextView.text = "Mobile Signal Level: ${mobileSignalDetails?.first} (RSRP: ${mobileSignalDetails?.second} dBm)"

        if (mobileSignalDetails != null) {
            storeSignalStrength(wifiSignalDetails, mobileSignalDetails)
        } else {
            //
        }

        progress.visibility = View.INVISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateSignalStrengths() {
        // For Wi-Fi (RSSI)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val wifiDbm = wifiInfo.rssi

        val wifiDescription = when {
            wifiDbm > -50 -> "Excellent signal"
            wifiDbm > -70 -> "Good signal"
            wifiDbm > -90 -> "Fair signal"
            else -> "Poor signal"
        }
        wifiSignalStrengthTextView.text = "Wi-Fi Signal Level: $wifiDescription (RSSI: $wifiDbm dBm)"

        // For Mobile Data (RSRP)
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val signalStrength = telephonyManager.signalStrength
        val mobileSignalDetails = signalStrength?.let { getCellularSignalDetails(it) }
        mobileSignalStrengthTextView.text = "Mobile Signal Level: ${mobileSignalDetails?.first} (RSRP: ${mobileSignalDetails?.second} dBm)"
    }
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