package com.example.examplewvapp20

import android.content.res.AssetManager
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException

// ── Fluid Resolution System ──────────────────────────────────────────────────

sealed class ShebangResult {
    data class AssetUrl(val url: String, val isLegacy: Boolean, val warningsDisabled: Boolean, val assetManifest: List<String>) : ShebangResult()
    data class Error(val code: ErrorCode, val detail: String? = null, val assetManifest: List<String>) : ShebangResult()
}

enum class ErrorCode(val code: String, val title: String, val message: String) {
    NO_PRIME_SHEBANG("001", "ENTRY POINT MISSING", "No Fluid ID or StatiX Prime Shebang found in manifest."),
    ASSET_IO_ERROR("003", "FS READ FAILURE", "Critical I/O error during container scan."),
    RENDER_CRASH("004", "WEBVIEW PANIC", "Internal engine reported a fatal frame failure.")
}

// ── Asset Logic ──────────────────────────────────────────────────────────────

fun resolveEntryPoint(assetManager: AssetManager): ShebangResult {
    val allAssets = try { assetManager.list("")?.toList() ?: emptyList() } catch (e: IOException) { return ShebangResult.Error(ErrorCode.ASSET_IO_ERROR, e.message, emptyList()) }
    val htmlFiles = allAssets.filter { it.endsWith(".html") }
    var (fluid, legacy) = Pair<String?, String?>(null, null)
    var warningsDisabled = false

    for (fileName in htmlFiles) {
        try {
            val content = assetManager.open(fileName).bufferedReader().use { it.readText() }
            if (content.contains("id=\"disablewarnings\"")) warningsDisabled = true
            if (content.contains("class=\"id1\"")) fluid = fileName
        } catch (e: Exception) { continue }
        if (fileName.contains("_$1.html")) legacy = fileName
    }

    return when {
        fluid != null -> ShebangResult.AssetUrl("file:///android_asset/$fluid", false, warningsDisabled, allAssets)
        legacy != null -> ShebangResult.AssetUrl("file:///android_asset/$legacy", true, warningsDisabled, allAssets)
        else -> ShebangResult.Error(ErrorCode.NO_PRIME_SHEBANG, null, allAssets)
    }
}

// ── Unified Terminal UI (Pulsing Red Bevel) ──────────────────────────────────

fun buildTerminalPage(title: String, description: String, fix: String, assets: List<String>): String {
    val listItems = assets.joinToString("") { "<li>$it</li>" }
    return """
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <style>
            @keyframes pulse { 0% { box-shadow: inset 0 0 30px #800; } 50% { box-shadow: inset 0 0 60px #f00; } 100% { box-shadow: inset 0 0 30px #800; } }
            html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; color: #fff; font-family: monospace; }
            .terminal-wrapper { 
                position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                display: flex; flex-direction: column; align-items: center; 
                padding: 40px 20px; box-sizing: border-box;
                animation: pulse 2s infinite ease-in-out;
            }
            .tag { color: #f00; font-weight: bold; margin-top: 15px; text-transform: uppercase; letter-spacing: 1px; }
            .content { margin-bottom: 10px; font-size: 13px; max-width: 450px; line-height: 1.4; color: #ccc; text-align: center; }
            #face { font-size: 60px; font-weight: bold; margin: 20px 0; height: 70px; color: #f00; text-shadow: 0 0 15px #f00; }
            details { width: 100%; max-width: 350px; border: 1px solid #333; border-radius: 8px; padding: 10px; margin-top: 20px; background: #0a0a0a; }
            summary { font-weight: bold; cursor: pointer; color: #666; font-size: 11px; }
            ul { font-size: 12px; padding-left: 20px; color: #444; margin-top: 10px; }
            .footer { margin-top: auto; font-size: 12px; font-weight: 900; letter-spacing: 4px; color: #f00; padding-bottom: 20px; text-shadow: 0 0 5px #f00; }
        </style>
    </head>
    <body>
        <div class="terminal-wrapper">
            <div class="tag">What whopsie did you do?</div>
            <div class="content">$title</div>
            <div class="tag">GIVE ME MORE INFO!!</div>
            <div class="content">$description</div>
            <div class="tag">So how I fix it?</div>
            <div class="content">$fix</div>
            <div id="face">OwO</div>
            <details>
                <summary>See contents of assets/ </summary>
                <ul>$listItems</ul>
            </details>
            <div class="footer">B0MK CORE V3 // BY BRICKBOSS</div>
        </div>
        <script>
            const face = document.getElementById('face');
            const frames = ['>_<', "X_X"];
            let i = 0; setInterval(() => { face.innerText = frames[i % frames.length]; i++; }, 800);
        </script>
    </body>
    </html>
    """.trimIndent()
}

// ── WebView Implementation ────────────────────────────────────────────────────

@Composable
fun WebViewScreen(result: ShebangResult) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                        if (result is ShebangResult.AssetUrl && result.isLegacy && !result.warningsDisabled) {
                            val warningHtml = buildTerminalPage("LEGACY_NAMING_SCHEME", "B0MK CORE operating in legacy mode. Performance is throttled.", "Rename file to standard; add class='id1' for ignition.", result.assetManifest)
                            val encoded = Base64.encodeToString(warningHtml.toByteArray(), Base64.NO_WRAP)
                            view?.evaluateJavascript("document.open(); document.write(atob('$encoded')); document.close();", null)
                        }
                    }
                }
                when (result) {
                    is ShebangResult.AssetUrl -> loadUrl(result.url)
                    is ShebangResult.Error -> loadDataWithBaseURL(null, buildTerminalPage(result.code.title, result.code.message, "Check manifest; 'id1' marker missing.", result.assetManifest), "text/html", "UTF-8", null)
                }
            }
        }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = resolveEntryPoint(assets)
        setContent { WebViewScreen(result) }
    }
}