package com.example.myapplication

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class ContactsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val listView: ListView = findViewById(R.id.contactsListView)

        // Load saved contacts
        val prefs = getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("contacts_list", emptySet()) ?: emptySet()

        val contactNames = mutableListOf<String>()

        for (contactId in savedSet) {
            val cursor: Cursor? = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        contactNames.add(it.getString(nameIndex))
                    }
                }
            }
            cursor?.close()
        }

        if (contactNames.isEmpty()) {
            contactNames.add("No contacts saved.")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, contactNames)
        listView.adapter = adapter
    }
}
