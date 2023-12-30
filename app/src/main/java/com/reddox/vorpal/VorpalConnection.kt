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
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import java.util.UUID
import java.util.Vector
import java.util.concurrent.Executors


class VorpalConnection(context: Context) {
    val context = context

    companion object {
        var serialUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var btSocket: BluetoothSocket? = null
        var isConnected: Boolean = false
    }


    fun connect(callback : ((Boolean) -> Unit)? = null) {
        Toast.makeText(context, "connecting...", Toast.LENGTH_SHORT).show()

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            val result = connectInternal()
            handler.post {
                Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                if(callback!=null) {
                    callback(result.first)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal() : Pair<Boolean, String> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hexapod = sharedPreferences.getString("hexapod", "")
        val regex = Regex(".+\\(([0-9A-F:]+)\\)$")
        val match = regex.matchEntire(hexapod as CharSequence)
        if (match == null) {
            return Pair(false, "$hexapod is not set correctly")
        }

        val address = match.groups[1]!!.value


        val btManager: BluetoothManager? = context.getSystemService<BluetoothManager>()
        if (match == null) {
            return Pair(false, "No Bluetooth available")
        }
        val btAdapter: BluetoothAdapter = btManager!!.adapter


        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableBtIntent)
        }

        val device: BluetoothDevice = btAdapter.getRemoteDevice(address)
        btAdapter.cancelDiscovery()
        btSocket = device.createInsecureRfcommSocketToServiceRecord(serialUUID)
        if(btSocket == null) {
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
        if(btSocket == null) {
            return
        }
        btSocket!!.close()
        btSocket=null
        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun sendCommandRaw(cmd : ByteArray) {
        if(btSocket == null || !btSocket!!.isConnected) {
            return
        }
        btSocket!!.outputStream.write(cmd)
    }

    fun sendCommand(len: Byte, cmd : Vector<Byte>) {
        val HEADER_SIZE = 4
        var checksum : Byte = len
        val buffer: ByteArray = ByteArray(len + HEADER_SIZE)
        if(cmd.size > len) {
            // TODO: fail silently...for now
            return
        }
        var bufpos : Int = 0
        buffer[bufpos++] = 'V'.toByte()
        buffer[bufpos++] = '1'.toByte()
        buffer[bufpos++] = len

        for(b in cmd) {
            buffer[bufpos++] = b
            checksum = (checksum + b).toByte()
        }

        while(bufpos+HEADER_SIZE < len) {
            buffer[bufpos++] = 0
        }

        buffer[bufpos] = checksum
        sendCommandRaw(buffer)
    }
}