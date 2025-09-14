package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.content.Intent
import androidx.appcompat.widget.AppCompatImageButton
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CONTACT_PERMISSION_REQUEST = 1
    private val LOCATION_AND_SMS_PERMISSION_REQUEST = 2

    private val selectedContactIds = ArrayList<String>()
    private val TAG = "MainActivity"

    // Use the correct types as defined in your XML layout file
    private lateinit var btnAddContact: ImageButton
    private lateinit var btnSendLocation: ImageButton
    private lateinit var btnShowContacts: ImageButton

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, "Contact pick cancelled", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val cursorId: Cursor? = contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            var contactId: String? = null
            cursorId?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIndex != -1) {
                        contactId = it.getString(idIndex)
                    }
                }
            }
            cursorId?.close()

            if (contactId == null) {
                Toast.makeText(this, "Failed to get contact ID", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            if (!selectedContactIds.contains(contactId)) {
                selectedContactIds.add(contactId)
                saveContacts()

                val nameCursor: Cursor? = contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    null, null, null
                )
                var contactName = "Contact"
                nameCursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            contactName = it.getString(nameIndex)
                        }
                    }
                }
                nameCursor?.close()

                Toast.makeText(this, "Added: $contactName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Contact already added.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

       
        btnAddContact = findViewById(R.id.btnAddContact)
        btnSendLocation = findViewById(R.id.imageButton)
        btnShowContacts = findViewById(R.id.btnShowContacts)

        loadContacts()

        btnAddContact.setOnClickListener {
            requestReadContactsThenPick()
        }

        btnSendLocation.setOnClickListener {
            if (selectedContactIds.isEmpty()) {
                Toast.makeText(this, "Error: Please add at least one contact.", Toast.LENGTH_LONG).show()
            } else {
                checkAllPermissionsAndSend()
            }
        }

        btnShowContacts.setOnClickListener {
            val intent = Intent(this, ContactsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadContacts() {
        val prefs = getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("contacts_list", emptySet())
        selectedContactIds.clear()
        selectedContactIds.addAll(savedSet?.toList() ?: listOf())
    }

    private fun saveContacts() {
        val prefs = getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val contactSet = selectedContactIds.toSet()
        editor.putStringSet("contacts_list", contactSet).apply()
    }

    private fun requestReadContactsThenPick() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                CONTACT_PERMISSION_REQUEST
            )
        } else {
            pickContactLauncher.launch(null)
        }
    }

    private fun checkAllPermissionsAndSend() {
        val permissionsToRequest = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_AND_SMS_PERMISSION_REQUEST
            )
        } else {
            getLastLocationAndSend()
        }
    }

    private fun getLastLocationAndSend() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Error: Location permission not granted.", Toast.LENGTH_LONG).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSmsToAll(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "Error: Location is null. Enable GPS.", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error: Failed to get location. ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getPhoneNumberFromContactId(contactId: String): String? {
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val phoneCursor: Cursor? = contentResolver.query(
            phoneUri,
            phoneProjection,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        var phoneNumber: String? = null
        phoneCursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    phoneNumber = it.getString(numberIndex).trim()
                }
            }
        }
        phoneCursor?.close()
        return phoneNumber?.replace("[^\\d+]".toRegex(), "")
    }

    private fun sendSmsToAll(lat: Double, lng: Double) {
        val message = "Emergency! My location: https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        val smsManager = SmsManager.getDefault()
        var sentCount = 0

        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val countryCode = telephonyManager.simCountryIso.uppercase()

            for (contactId in selectedContactIds) {
                var phoneNumber = getPhoneNumberFromContactId(contactId)

                if (phoneNumber.isNullOrBlank()) {
                    Log.e(TAG, "Skipping invalid number for contact: $contactId")
                    continue
                }

                if (!phoneNumber.startsWith("+")) {
                    val formattedNumber = PhoneNumberUtils.formatNumberToE164(phoneNumber, countryCode)
                    if (formattedNumber != null) {
                        phoneNumber = formattedNumber
                    }
                }

                if (phoneNumber.isNotBlank()) {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    sentCount++
                }
            }

           
            if (sentCount > 0) {
                Toast.makeText(this, "Sent SOS to $sentCount contacts.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No valid numbers to send SOS.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}", e)
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CONTACT_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickContactLauncher.launch(null)
                } else {
                    Toast.makeText(this, "Contact permission required.", Toast.LENGTH_LONG).show()
                }
            }
            LOCATION_AND_SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    getLastLocationAndSend()
                } else {
                    Toast.makeText(this, "Location & SMS permissions required.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("contacts", selectedContactIds)
    }
}
