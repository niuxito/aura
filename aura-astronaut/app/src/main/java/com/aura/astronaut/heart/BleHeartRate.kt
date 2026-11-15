package com.aura.astronaut.heart

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Frecuencia cardiaca del reloj por Bluetooth LE, perfil estándar Heart Rate (sin SDK de Garmin ni ANT+):
 * servicio 0x180D, característica 0x2A37 por notificaciones. Busca solo dispositivos que anuncian 0x180D,
 * guarda el elegido y reconecta solo al arrancar y tras una desconexión. Funciona en modo avión con el Bluetooth activo.
 */
@SuppressLint("MissingPermission") // Cada llamada protegida va precedida de hasPermissions().
class BleHeartRate(context: Context, private val onDeviceChosen: (String?) -> Unit) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(HeartState(source = HeartSource.WATCH, status = WatchStatus.OFF))
    val state: StateFlow<HeartState> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<FoundWatch>>(emptyList())
    val found: StateFlow<List<FoundWatch>> = _found.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var wanted: String? = null
    private var running = false
    private var scanning = false

    val available: Boolean get() = adapter != null && appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Arranca con el reloj guardado (si lo hay): conecta y reconecta solo. */
    fun start(savedAddress: String?) {
        running = true
        wanted = savedAddress
        refresh()
    }

    fun stop() {
        running = false
        stopScan()
        closeGatt()
        _state.value = HeartState(source = HeartSource.WATCH, status = WatchStatus.OFF)
    }

    /** Vuelve a evaluar permisos y Bluetooth (por ejemplo, al volver de los ajustes). */
    fun refresh() {
        if (!running) return
        val status = when {
            !available -> WatchStatus.UNAVAILABLE
            !hasPermissions() -> WatchStatus.NO_PERMISSION
            adapter?.isEnabled != true -> WatchStatus.BLUETOOTH_OFF
            wanted == null -> if (scanning) WatchStatus.SCANNING else WatchStatus.NOT_PAIRED
            else -> null
        }
        if (status != null) {
            setStatus(status)
            return
        }
        if (gatt == null) connect(wanted!!)
    }

    fun startScan() {
        if (!running || !available || !hasPermissions() || adapter?.isEnabled != true || scanning) {
            refresh()
            return
        }
        _found.value = emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        scanning = true
        if (wanted == null) setStatus(WatchStatus.SCANNING)
        main.postDelayed({ stopScan() }, HeartRules.SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        if (hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (wanted == null && running) refresh()
    }

    /** El astronauta elige su reloj: se guarda y se conecta. */
    fun choose(address: String) {
        stopScan()
        closeGatt()
        wanted = address
        onDeviceChosen(address)
        refresh()
    }

    fun forget() {
        closeGatt()
        wanted = null
        onDeviceChosen(null)
        refresh()
    }

    private fun connect(address: String) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        setStatus(WatchStatus.CONNECTING, device.name)
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun closeGatt() {
        main.removeCallbacksAndMessages(RECONNECT_TOKEN)
        runCatching { gatt?.close() }
        gatt = null
    }

    private fun scheduleReconnect() {
        closeGatt()
        if (!running || wanted == null) return
        main.postAtTime({ refresh() }, RECONNECT_TOKEN, android.os.SystemClock.uptimeMillis() + HeartRules.RECONNECT_DELAY_MS)
    }

    private fun setStatus(status: WatchStatus, name: String? = _state.value.deviceName) {
        _state.update { it.copy(source = HeartSource.WATCH, status = status, deviceName = name) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = runCatching { d.name }.getOrNull() ?: result.scanRecord?.deviceName ?: d.address
            _found.update { list ->
                (list.filterNot { it.address == d.address } + FoundWatch(d.address, name, result.rssi)).sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Búsqueda fallida ($errorCode)")
            scanning = false
            refresh()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Reloj desconectado ($status); se reintenta")
                    setStatus(WatchStatus.DISCONNECTED)
                    main.post { scheduleReconnect() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (ch == null) {
                Log.w(TAG, "El dispositivo no ofrece Heart Rate Measurement")
                setStatus(WatchStatus.DISCONNECTED)
                return
            }
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CLIENT_CONFIG) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            setStatus(WatchStatus.CONNECTED, runCatching { g.device.name }.getOrNull())
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) = handle(value)

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handle(ch.value ?: return)
        }
    }

    private fun handle(value: ByteArray) {
        val m = HeartRateParser.parse(value) ?: return
        if (m.bpm !in HeartRules.PLAUSIBLE || m.contact == false) return
        _state.update { it.copy(status = WatchStatus.CONNECTED, bpm = m.bpm, receivedAtMs = System.currentTimeMillis()) }
    }

    companion object {
        private const val TAG = "AuraHeart"
        private val RECONNECT_TOKEN = Any()
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Android 12+: buscar y conectar; antes, ubicación para poder buscar. */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
