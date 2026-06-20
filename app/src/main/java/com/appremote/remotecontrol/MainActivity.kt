package com.appremote.remotecontrol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appremote.remotecontrol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardServer.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }

        binding.cardClient.setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
    }
}
