package com.smartgif.maker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.smartgif.maker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var backPressedTime: Long = 0L

    // File Chooser Activity Result Launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback ?: return@registerForActivityResult
        fileUploadCallback = null

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val uriList = mutableListOf<Uri>()

            val clipData = data.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uriList.add(it) }
                }
            } else {
                data.data?.let { uriList.add(it) }
            }

            if (uriList.isNotEmpty()) {
                callback.onReceiveValue(uriList.toTypedArray())
            } else {
                callback.onReceiveValue(null)
            }
        } else {
            callback.onReceiveValue(null)
        }
    }

    // Permission Request Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                getString(R.string.permission_denied_notice),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyFullscreen()
        checkPermissions()
        setupWebView()
        setupListeners()
        setupBackHandler()

        loadTargetSite()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreen()
        }
    }

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        // Core HTML5 & WebAssembly capabilities
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // File & Media settings
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Viewport & Zoom setup
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Cache & Connectivity
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Mobile User-Agent with app token
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = "$defaultUserAgent SmartGIFApp/1.0"

        // Native JS Bridge
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        // Chrome Client: File Chooser & Progress
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.topProgressBar.progress = newProgress
                if (newProgress >= 100) {
                    binding.topProgressBar.visibility = View.GONE
                } else {
                    binding.topProgressBar.visibility = View.VISIBLE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val isMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val acceptTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    if (isMultiple) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }

                    if (!acceptTypes.isNullOrEmpty()) {
                        if (acceptTypes.size == 1) {
                            type = acceptTypes[0]
                        } else {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                        }
                    } else {
                        type = "*/*"
                    }
                }

                try {
                    fileChooserLauncher.launch(
                        Intent.createChooser(intent, getString(R.string.file_chooser_title))
                    )
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    Toast.makeText(this@MainActivity, "파일 탐색기를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                // Return true to avoid cluttering or handle logs if needed
                return super.onConsoleMessage(consoleMessage)
            }
        }

        // WebView Client: Navigation, Interception & Error handling
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.errorLayout.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobDownloadScript()

                // Smoothly dismiss loading screen once loaded
                binding.loadingLayout.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.loadingLayout.visibility = View.GONE
                    }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.loadingLayout.visibility = View.GONE
                    binding.errorLayout.visibility = View.VISIBLE
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host ?: ""

                // Allow in-app navigation for target site and KMOU university link
                if (host.contains("github.io") || host.contains("kmou.ac.kr")) {
                    return false
                }

                // External browser for other external links
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        // Fallback standard DownloadListener
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // Handled via JavaScript bridge
                return@setDownloadListener
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Injects a client-side hook that intercepts Blob downloads (e.g. from gifenc)
     * and forwards the generated GIF file directly to the native Android bridge.
     */
    private fun injectBlobDownloadScript() {
        val script = """
            (function() {
                if (window.__android_blob_bridge_installed) return;
                window.__android_blob_bridge_installed = true;

                function handleBlobUrl(blobUrl, filename, mimeType) {
                    fetch(blobUrl)
                        .then(function(res) { return res.blob(); })
                        .then(function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                if (window.AndroidBridge && window.AndroidBridge.downloadBase64) {
                                    window.AndroidBridge.downloadBase64(
                                        reader.result,
                                        filename || 'smart_gif.gif',
                                        mimeType || blob.type || 'image/gif'
                                    );
                                }
                            };
                            reader.readAsDataURL(blob);
                        })
                        .catch(function(err) {
                            console.error('Android Blob bridge fetch failed:', err);
                        });
                }

                // Intercept programmatic click() on <a> elements
                var origClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    var href = this.getAttribute('href') || this.href;
                    var download = this.getAttribute('download') || this.download;
                    if (href && href.indexOf('blob:') === 0) {
                        handleBlobUrl(href, download, 'image/gif');
                        return;
                    }
                    return origClick.apply(this, arguments);
                };

                // Intercept direct user clicks on <a> elements
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el.tagName !== 'A') {
                        el = el.parentElement;
                    }
                    if (el && el.tagName === 'A') {
                        var href = el.getAttribute('href') || el.href;
                        var download = el.getAttribute('download') || el.download;
                        if (href && href.indexOf('blob:') === 0) {
                            e.preventDefault();
                            e.stopPropagation();
                            handleBlobUrl(href, download, 'image/gif');
                        }
                    }
                }, true);
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(script, null)
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
            binding.loadingLayout.alpha = 1.0f
            loadTargetSite()
        }
    }

    private fun loadTargetSite() {
        val siteUrl = getString(R.string.site_url)
        binding.webView.loadUrl(siteUrl)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - backPressedTime < 2000) {
                        isEnabled = false
                        finish()
                    } else {
                        backPressedTime = now
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.back_press_exit_prompt),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
