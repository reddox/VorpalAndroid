package com.reddox.vorpal

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Vector
import android.Manifest


class MainActivity : AppCompatActivity() {

    private lateinit var vorpal: VorpalConnection

    enum class BlocklyState { STOPPED, CONNECTING, RUNNING }

    private var state: BlocklyState = BlocklyState.STOPPED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContentView(R.layout.activity_main)

        val settingsFab: View = findViewById(R.id.settings_fab)
        settingsFab.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }

        val blocklyWebView: WebView = findViewById(R.id.blockly_webview)
        blocklyWebView.webViewClient = WebViewClient()
        blocklyWebView.settings.javaScriptEnabled = true
        blocklyWebView.webChromeClient = object : WebChromeClient() {
            // https://stackoverflow.com/questions/14859970/how-can-i-see-javascript-errors-in-webview-in-an-android-app
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    "WebView", consoleMessage.message() + " -- From line "
                            + consoleMessage.lineNumber() + " of "
                            + consoleMessage.sourceId()
                )
                return super.onConsoleMessage(consoleMessage)
            }
        }
        blocklyWebView.loadUrl("file:///android_asset/VorpalBlockly/index.html")

        blocklyWebView.addJavascriptInterface(this, "Android")

        vorpal = VorpalConnection(this)
        vorpal.connectionLostHandler = {
            Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show()
            blocklyWebView.loadUrl("javascript:stopExecution();")
        }

        val runFab: FloatingActionButton = findViewById(R.id.run_fab)
        runFab.setOnClickListener {
            when (state) {
                BlocklyState.STOPPED -> {
                    runFab.setImageResource(R.drawable.ic_wait)
                    vorpal.connect { success ->
                        if (success) {
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(this)
                            val executionStep = sharedPreferences.getInt("executionStep", 300)
                            val highlight = sharedPreferences.getBoolean("highlight", true)
                            blocklyWebView.loadUrl("javascript:startExecution(${executionStep}, ${highlight});")
                        } else {
                            runFab.setImageResource(R.drawable.ic_play)
                        }
                    }
                }

                BlocklyState.RUNNING -> blocklyWebView.loadUrl("javascript:stopExecution();")
                BlocklyState.CONNECTING -> return@setOnClickListener
            }
        }
    }

    // https://stackoverflow.com/questions/67722950/android-12-new-bluetooth-permissions
    fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                //granted
            } else {
                //deny
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {}
        }

    @JavascriptInterface
    public fun notifyStart() {
        val runFab: FloatingActionButton = findViewById(R.id.run_fab)
        runFab.setImageResource(R.drawable.ic_stop)
        vorpal.run()
        state = BlocklyState.RUNNING
    }

    @JavascriptInterface
    public fun notifyStop() {
        val runFab: FloatingActionButton = findViewById(R.id.run_fab)
        runFab.setImageResource(R.drawable.ic_play)
        state = BlocklyState.STOPPED
        vorpal.stop()
        vorpal.disconnect()
    }

    @JavascriptInterface
    public fun saveWorkspace(state: String) {
        val filename = "workspace"
        this.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(state.toByteArray())
        }
    }

    @JavascriptInterface
    public fun loadWorkspace(): String {
        var workspace = ""
        if ("workspace" in this.fileList()) {
            workspace = this.openFileInput("workspace").bufferedReader().useLines { lines ->
                lines.fold(workspace) { t1, t2 ->
                    "$t1\n$t2"
                }
            }
        }
        return workspace
    }

    // Robot commands
    @JavascriptInterface
    public fun robotBeep(frequency: Int, duration: Int) {
        var cmd: Vector<Byte> = Vector()
        cmd.add('B'.toByte())
        cmd.add((frequency shr 8).toByte())
        cmd.add((frequency shr 0).toByte())
        cmd.add((duration shr 8).toByte())
        cmd.add((duration shr 0).toByte())

        vorpal.queueCommand(cmd)
    }

    @JavascriptInterface
    public fun setMode(mode: String) {
        vorpal.setMode(mode)
    }

    @JavascriptInterface
    public fun setDpad(dpad: String) {
        vorpal.setDpad(dpad)
    }
}