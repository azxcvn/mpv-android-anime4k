package com.fam4k007.videoplayer.utils

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局异常处理器
 * 自动捕获未处理的崩溃，保存日志并提示用户
 */
class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    companion object {
        private const val TAG = "CrashHandler"
        
        @Volatile
        private var instance: CrashHandler? = null
        
        /**
         * 初始化全局异常处理器
         * 在Application.onCreate()中调用
         */
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CrashHandler(context.applicationContext)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                        Log.d(TAG, "全局异常处理器已初始化")
                    }
                }
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. 保存崩溃日志到本地
            val logFile = saveCrashLog(throwable)
            
            // 2. 显示友好提示
            showCrashToast(throwable, logFile)
            
            // 3. 等待Toast显示（给用户看到提示的时间）
            Thread.sleep(2000)
        } catch (e: Exception) {
            Log.e(TAG, "处理崩溃异常失败", e)
        } finally {
            // 4. 交给系统默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    /**
     * 保存崩溃日志到本地文件
     * @return 日志文件路径，失败返回null
     */
    private fun saveCrashLog(throwable: Throwable): File? {
        return try {
            // 日志保存目录：/Android/data/包名/files/crash_logs/
            val logDir = File(context.getExternalFilesDir(null), "crash_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // 文件名：crash_2026-01-02_15-30-45.txt
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "crash_$timestamp.txt")
            
            // 写入崩溃信息
            FileWriter(logFile).use { writer ->
                writer.append("=============================================\n")
                writer.append("           App Crash Log\n")
                writer.append("=============================================\n\n")
                
                // 基本信息
                writer.append("[Time] $timestamp\n")
                writer.append("[App Version] Unknown\n")  // BuildConfig不可用时使用占位符
                writer.append("[Device Model] ${Build.MANUFACTURER} ${Build.MODEL}\n")
                writer.append("[System Version] Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                writer.append("[CPU Architecture] ${Build.SUPPORTED_ABIS.joinToString(", ")}\n\n")
                
                // 异常信息
                writer.append("[Exception Type] ${throwable.javaClass.name}\n")
                writer.append("[Exception Message] ${throwable.message ?: "None"}\n\n")
                
                writer.append("[Stack Trace]\n")
                throwable.printStackTrace(PrintWriter(writer))
                
                writer.append("\n\n=============================================\n")
                writer.append("Tip: To report issues, please send this file to the developer\n")
                writer.append("File location: ${logFile.absolutePath}\n")
                writer.append("=============================================\n")
            }
            
            Log.e(TAG, "崩溃日志已保存: ${logFile.absolutePath}")
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
            null
        }
    }
    
    /**
     * 显示崩溃提示Toast
     */
    private fun showCrashToast(throwable: Throwable, logFile: File?) {
        try {
            Handler(Looper.getMainLooper()).post {
                val message = if (logFile != null) {
                    "App encountered an error and stopped running\nLog saved to:\n${logFile.parent}"
                } else {
                    "App encountered an error and stopped running\nError: ${throwable.message}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示崩溃提示失败", e)
        }
    }
}
