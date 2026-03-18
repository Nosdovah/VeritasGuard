package com.veritasguard.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.View.OnTouchListener
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.veritasguard.R
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "veritasguard_overlay_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_SHOW_RESULT = "com.veritasguard.ACTION_SHOW_RESULT"
        const val EXTRA_RESULT_TYPE = "EXTRA_RESULT_TYPE" // safe, danger, warning
        const val EXTRA_RESULT_TITLE = "EXTRA_RESULT_TITLE"
        const val EXTRA_RESULT_MESSAGE = "EXTRA_RESULT_MESSAGE"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var bubbleView: ImageView
    private lateinit var menuView: View
    private lateinit var resultView: View
    private lateinit var params: WindowManager.LayoutParams

    private var isMenuExpanded = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupViews()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_RESULT) {
            val type = intent.getStringExtra(EXTRA_RESULT_TYPE) ?: "info"
            val title = intent.getStringExtra(EXTRA_RESULT_TITLE) ?: "Result"
            val message = intent.getStringExtra(EXTRA_RESULT_MESSAGE) ?: ""
            showResult(type, title, message)
        }
        return START_STICKY
    }

    private fun setupViews() {
        // Root Container
        rootLayout = FrameLayout(this)

        // 1. Create Bubble (Collapsed State)
        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield_floating)
            layoutParams = FrameLayout.LayoutParams(160, 160) // approx 50-60dp
            setPadding(20, 20, 20, 20)
            background = getDrawable(R.drawable.rounded_bg)
            elevation = 10f
        }

        // 2. Inflate Menu (Expanded State)
        menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, rootLayout, false)
        menuView.visibility = View.GONE
        
        // Setup Menu Clicks
        menuView.findViewById<Button>(R.id.btn_scan_phishing).setOnClickListener {
            // Get content from EditText if any
            val input = menuView.findViewById<EditText>(R.id.input_link).text.toString()
            
            val intent = Intent("com.veritasguard.ACTION_SCAN_PHISHING")
            intent.setPackage(packageName)
            intent.putExtra("INPUT_LINK", input)
            sendBroadcast(intent)
            
            collapseMenu() // temporarily collapse while processing
        }
        
        menuView.findViewById<Button>(R.id.btn_verify_fact).setOnClickListener {
             // Get content from EditText if any
            val input = menuView.findViewById<EditText>(R.id.input_link).text.toString()

            val intent = Intent("com.veritasguard.ACTION_VERIFY_FACT")
            intent.setPackage(packageName)
            intent.putExtra("INPUT_LINK", input)
            sendBroadcast(intent)
            collapseMenu()
        }

        menuView.findViewById<Button>(R.id.btn_close_menu).setOnClickListener {
            collapseMenu()
        }

        // 3. Inflate Result View
        resultView = LayoutInflater.from(this).inflate(R.layout.overlay_result, rootLayout, false)
        resultView.visibility = View.GONE
        
        resultView.findViewById<Button>(R.id.btn_close_result).setOnClickListener {
            closeResult()
        }

        // Add views to root
        rootLayout.addView(bubbleView)
        rootLayout.addView(menuView)
        rootLayout.addView(resultView)

        // Window Params
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Touch Listener for Dragging
        bubbleView.setOnTouchListener(object : OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val touchSlop = 10 

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(rootLayout, params)
                        } catch (e: Exception) {
                            // Ignore update errors (e.g. view not attached)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        
                        if (diffX > touchSlop || diffY > touchSlop) {
                            snapToEdge()
                        } else {
                            toggleMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(rootLayout, params)
        } catch (e: Exception) {
           e.printStackTrace()
        }
    }
    
    private fun showResult(type: String, title: String, message: String) {
        // Update UI Logic
        val iconView = resultView.findViewById<TextView>(R.id.result_icon)
        val titleView = resultView.findViewById<TextView>(R.id.result_title)
        val bodyView = resultView.findViewById<TextView>(R.id.result_message)
        val sourceView = resultView.findViewById<TextView>(R.id.result_source)
        val cardBg = resultView.findViewById<LinearLayout>(R.id.result_card)

        bodyView.text = message
        titleView.text = title

        when (type) {
            "safe" -> {
                iconView.text = "✅"
                titleView.setTextColor(Color.WHITE)
                sourceView.text = "Sumber: Verifikasi Valid"
                sourceView.setTextColor(Color.parseColor("#AECC0F"))
                // Optional: Change background tint dynamically if supported or via multiple drawables
            }
            "danger" -> {
                iconView.text = "🚨"
                titleView.setTextColor(Color.parseColor("#EF4444")) // Red
                sourceView.text = "Sumber: Indikasi Hoax/Penipuan"
                sourceView.setTextColor(Color.parseColor("#EF4444"))
            }
            "warning" -> {
                iconView.text = "⚠️"
                titleView.setTextColor(Color.parseColor("#F59E0B")) // Orange
                sourceView.text = "Sumber: Belum Terverifikasi"
                sourceView.setTextColor(Color.parseColor("#F59E0B"))
            }
            else -> {
                iconView.text = "ℹ️"
                sourceView.text = "Info"
            }
        }

        // Show View
        isMenuExpanded = false
        menuView.visibility = View.GONE
        bubbleView.visibility = View.GONE
        resultView.visibility = View.VISIBLE
        
        // Note: We might need to make window FOCUSABLE to allow input in menu, 
        // but for results we just need display.
        // If we want the EditText in menu to work, we need to update FLAG_NOT_FOCUSABLE dynamically.
    }

    private fun closeResult() {
        resultView.visibility = View.GONE
        bubbleView.visibility = View.VISIBLE
    }
    
    // ... [snapToEdge, toggleMenu, expandMenu, collapseMenu same as before] ...
    private fun snapToEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val middle = screenWidth / 2
        params.x = if (params.x >= middle) screenWidth - 100 else 0
        try {
            windowManager.updateViewLayout(rootLayout, params)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun toggleMenu() {
        if (isMenuExpanded) collapseMenu() else expandMenu()
    }

    private fun expandMenu() {
        isMenuExpanded = true
        bubbleView.visibility = View.GONE
        menuView.visibility = View.VISIBLE
        
        // Make Focusable for EditText
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            windowManager.updateViewLayout(rootLayout, params)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun collapseMenu() {
        isMenuExpanded = false
        menuView.visibility = View.GONE
        bubbleView.visibility = View.VISIBLE
        
        // Make Not Focusable again (so touches pass through outside)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            windowManager.updateViewLayout(rootLayout, params)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rootLayout.isInitialized) windowManager.removeView(rootLayout)
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VeritasGuard Active")
            .setContentText("Assistive Touch running")
            .setSmallIcon(R.drawable.ic_shield_floating)
            .build()
    }
}
