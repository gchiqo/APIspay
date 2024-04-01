package com.example.apispay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ID = "1122"
        private const val NOTIFICATION_ID = 123
    }

    private var handler: Handler? = null
    private var runnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        val apiUrlEditText = findViewById<EditText>(R.id.apiUrlEditText)
        val getRadioButton = findViewById<RadioButton>(R.id.getRadioButton)
        val postRadioButton = findViewById<RadioButton>(R.id.postRadioButton)
        val requestBodyEditText = findViewById<EditText>(R.id.requestBodyEditText)
        val sendRequestButton = findViewById<Button>(R.id.sendRequestButton)
        val responseTextView = findViewById<TextView>(R.id.responseTextView)
        val intervalEditText = findViewById<EditText>(R.id.intervalEditText)
        val wrongResponseEditText = findViewById<EditText>(R.id.wrongResponse)

        // Set click listener for the sendRequestButton
        sendRequestButton.setOnClickListener {
            var interval: Long = (intervalEditText.text.toString().toLongOrNull() ?: 5) * 1000
            val apiUrl = apiUrlEditText.text.toString()
            val requestType = if (getRadioButton.isChecked) "GET" else "POST"
            val requestBody = requestBodyEditText.text.toString()
            val wrongResponse = wrongResponseEditText.text.toString()

            // Clear any existing handlers and runnables
            runnable?.let { it1 -> handler?.removeCallbacks(it1) }

            // Start sending requests at regular intervals
            handler = Handler()
            runnable = object : Runnable {
                override fun run() {
                    SendRequestTask(responseTextView, wrongResponse).execute(
                        apiUrl,
                        requestType,
                        requestBody
                    )
                    handler?.postDelayed(this, interval)
                }
            }
            handler?.post(runnable!!)
        }

        // Create the notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // AsyncTask to perform network request in background thread
    private class SendRequestTask(
        private val responseTextView: TextView,
        private val wrongResponse: String
    ) : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String?): String {
            val apiUrl = params[0]
            val requestType = params[1]
            val requestBody = params[2]

            var response = ""
            try {
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = requestType
                connection.setRequestProperty("Content-Type", "application/json")

                if (requestType == "POST") {
                    connection.doOutput = true
                    val outputStream: OutputStream = connection.outputStream
                    if (requestBody != null) {
                        outputStream.write(requestBody.toByteArray())
                    }
                    outputStream.flush()
                    outputStream.close()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStreamReader = InputStreamReader(connection.inputStream)
                    val bufferedReader = BufferedReader(inputStreamReader)
                    var line: String?
                    val responseData = StringBuilder()
                    while (bufferedReader.readLine().also { line = it } != null) {
                        responseData.append(line)
                    }
                    bufferedReader.close()
                    response = responseData.toString()
                } else {
                    response = "Error: ${connection.responseMessage}"
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                response = "Error: ${e.message}"
            }
            return response
        }

        private val MAX_LINES = 100 // Maximum number of lines to display in the TextView

        // Inside the onPostExecute method of SendRequestTask
        @SuppressLint("MissingPermission")
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (result != null) {
                // Limit the displayed text to a certain number of lines
                val currentText = responseTextView.text.toString()
                val newText = "$result\n\n$currentText"
                val lines = newText.split("\n")
                val limitedText = if (lines.size > MAX_LINES) {
                    lines.take(MAX_LINES)
                        .joinToString("\n") + "\n\n[...]\n" // Display only a subset of lines
                } else {
                    newText
                }
                responseTextView.text = limitedText

                // Check if response is not empty and wrongResponse is not equal to response
                if (result != wrongResponse) {
                    // Create a notification
                    val notificationBuilder = NotificationCompat.Builder(
                        responseTextView.context,
                        CHANNEL_ID
                    )
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("New Response Received")
                        .setContentText("Response: $result")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                    // Play notification sound
                    val notificationSound =
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = RingtoneManager.getRingtone(
                        responseTextView.context,
                        notificationSound
                    )
                    ringtone.play()

                    // Show notification
                    with(NotificationManagerCompat.from(responseTextView.context)) {
                        notify(NOTIFICATION_ID, notificationBuilder.build())
                    }
                }
            }
        }
    }
}
