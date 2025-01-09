package ro.pub.cs.systems.eim.practicaltest02

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Scanner
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PracticalTest02MainActivity : AppCompatActivity() {

    private var serverThread: Thread? = null
    private val weatherCache = mutableMapOf<String, JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practical_test02_main)

        val radioServer = findViewById<RadioButton>(R.id.radio_server)
        val radioClient = findViewById<RadioButton>(R.id.radio_client)
        val serverLayout = findViewById<LinearLayout>(R.id.server_layout)
        val clientLayout = findViewById<LinearLayout>(R.id.client_layout)

        // Switch between Server and Client
        val functionalitySelector = findViewById<RadioGroup>(R.id.functionality_selector)
        functionalitySelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_server -> {
                    serverLayout.visibility = View.VISIBLE
                    clientLayout.visibility = View.GONE
                }
                R.id.radio_client -> {
                    serverLayout.visibility = View.GONE
                    clientLayout.visibility = View.VISIBLE
                }
            }
        }

        // Server logic
        val serverPortEditText = findViewById<EditText>(R.id.server_port)
        val startServerButton = findViewById<Button>(R.id.start_server_button)
        val serverStatusTextView = findViewById<TextView>(R.id.server_status)

        startServerButton.setOnClickListener {
            val port = serverPortEditText.text.toString().toIntOrNull()
            if (port != null) {
                serverThread = Thread {
                    val serverSocket = ServerSocket(port)
                    runOnUiThread { serverStatusTextView.text = "Server started on port $port" }
                    while (!Thread.currentThread().isInterrupted) {
                        val clientSocket = serverSocket.accept()
                        Thread {
                            handleClient(clientSocket)
                        }.start()
                    }
                }
                serverThread?.start()
            } else {
                serverStatusTextView.text = "Invalid port!"
            }
        }

        // Client logic
        val clientAddressEditText = findViewById<EditText>(R.id.client_address)
        val clientPortEditText = findViewById<EditText>(R.id.client_port)
        val cityEditText = findViewById<EditText>(R.id.city_name)
        val infoTypeEditText = findViewById<EditText>(R.id.information_type)
        val sendRequestButton = findViewById<Button>(R.id.send_request_button)
        val responseTextView = findViewById<TextView>(R.id.response_text)

        sendRequestButton.setOnClickListener {
            val address = clientAddressEditText.text.toString()
            val port = clientPortEditText.text.toString().toIntOrNull()
            val city = cityEditText.text.toString()
            val infoType = infoTypeEditText.text.toString()

            if (address.isNotEmpty() && port != null && city.isNotEmpty() && infoType.isNotEmpty()) {
                Thread {
                    try {
                        val socket = Socket(address, port)
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        val reader = Scanner(socket.getInputStream())

                        writer.println(city)
                        writer.println(infoType)

                        val response = StringBuilder()
                        while (reader.hasNextLine()) {
                            response.append(reader.nextLine()).append("\n")
                        }

                        runOnUiThread {
                            responseTextView.text = "Response: $response"
                        }

                        socket.close()
                    } catch (e: Exception) {
                        runOnUiThread {
                            responseTextView.text = "Error: ${e.message}"
                        }
                    }
                }.start()
            } else {
                responseTextView.text = "Invalid input!"
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        val clientAddress = clientSocket.inetAddress.hostAddress
        println("New connection from $clientAddress")

        val reader = Scanner(clientSocket.getInputStream())
        val writer = PrintWriter(clientSocket.getOutputStream(), true)

        val city = reader.nextLine()
        val infoType = reader.nextLine()

        println("Request received: city=$city, infoType=$infoType from $clientAddress")

        val weatherData = weatherCache[city] ?: fetchWeatherData(city)?.also {
            weatherCache[city] = it
        }

        if (weatherData != null) {
            val response = when (infoType) {
                "temperature" -> "Temperature: ${weatherData.getJSONObject("main").getDouble("temp")} °C"
                "wind" -> "Wind speed: ${weatherData.getJSONObject("wind").getDouble("speed")} m/s"
                "pressure" -> "Pressure: ${weatherData.getJSONObject("main").getInt("pressure")} hPa"
                "humidity" -> "Humidity: ${weatherData.getJSONObject("main").getInt("humidity")} %"
                "all" -> {
                    val main = weatherData.getJSONObject("main")
                    val wind = weatherData.getJSONObject("wind")
                    "Temperature: ${main.getDouble("temp")} °C\n" +
                            "Wind speed: ${wind.getDouble("speed")} m/s\n" +
                            "Pressure: ${main.getInt("pressure")} hPa\n" +
                            "Humidity: ${main.getInt("humidity")} %"
                }
                else -> "Invalid request"
            }
            writer.println(response)
        } else {
            writer.println("Error fetching weather data for city: $city")
        }

        println("Response sent to $clientAddress")
        clientSocket.close()
    }

    private fun fetchWeatherData(city: String): JSONObject? {
        val apiKey = "e03c3b32cfb5a6f7069f2ef29237d87e"
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serverThread?.interrupt()
    }
}
