package com.appremote.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appremote.remotecontrol.databinding.ActivityServerBinding
import com.appremote.remotecontrol.network.RelayProtocol
import com.appremote.remotecontrol.service.RemoteServerService
import com.appremote.remotecontrol.util.AppPreferences
import com.appremote.remotecontrol.util.DeviceUtils

class ServerActivity : AppCompatActivity(), RemoteServerService.ConnectionListener {

    private lateinit var binding: ActivityServerBinding
    private var isServerRunning = false
    private var useInternet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.server_mode)

        useInternet = AppPreferences.getConnectionMode(this) == AppPreferences.MODE_INTERNET
        binding.rbInternet.isChecked = useInternet
        binding.rbLan.isChecked = !useInternet
        updateModeUi()

        binding.etRelayUrl.setText(AppPreferences.getRelayUrl(this))
        if (binding.etRoomCode.text.isNullOrBlank()) {
            binding.etRoomCode.setText(RelayProtocol.generateRoomCode())
        }

        binding.rgConnectionMode.setOnCheckedChangeListener { _: RadioGroup, checkedId ->
            useInternet = checkedId == R.id.rbInternet
            AppPreferences.setConnectionMode(
                this,
                if (useInternet) AppPreferences.MODE_INTERNET else AppPreferences.MODE_LAN
            )
            updateModeUi()
        }

        binding.btnGenerateCode.setOnClickListener {
            binding.etRoomCode.setText(RelayProtocol.generateRoomCode())
        }

        updateIpDisplay()
        updateAccessibilityStatus()

        binding.btnEnableAccessibility.setOnClickListener {
            DeviceUtils.openAccessibilitySettings(this)
        }

        binding.btnStartStop.setOnClickListener {
            if (isServerRunning) stopServer() else startServer()
        }
    }

    override fun onResume() {
        super.onResume()
        RemoteServerService.connectionListener = this
        updateAccessibilityStatus()
        updateIpDisplay()
    }

    override fun onPause() {
        RemoteServerService.connectionListener = null
        super.onPause()
    }

    private fun updateModeUi() {
        binding.layoutInternet.visibility = if (useInternet) View.VISIBLE else View.GONE
        binding.layoutLan.visibility = if (useInternet) View.GONE else View.VISIBLE
    }

    private fun updateIpDisplay() {
        val ip = DeviceUtils.getLocalIpAddress()
        binding.tvDeviceIp.text = if (ip != null) {
            getString(R.string.device_ip, ip)
        } else {
            getString(R.string.no_wifi_ip)
        }
    }

    private fun updateAccessibilityStatus() {
        val enabled = DeviceUtils.isAccessibilityServiceEnabled(this)
        binding.btnEnableAccessibility.visibility =
            if (enabled) View.GONE else View.VISIBLE
    }

    private fun startServer() {
        if (!DeviceUtils.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, R.string.accessibility_required, Toast.LENGTH_LONG).show()
            DeviceUtils.openAccessibilitySettings(this)
            return
        }

        val intent = Intent(this, RemoteServerService::class.java)

        if (useInternet) {
            val relayUrl = binding.etRelayUrl.text?.toString()?.trim().orEmpty()
            val roomCode = binding.etRoomCode.text?.toString()?.trim().orEmpty()

            if (relayUrl.isEmpty()) {
                Toast.makeText(this, R.string.relay_url_required, Toast.LENGTH_SHORT).show()
                return
            }
            if (roomCode.length != 6) {
                Toast.makeText(this, R.string.room_code_invalid, Toast.LENGTH_SHORT).show()
                return
            }

            AppPreferences.setRelayUrl(this, relayUrl)
            intent.putExtra(RemoteServerService.EXTRA_USE_RELAY, true)
            intent.putExtra(RemoteServerService.EXTRA_RELAY_URL, relayUrl)
            intent.putExtra(RemoteServerService.EXTRA_ROOM_CODE, roomCode)
        } else {
            intent.putExtra(RemoteServerService.EXTRA_USE_RELAY, false)
        }

        ContextCompat.startForegroundService(this, intent)
        isServerRunning = true
        binding.btnStartStop.text = getString(R.string.stop_server)

        if (useInternet) {
            binding.tvStatus.text = getString(R.string.server_connecting)
            binding.tvStatus.setTextColor(getColor(R.color.text_secondary))
        } else {
            binding.tvStatus.text = getString(R.string.server_running)
            binding.tvStatus.setTextColor(getColor(R.color.success))
        }
    }

    private fun stopServer() {
        val intent = Intent(this, RemoteServerService::class.java).apply {
            action = RemoteServerService.ACTION_STOP
        }
        startService(intent)
        isServerRunning = false
        binding.btnStartStop.text = getString(R.string.start_server)
        binding.tvStatus.text = getString(R.string.server_stopped)
        binding.tvStatus.setTextColor(getColor(R.color.text_secondary))
    }

    override fun onRegistered(roomCode: String) {
        runOnUiThread {
            binding.tvStatus.text = getString(R.string.server_waiting_peer, roomCode)
            binding.tvStatus.setTextColor(getColor(R.color.success))
            binding.tvRoomInfo.text = getString(R.string.share_room_code, roomCode)
        }
    }

    override fun onClientConnected() {
        runOnUiThread {
            binding.tvStatus.text = getString(R.string.client_connected)
            binding.tvStatus.setTextColor(getColor(R.color.success))
        }
    }

    override fun onClientDisconnected() {
        runOnUiThread {
            if (isServerRunning) {
                val roomCode = binding.etRoomCode.text?.toString().orEmpty()
                binding.tvStatus.text = getString(R.string.server_waiting_peer, roomCode)
            } else {
                binding.tvStatus.text = getString(R.string.server_stopped)
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            if (isServerRunning) {
                stopServer()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
