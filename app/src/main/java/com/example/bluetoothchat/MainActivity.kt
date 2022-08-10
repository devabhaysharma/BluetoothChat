package com.example.bluetoothchat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    var listen: Button? = null
    var send: Button? = null
    var listDevices: Button? = null
    var listView: ListView? = null
    var msg_box: TextView? = null
    var status: TextView? = null
    var writeMsg: EditText? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    lateinit var btArray: Array<BluetoothDevice>
    var sendReceive: SendReceive? = null
    var REQUEST_ENABLE_BLUETOOTH = 1
    override
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewByIdes()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled()) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        }
        implementListeners()
    }

    private fun implementListeners() {

        listDevices.run {

            setOnClickListener(object : View.OnClickListener() {
                fun onClick(view: View?) {
                    val bt: Set<BluetoothDevice> = bluetoothAdapter.getBondedDevices()
                    val strings: Array<String> = arrayOfNulls(bt.size)
                    btArray = arrayOfNulls<BluetoothDevice>(bt.size)
                    var index = 0
                    if (bt.size > 0) {
                        for (device in bt) {
                            btArray[index] = device
                            strings[index] = device.getName()
                            index++
                        }
                        val arrayAdapter: ArrayAdapter<String> = ArrayAdapter<String>(
                            getApplicationContext(),
                            android.R.layout.simple_list_item_1,
                            strings
                        )
                        listView.setAdapter(arrayAdapter)
                    }
                }
            })
        }
        listen.setOnClickListener(object : OnClickListener() {
            fun onClick(view: View?) {
                val serverClass: ServerClass = ServerClass()
                serverClass.start()
            }
        })
        listView.setOnItemClickListener(object : OnItemClickListener() {
            fun onItemClick(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                val clientClass: ClientClass = ClientClass(
                    btArray[i]
                )
                clientClass.start()
                status.setText("Connecting")
            }
        })
        send.setOnClickListener(object : OnClickListener() {
            fun onClick(view: View?) {
                val string: String = java.lang.String.valueOf(writeMsg.getText())
                sendReceive!!.write(string.toByteArray())
            }
        })
    }

    var handler: Handler = Handler(object : Callback() {
        fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                STATE_LISTENING -> status.setText("Listening")
                STATE_CONNECTING -> status.setText("Connecting")
                STATE_CONNECTED -> status.setText("Connected")
                STATE_CONNECTION_FAILED -> status.setText("Connection Failed")
                STATE_MESSAGE_RECEIVED -> {
                    val readBuff = msg.obj as ByteArray
                    val tempMsg = String(readBuff, 0, msg.arg1)
                    msg_box.setText(tempMsg)
                }
            }
            return true
        }
    })

    private fun findViewByIdes() {
        listen = findViewById(R.id.listen) as Button?
        send = findViewById(R.id.send) as Button?
        listView = findViewById(R.id.listview) as ListView?
        msg_box = findViewById(R.id.msg) as TextView?
        status = findViewById(R.id.status) as TextView?
        writeMsg = findViewById(R.id.writemsg) as EditText?
        listDevices = findViewById(R.id.listDevices) as Button?
    }

    private inner class ServerClass : java.lang.Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        override fun run() {
            var socket: BluetoothSocket? = null
            while (socket == null) {
                try {
                    val message: Message = Message.obtain()
                    message.what = STATE_CONNECTING
                    handler.sendMessage(message)
                    socket = serverSocket.accept()
                } catch (e: IOException) {
                    e.printStackTrace()
                    val message: Message = Message.obtain()
                    message.what = STATE_CONNECTION_FAILED
                    handler.sendMessage(message)
                }
                if (socket != null) {
                    val message: Message = Message.obtain()
                    message.what = STATE_CONNECTED
                    handler.sendMessage(message)
                    sendReceive = SendReceive(socket)
                    sendReceive.start()
                    break
                }
            }
        }

        init {
            try {
                serverSocket =
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private inner class ClientClass(device1: BluetoothDevice) : java.lang.Thread() {
        private val device: BluetoothDevice
        private var socket: BluetoothSocket? = null
        override fun run() {
            try {
                socket.connect()
                val message: Message = Message.obtain()
                message.what = STATE_CONNECTED
                handler.sendMessage(message)
                sendReceive = SendReceive(socket)
                sendReceive.start()
            } catch (e: IOException) {
                e.printStackTrace()
                val message: Message = Message.obtain()
                message.what = STATE_CONNECTION_FAILED
                handler.sendMessage(message)
            }
        }

        init {
            device = device1
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    inner class SendReceive(socket: BluetoothSocket) : java.lang.Thread() {
        private val bluetoothSocket: BluetoothSocket
        private val inputStream: java.io.InputStream?
        private val outputStream: java.io.OutputStream?
        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = inputStream.read(buffer)
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        fun write(bytes: ByteArray?) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        init {
            bluetoothSocket = socket
            var tempIn: java.io.InputStream? = null
            var tempOut: java.io.OutputStream? = null
            try {
                tempIn = bluetoothSocket.getInputStream()
                tempOut = bluetoothSocket.getOutputStream()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            inputStream = tempIn
            outputStream = tempOut
        }
    }

    companion object {
        const val STATE_LISTENING = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
        const val STATE_CONNECTION_FAILED = 4
        const val STATE_MESSAGE_RECEIVED = 5
        private const val APP_NAME = "BTChat"
        private val MY_UUID: UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66")
    }
}