package com.example.aqua_control

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.aqua_control.data.AuthDatabaseHelper
import com.example.aqua_control.data.AuthResult

class MainActivity : android.app.Activity() {
    private lateinit var authDatabase: AuthDatabaseHelper
    private lateinit var inputUsername: EditText
    private lateinit var inputPassword: EditText
    private lateinit var textLoginMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_login)

        authDatabase = AuthDatabaseHelper(activityContext)
        inputUsername = super.findViewById(R.id.inputUsername)
        inputPassword = super.findViewById(R.id.inputPassword)
        textLoginMessage = super.findViewById(R.id.textLoginMessage)

        val buttonLogin: Button = super.findViewById(R.id.buttonLogin)
        val buttonCreateUser: Button = super.findViewById(R.id.buttonCreateUser)
        buttonLogin.setOnClickListener { login() }
        buttonCreateUser.setOnClickListener { createUser() }
    }

    private fun login() {
        val username = inputUsername.text.toString()
        val password = inputPassword.text.toString()

        if (authDatabase.validateUser(username, password)) {
            super.startActivity(Intent(activityContext, DashboardActivity::class.java))
            inputPassword.text.clear()
        } else {
            showMessage("Usuario o contrasena incorrectos", isError = true)
        }
    }

    private fun createUser() {
        val username = inputUsername.text.toString()
        val password = inputPassword.text.toString()

        when (val result = authDatabase.createUser(username, password)) {
            AuthResult.Success -> {
                showMessage("Usuario local creado. Ya puedes entrar.", isError = false)
                Toast.makeText(activityContext, "Usuario creado", Toast.LENGTH_SHORT).show()
            }
            is AuthResult.Invalid -> showMessage(result.message, isError = true)
        }
    }

    private fun showMessage(message: String, isError: Boolean) {
        textLoginMessage.text = message
        textLoginMessage.setTextColor(super.getColor(if (isError) R.color.danger else R.color.success))
    }

    private val activityContext: Context
        get() = this
}
