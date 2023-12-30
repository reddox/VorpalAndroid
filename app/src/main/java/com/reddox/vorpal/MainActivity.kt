package com.reddox.vorpal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Vector

class MainActivity : AppCompatActivity() {

    private lateinit var vorpal : VorpalConnection
    enum class BlocklyState { STOPPED, CONNECTING, RUNNING}
    private var state : BlocklyState = BlocklyState.STOPPED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settingsFab: View = findViewById(R.id.settings_fab)
        settingsFab.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }

        val blocklyWebView: WebView = findViewById(R.id.blockly_webview)
        blocklyWebView.webViewClient = WebViewClient()
        blocklyWebView.settings.javaScriptEnabled = true
        blocklyWebView.webChromeClient = WebChromeClient()
        blocklyWebView.loadUrl("file:///android_asset/VorpalBlockly/index.html")

        blocklyWebView.addJavascriptInterface(this, "Android")

        vorpal = VorpalConnection(this)

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

    @JavascriptInterface
    public fun notifyStart() {
        val runFab: FloatingActionButton = findViewById(R.id.run_fab)
        runFab.setImageResource(R.drawable.ic_stop)
        state = BlocklyState.RUNNING
    }

    @JavascriptInterface
    public fun notifyStop() {
        val runFab: FloatingActionButton = findViewById(R.id.run_fab)
        runFab.setImageResource(R.drawable.ic_play)
        state = BlocklyState.STOPPED
        vorpal.disconnect()
    }

    @JavascriptInterface
    public fun robotBeep(frequency : Int, duration: Int) {
        var cmd : Vector<Byte> = Vector()
        cmd.add('B'.toByte())
        cmd.add((frequency shr 8).toByte())
        cmd.add((frequency shr 0).toByte())
        cmd.add((duration shr 8).toByte())
        cmd.add((duration shr 0).toByte())

        vorpal.sendCommand(5, cmd)
    }
}