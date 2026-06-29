package com.appremote.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appremote.remotecontrol.databinding.ActivityClientBinding
import com.appremote.remotecontrol.databinding.ItemDeviceSlotBinding
import com.appremote.remotecontrol.model.DeviceConfig
import com.appremote.remotecontrol.model.DeviceState
import com.appremote.remotecontrol.network.CommandProtocol
import com.appremote.remotecontrol.network.MultiDeviceManager
import com.appremote.remotecontrol.network.ShoppingSites
import com.appremote.remotecontrol.util.AppPreferences
import com.appremote.remotecontrol.util.DeviceControlBridge

class ClientActivity : AppCompatActivity(), MultiDeviceManager.Listener {

    private lateinit var binding: ActivityClientBinding
    private var deviceManager: MultiDeviceManager? = null
    private var useInternet = true
    private var deviceCount = AppPreferences.MAX_DEVICES
    private val deviceSlots = mutableListOf<DeviceSlotHolder>()
    private var cachedRelayUrl = ""
    private var cachedPort = CommandProtocol.DEFAULT_PORT
    private var cachedUseInternet = true

    private val slotCallbacks = object : DeviceSlotCallbacks {
        override fun onConnectOne(index: Int) = connectSingle(index)
        override fun onDisconnectOne(index: Int) = disconnectSingle(index)
        override fun onCommandOne(index: Int, command: String) = sendCommandToDevice(index, command)
        override fun onViewScreen(index: Int) = openScreenControl(index)
        override fun onSelectionChanged() = updateSummary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.client_mode)

        useInternet = AppPreferences.getConnectionMode(this) == AppPreferences.MODE_INTERNET
        deviceCount = AppPreferences.getDeviceCount(this)
        binding.rbInternet.isChecked = useInternet
        binding.rbLan.isChecked = !useInternet
        binding.etRelayUrl.setText(AppPreferences.getRelayUrl(this))

        setupDeviceCountSpinner()
        setupDeviceSlots()
        updateModeUi()
        applyDeviceCount(deviceCount)
        recreateDeviceManager()

        binding.rgConnectionMode.setOnCheckedChangeListener { _: RadioGroup, checkedId ->
            deviceManager?.disconnectAll()
            useInternet = checkedId == R.id.rbInternet
            AppPreferences.setConnectionMode(
                this,
                if (useInternet) AppPreferences.MODE_INTERNET else AppPreferences.MODE_LAN
            )
            updateModeUi()
            recreateDeviceManager()
            updateSummary()
        }

        binding.btnSelectAll.setOnClickListener { setAllSelected(true) }
        binding.btnSelectNone.setOnClickListener { setAllSelected(false) }
        binding.btnConnectAll.setOnClickListener { connectAll() }
        binding.btnDisconnectAll.setOnClickListener {
            deviceManager?.disconnectAll()
            updateSummary()
        }

        binding.btnShopee.setOnClickListener { sendOpenBrowser(ShoppingSites.SHOPEE) }
        binding.btnLazada.setOnClickListener { sendOpenBrowser(ShoppingSites.LAZADA) }
        binding.btnTiki.setOnClickListener { sendOpenBrowser(ShoppingSites.TIKI) }
        binding.btnSendo.setOnClickListener { sendOpenBrowser(ShoppingSites.SENDO) }

        binding.btnOpenCustomUrl.setOnClickListener {
            val url = binding.etCustomUrl.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendOpenBrowser(url)
        }

        binding.btnHome.setOnClickListener {
            sendCommandToSelected(CommandProtocol.globalAction(CommandProtocol.ACTION_HOME))
        }
        binding.btnBack.setOnClickListener {
            sendCommandToSelected(CommandProtocol.globalAction(CommandProtocol.ACTION_BACK))
        }
        binding.btnRecents.setOnClickListener {
            sendCommandToSelected(CommandProtocol.globalAction(CommandProtocol.ACTION_RECENTS))
        }
        binding.btnScrollDown.setOnClickListener {
            sendCommandToSelected(CommandProtocol.scroll(CommandProtocol.SCROLL_DOWN))
        }
        binding.btnScrollUp.setOnClickListener {
            sendCommandToSelected(CommandProtocol.scroll(CommandProtocol.SCROLL_UP))
        }

        setBatchControlsEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        DeviceControlBridge.deviceManager = deviceManager
    }

    override fun onPause() {
        saveDeviceConfigs()
        AppPreferences.setDeviceCount(this, deviceCount)
        super.onPause()
    }

    override fun onDestroy() {
        DeviceControlBridge.deviceManager = null
        deviceManager?.disconnectAll()
        super.onDestroy()
    }

    private fun setupDeviceCountSpinner() {
        val options = (1..AppPreferences.MAX_DEVICES).map { it.toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerDeviceCount.adapter = adapter
        binding.spinnerDeviceCount.setSelection(deviceCount - 1)
        binding.spinnerDeviceCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newCount = position + 1
                if (newCount != deviceCount) {
                    applyDeviceCount(newCount)
                    deviceCount = newCount
                    AppPreferences.setDeviceCount(this@ClientActivity, deviceCount)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDeviceSlots() {
        val saved = AppPreferences.loadDevices(this)
        val inflater = LayoutInflater.from(this)
        saved.forEach { config ->
            val slotBinding = ItemDeviceSlotBinding.inflate(inflater, binding.deviceSlotsContainer, true)
            val holder = DeviceSlotHolder(config.index, slotBinding, slotCallbacks)
            holder.bind(config)
            deviceSlots.add(holder)
        }
    }

    private fun applyDeviceCount(count: Int) {
        deviceSlots.forEach { holder ->
            val visible = holder.index < count
            holder.setVisible(visible)
            if (!visible) {
                deviceManager?.disconnectDevice(holder.index)
            }
        }
        updateSummary()
    }

    private fun setAllSelected(selected: Boolean) {
        visibleSlots().forEach { it.setSelected(selected) }
        updateSummary()
    }

    private fun visibleSlots(): List<DeviceSlotHolder> =
        deviceSlots.filter { it.index < deviceCount }

    private fun recreateDeviceManager() {
        deviceManager?.disconnectAll()
        deviceManager = buildDeviceManager()
    }

    private fun buildDeviceManager(): MultiDeviceManager {
        val relayUrl = binding.etRelayUrl.text?.toString()?.trim().orEmpty()
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: CommandProtocol.DEFAULT_PORT
        cachedRelayUrl = relayUrl
        cachedPort = port
        cachedUseInternet = useInternet
        return MultiDeviceManager(
            useInternet = useInternet,
            relayUrl = relayUrl,
            lanPort = port,
            listener = this
        )
    }

    private fun ensureDeviceManager(): MultiDeviceManager {
        val relayUrl = binding.etRelayUrl.text?.toString()?.trim().orEmpty()
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: CommandProtocol.DEFAULT_PORT
        val needsRecreate = deviceManager == null ||
            relayUrl != cachedRelayUrl ||
            port != cachedPort ||
            useInternet != cachedUseInternet

        if (needsRecreate) {
            deviceManager?.disconnectAll()
            deviceManager = buildDeviceManager()
        }
        return deviceManager!!
    }

    private fun updateModeUi() {
        binding.tilRelayUrl.visibility = if (useInternet) View.VISIBLE else View.GONE
        binding.tilLanPort.visibility = if (useInternet) View.GONE else View.VISIBLE
        deviceSlots.forEach { it.updateMode(useInternet) }
    }

    private fun collectConfigs(): List<DeviceConfig> =
        deviceSlots.map { it.toConfig() }

    private fun connectAll() {
        if (!validateConnectionSettings()) return
        val configs = visibleSlots()
            .map { it.toConfig() }
            .filter { it.isConfigured(useInternet) }
        if (configs.isEmpty()) {
            Toast.makeText(this, R.string.no_device_configured, Toast.LENGTH_SHORT).show()
            return
        }
        saveDeviceConfigs()
        recreateDeviceManager()
        binding.btnConnectAll.isEnabled = false
        deviceManager?.connectAll(configs)
        binding.btnConnectAll.postDelayed({ binding.btnConnectAll.isEnabled = true }, 2000)
    }

    private fun validateConnectionSettings(): Boolean {
        if (useInternet) {
            val relayUrl = binding.etRelayUrl.text?.toString()?.trim().orEmpty()
            if (relayUrl.isEmpty()) {
                Toast.makeText(this, R.string.relay_url_required, Toast.LENGTH_SHORT).show()
                return false
            }
            AppPreferences.setRelayUrl(this, relayUrl)
        }
        return true
    }

    private fun connectSingle(index: Int) {
        if (!validateConnectionSettings()) return
        val config = deviceSlots.getOrNull(index)?.toConfig() ?: return
        if (!config.isConfigured(useInternet)) {
            Toast.makeText(this, R.string.no_device_configured, Toast.LENGTH_SHORT).show()
            return
        }
        saveDeviceConfigs()
        ensureDeviceManager().connectDevice(config)
    }

    private fun disconnectSingle(index: Int) {
        deviceManager?.disconnectDevice(index)
    }

    private fun saveDeviceConfigs() {
        AppPreferences.saveDevices(this, collectConfigs())
    }

    private fun getSelectedPairedIndices(): List<Int> =
        visibleSlots()
            .filter { it.isSelected() && deviceManager?.isPaired(it.index) == true }
            .map { it.index }

    override fun onDeviceStateChanged(index: Int, state: DeviceState) {
        runOnUiThread {
            deviceSlots.getOrNull(index)?.updateStatus(state)
            if (state == DeviceState.PAIRED) {
                deviceSlots.getOrNull(index)?.setSelected(true)
            }
            updateSummary()
        }
    }

    override fun onSummaryChanged(pairedCount: Int, totalConfigured: Int) {
        runOnUiThread { updateSummary() }
    }

    private fun updateSummary() {
        val paired = deviceManager?.getPairedCount() ?: 0
        val selected = visibleSlots().count { it.isSelected() }
        if (paired > 0) {
            binding.tvConnectionStatus.text = getString(
                R.string.selected_summary, selected, paired, deviceCount
            )
            binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
            setBatchControlsEnabled(getSelectedPairedIndices().isNotEmpty())
        } else {
            binding.tvConnectionStatus.text = getString(R.string.disconnected)
            binding.tvConnectionStatus.setTextColor(getColor(R.color.error))
            setBatchControlsEnabled(false)
        }
        visibleSlots().forEach { holder ->
            holder.setSingleControlsEnabled(deviceManager?.isPaired(holder.index) == true)
        }
    }

    private fun setBatchControlsEnabled(enabled: Boolean) {
        listOf(
            binding.btnShopee, binding.btnLazada, binding.btnTiki, binding.btnSendo,
            binding.btnOpenCustomUrl, binding.btnHome, binding.btnBack, binding.btnRecents,
            binding.btnScrollDown, binding.btnScrollUp, binding.etCustomUrl
        ).forEach { it.isEnabled = enabled }
    }

    private fun sendOpenBrowser(url: String) {
        sendCommandToSelected(CommandProtocol.openBrowser(url))
    }

    private fun sendCommandToSelected(command: String) {
        val indices = getSelectedPairedIndices()
        if (indices.isEmpty()) {
            Toast.makeText(this, R.string.no_device_selected, Toast.LENGTH_SHORT).show()
            return
        }
        deviceManager?.sendToIndices(indices, command) { sent, total ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.command_sent_summary, sent, total),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun sendCommandToDevice(index: Int, command: String) {
        if (deviceManager?.isPaired(index) != true) {
            Toast.makeText(this, R.string.device_not_connected, Toast.LENGTH_SHORT).show()
            return
        }
        val name = deviceSlots.getOrNull(index)?.getDisplayName() ?: "Máy ${index + 1}"
        deviceManager?.sendToDevice(index, command) { success ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (success) getString(R.string.command_sent_one, name)
                    else getString(R.string.command_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openScreenControl(index: Int) {
        if (deviceManager?.isPaired(index) != true) {
            Toast.makeText(this, R.string.device_not_connected, Toast.LENGTH_SHORT).show()
            return
        }
        DeviceControlBridge.deviceManager = ensureDeviceManager()
        val name = deviceSlots.getOrNull(index)?.getDisplayName() ?: "Máy ${index + 1}"
        startActivity(Intent(this, ScreenControlActivity::class.java).apply {
            putExtra(ScreenControlActivity.EXTRA_DEVICE_INDEX, index)
            putExtra(ScreenControlActivity.EXTRA_DEVICE_NAME, name)
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private interface DeviceSlotCallbacks {
        fun onConnectOne(index: Int)
        fun onDisconnectOne(index: Int)
        fun onCommandOne(index: Int, command: String)
        fun onViewScreen(index: Int)
        fun onSelectionChanged()
    }

    private class DeviceSlotHolder(
        val index: Int,
        private val binding: ItemDeviceSlotBinding,
        private val callbacks: DeviceSlotCallbacks
    ) {
        init {
            binding.tvDeviceLabel.text = binding.root.context.getString(R.string.device_slot_label, index + 1)

            binding.cbSelected.setOnCheckedChangeListener { _, _ ->
                callbacks.onSelectionChanged()
            }

            binding.btnConnectOne.setOnClickListener { callbacks.onConnectOne(index) }
            binding.btnDisconnectOne.setOnClickListener { callbacks.onDisconnectOne(index) }

            binding.btnShopeeOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.openBrowser(ShoppingSites.SHOPEE))
            }
            binding.btnLazadaOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.openBrowser(ShoppingSites.LAZADA))
            }
            binding.btnBackOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.globalAction(CommandProtocol.ACTION_BACK))
            }
            binding.btnHomeOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.globalAction(CommandProtocol.ACTION_HOME))
            }
            binding.btnScrollDownOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.scroll(CommandProtocol.SCROLL_DOWN))
            }
            binding.btnScrollUpOne.setOnClickListener {
                callbacks.onCommandOne(index, CommandProtocol.scroll(CommandProtocol.SCROLL_UP))
            }
            binding.btnViewScreen.setOnClickListener { callbacks.onViewScreen(index) }
        }

        fun bind(config: DeviceConfig) {
            binding.etDeviceName.setText(config.name)
            binding.etRoomCode.setText(config.roomCode)
            binding.etLanIp.setText(config.lanIp)
            binding.cbSelected.isChecked = config.selected
            updateStatus(DeviceState.DISCONNECTED)
            setSingleControlsEnabled(false)
        }

        fun setVisible(visible: Boolean) {
            binding.root.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun setSelected(selected: Boolean) {
            binding.cbSelected.isChecked = selected
        }

        fun isSelected(): Boolean = binding.cbSelected.isChecked

        fun getDisplayName(): String =
            binding.etDeviceName.text?.toString()?.trim().orEmpty().ifEmpty { "Máy ${index + 1}" }

        fun updateMode(useInternet: Boolean) {
            binding.tilRoomCode.visibility = if (useInternet) View.VISIBLE else View.GONE
            binding.tilLanIp.visibility = if (useInternet) View.GONE else View.VISIBLE
        }

        fun setSingleControlsEnabled(enabled: Boolean) {
            listOf(
                binding.btnViewScreen,
                binding.btnShopeeOne, binding.btnLazadaOne, binding.btnBackOne,
                binding.btnHomeOne, binding.btnScrollDownOne, binding.btnScrollUpOne
            ).forEach { it.isEnabled = enabled }
        }

        fun updateStatus(state: DeviceState) {
            val context = binding.root.context
            val statusView: TextView = binding.tvDeviceStatus
            when (state) {
                DeviceState.PAIRED -> {
                    statusView.text = context.getString(R.string.device_online)
                    statusView.setTextColor(context.getColor(R.color.success))
                }
                DeviceState.WAITING -> {
                    statusView.text = context.getString(R.string.device_waiting)
                    statusView.setTextColor(context.getColor(R.color.accent))
                }
                DeviceState.CONNECTING -> {
                    statusView.text = context.getString(R.string.device_connecting)
                    statusView.setTextColor(context.getColor(R.color.text_secondary))
                }
                DeviceState.ERROR -> {
                    statusView.text = context.getString(R.string.device_error)
                    statusView.setTextColor(context.getColor(R.color.error))
                }
                DeviceState.DISCONNECTED -> {
                    statusView.text = context.getString(R.string.device_offline)
                    statusView.setTextColor(context.getColor(R.color.text_secondary))
                }
            }
        }

        fun toConfig(): DeviceConfig = DeviceConfig(
            index = index,
            name = getDisplayName(),
            roomCode = binding.etRoomCode.text?.toString()?.trim().orEmpty(),
            lanIp = binding.etLanIp.text?.toString()?.trim().orEmpty(),
            selected = binding.cbSelected.isChecked
        )
    }

    private fun DeviceConfig.isConfigured(useInternet: Boolean): Boolean =
        if (useInternet) roomCode.length == 6 else lanIp.isNotBlank()
}
