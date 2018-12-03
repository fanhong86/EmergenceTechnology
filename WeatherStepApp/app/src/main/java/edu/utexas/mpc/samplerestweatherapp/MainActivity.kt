package edu.utexas.mpc.samplerestweatherapp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage



class MainActivity : AppCompatActivity() {


    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var retrieveButton: Button
    lateinit var sendButton: Button
    lateinit var wifiButton: Button
    lateinit var syncButton: Button
    lateinit var imageView: ImageView

    lateinit var queue: RequestQueue
    lateinit var gson: Gson

    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mqttAndroidClient: MqttAndroidClient
    lateinit var mostRecentForecastResult: ForecastResult

    val serverUri = "tcp://192.168.4.1"
    val clientId = "EmergingTechMQTTClient"

    val subscribeTopic = "stepTopic"
    val publishTopic = "weatherTopic"
    val publishTopic1 = "forecastTopic"




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        imageView = this.findViewById(R.id.image)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        sendButton = this.findViewById(R.id.sendButton)
        syncButton = this.findViewById(R.id.syncButton)
        wifiButton = this.findViewById(R.id.wifiButton)

        // when the user presses the syncbutton, this method will get called
        retrieveButton.setOnClickListener({ requestWeather() })
        sendButton.setOnClickListener({ sendWeather()})
        syncButton.setOnClickListener({ syncWithPi() })
        wifiButton.setOnClickListener({ isNetworkConnected() })

        // initialize the paho mqtt client with the uri and client id
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        if (networkInfo != null && networkInfo.isConnected)
            textView.text="Wifi Connected "+networkInfo.extraInfo.toString()
        else
            textView.text="Wifi Disconnected"

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                textView.text="Connection Complete"


            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                textView.text = message.toString()

            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
                textView.text="Connection Lost"
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
                textView.text="Delivery Complete"
            }
        })


        queue = Volley.newRequestQueue(this)
        gson = Gson()
    }

     fun isNetworkConnected() {

         val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
         //Thread.sleep(3_000)
         startActivity(intent)

    }


    // this method just connects the paho mqtt client to the broker
    fun syncWithPi(){
        println("+++++++ Connecting...")

        mqttAndroidClient.connect()
    }



    fun requestWeather(){
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?id=4671654&appid=4da70a14d06850ee65d9d302ed07cfed").toString()
        val url1 = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?id=4671654&appid=4da70a14d06850ee65d9d302ed07cfed").toString()

        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    textView.text = response
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    textView.text = mostRecentWeatherResult.weather.get(0).description

                   // textView.text=mostRecentForecastResult.list[0].main.temp_max.toString()

                    var iconCode = mostRecentWeatherResult.weather.get(0).icon
                    var iconUrl = "http://openweathermap.org/img/w/" + iconCode + ".png"
                    Picasso.with(this).load(iconUrl).resize(200, 200).into(imageView)
                },
                com.android.volley.Response.ErrorListener { println("******Current weather didn't work!") }) {}




        val stringRequest1 = object : StringRequest(com.android.volley.Request.Method.GET, url1,
                com.android.volley.Response.Listener<String> { predict ->
                    mostRecentForecastResult = gson.fromJson(predict, ForecastResult::class.java)

                },
                com.android.volley.Response.ErrorListener { println("******Forecast didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequest)
        queue.add(stringRequest1)
    }


    val t_max = mutableListOf<Double>()
    val t_min = mutableListOf<Double>()
    val t_h = mutableListOf<Int>()


    fun sendWeather(){
        // this subscribes the client to the subscribe topic

        mqttAndroidClient.subscribe(subscribeTopic, 0)
        val message = MqttMessage()
        val message1 = MqttMessage()

        val weather = listOf(mostRecentWeatherResult.main.temp_min,mostRecentWeatherResult.main.temp_max,mostRecentWeatherResult.main.humidity).joinToString()

        for (i in 0..8){
            val a=mostRecentForecastResult.list[i].main.temp_max
            t_max.add(a)
            val b=mostRecentForecastResult.list[i].main.temp_min
            t_min.add(b)
            val c=mostRecentForecastResult.list[i].main.humidity
            t_h.add(c)
        }
        val prediction = listOf(t_max.max(),t_min.min(),t_h.average()).joinToString()

        // this publishes a message to the publish topic
        message.payload = weather.toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
        println("today successful")
        message1.payload = prediction.toByteArray()
        mqttAndroidClient.publish(publishTopic1, message1)
        println("tommorow successful")
    }

}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

class ForecastResult(val list: Array<WeatherList>)
class WeatherList(val main: PredictMain, val dt_txt: String)
class PredictMain(val humidity: Int, val temp_min: Double, val temp_max: Double)

