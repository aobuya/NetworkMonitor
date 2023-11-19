package com.broadcast.networkmonitor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase



class Register : AppCompatActivity() {
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var regionEditText: EditText
    private lateinit var registerButton: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSignin: Button //btnSign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is already logged in, and if so, redirect to MainActivity
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        btnSignin = findViewById(R.id.btnSignIn)
        emailEditText = findViewById(R.id.et_email)
        passwordEditText = findViewById(R.id.et_password)
        regionEditText = findViewById(R.id.region_field)
        registerButton = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progressBar)


        val items = arrayOf("Mukhonje", "Bokoli", "Bukembe")

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        val autoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.region_field)
        autoCompleteTextView.setAdapter(adapter)

        autoCompleteTextView.threshold = 2

        autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position).toString()
            Toast.makeText(this, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()
        }

        btnSignin.setOnClickListener{

            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
        }



        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val region = regionEditText.text.toString()

            progressBar.visibility = View.VISIBLE

            // Register user with email and password
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.INVISIBLE
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val uid = user?.uid

                        // Store region under user's ID in Firebase Realtime Database
                        if (uid != null) {
                            val userRef = database.reference.child("users").child(uid)
                            userRef.child("region").setValue(region)

                            // Registration successful, handle next steps (e.g., navigate to next activity)
                            Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT)
                                .show()
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        // Registration failed, handle error (e.g., show error message)
                        Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        }
    }
}