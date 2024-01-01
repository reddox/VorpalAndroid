package com.reddox.vorpal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import java.io.IOException
import java.util.LinkedList
import java.util.UUID
import java.util.Vector
import java.util.concurrent.Executors


class VorpalConnection(context: Context) {
    val context = context
    val handler = Handler(Looper.getMainLooper())
    val executor = Executors.newSingleThreadExecutor()
    private val queuedCommands = LinkedList<Vector<Byte>>()
    var connectionLostHandler: (() -> Unit)? = null

    private var mode = arrayOf<Byte>('W'.toByte(), '1'.toByte())
    private var dpad: Byte = 's'.toByte()

    companion object {
        const val HEADER_SIZE = 4
        const val MAX_PACKET_DATA = 48
        var serialUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var btSocket: BluetoothSocket? = null
        var isConnected: Boolean = false
    }


    fun connect(callback: ((Boolean) -> Unit)? = null) {
        Toast.makeText(context, "connecting...", Toast.LENGTH_SHORT).show()

        executor.execute {
            val result = connectInternal()
            handler.post {
                Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                if (callback != null) {
                    callback(result.first)
                }
            }
        }
    }

    private val bluetoothTask = object : Runnable {
        override fun run() {
            Log.d("Vorpal", "RunTask")
            sendBtData()
            handler.postDelayed(this, 100)
        }
    }

    private fun sendBtData() {
        val cmd = Vector<Byte>()
        cmd.add(mode[0])
        cmd.add(mode[1])
        cmd.add(dpad)

        while (queuedCommands.isNotEmpty() && (cmd.size + queuedCommands.peekFirst()!!.size) < MAX_PACKET_DATA) {
            cmd.addAll(queuedCommands.removeFirst())
        }

        val len: Byte = cmd.size.toByte()

        val buffer: ByteArray = ByteArray(len + HEADER_SIZE)

        var checksum: Byte = len
        var bufPos: Int = 0
        buffer[bufPos++] = 'V'.toByte()
        buffer[bufPos++] = '1'.toByte()
        buffer[bufPos++] = len

        for (b in cmd) {
            buffer[bufPos++] = b
            checksum = (checksum + b).toByte()
        }

        buffer[bufPos] = checksum
        sendBtRaw(buffer)
    }

    fun run() {
        handler.post(bluetoothTask)
    }

    fun stop() {
        handler.removeCallbacks(bluetoothTask)
    }

    private fun connectInternal(): Pair<Boolean, String> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hexapod = sharedPreferences.getString("hexapod", "")
        val regex = Regex(".+\\(([0-9A-F:]+)\\)$")
        val match = regex.matchEntire(hexapod as CharSequence)
            ?: return Pair(false, "Device \"$hexapod\" is not set correctly")

        val address = match.groups[1]!!.value

        val btManager: BluetoothManager = context.getSystemService<BluetoothManager>()
            ?: return Pair(false, "No Bluetooth available")
        val btAdapter: BluetoothAdapter = btManager!!.adapter


        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableBtIntent)
        }

        val device: BluetoothDevice = btAdapter.getRemoteDevice(address)
        btAdapter.cancelDiscovery()
        btSocket = device.createInsecureRfcommSocketToServiceRecord(serialUUID)
        if (btSocket == null) {
            return Pair(false, "Cannot create Bluetooth socket")
        }

        try {
            btSocket!!.connect()
        } catch (e: Exception) {
            return Pair(false, "Cannot connect to $hexapod")
        }
        return Pair(true, "Connected to $hexapod")
    }

    fun disconnect() {
        if (btSocket == null) {
            return
        }
        btSocket!!.close()
        btSocket = null
        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun sendBtRaw(cmd: ByteArray) {
        if (btSocket == null || !btSocket!!.isConnected) {
            handler.post { connectionLostHandler?.invoke() }
            return
        }
        try {
            btSocket!!.outputStream.write(cmd)
        } catch (e: IOException) {
            handler.post { connectionLostHandler?.invoke() }
        }
    }

    fun queueCommand(cmd: Vector<Byte>) {
        queuedCommands.addLast(cmd)
    }

    fun setMode(mode: String) {
        val byteArray = mode.toByteArray(Charsets.US_ASCII)
        if (byteArray.size >= 2) {
            this.mode[0] = byteArray[0]
            this.mode[1] = byteArray[1]
        } else {
            // TODO: fail silently
        }
    }

    fun setDpad(dpad: String) {
        val byteArray = dpad.toByteArray(Charsets.US_ASCII)
        if (byteArray.size >= 1) {
            this.dpad = byteArray[0]
        } else {
            // TODO: fail silently
        }
    }
}