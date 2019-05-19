package com.example.android.blueheartreceiver;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN";
    private static final int REQUEST_ENABLE_BT = 2;

    private String filepath; // path of files
    private Button save;
    private TextView attesa;

    String file; // name of files
    String content;

    private boolean dataReceived = false;

    // Layout Views
    private ListView mConversationView;
    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothService mChatService = null;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        save =  findViewById(R.id.bluetooth_b_save);
        attesa =  findViewById(R.id.bluetooth_tv_wait);


        // Request write / read permissions
        String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 1);
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        Intent i = getIntent();
        filepath = i.getStringExtra("filepath");

        if(filepath == null) {
            attesa.setVisibility(View.VISIBLE);
            attesa.setText(R.string.attesa);
            save.setVisibility(View.VISIBLE);
            ensureDiscoverable();
        }else{
            attesa.setVisibility(View.INVISIBLE);
            save.setVisibility(View.INVISIBLE);
            content = searchFile();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }




    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationView = findViewById(R.id.in);

        mConversationArrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothService(getParent(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();
    }


    public void onClickSave(View view) {
        if(dataReceived) {

            String res="";

            for (int j=0;j<mConversationArrayAdapter.getCount();j++){

                String s=mConversationArrayAdapter.getItem(j);
                res=res+s;

            }
            saveTxt(file, res);
            attesa.setText("Data saved in file");
            Toast.makeText(getApplicationContext(), "File Saved Correctly",
                    Toast.LENGTH_SHORT).show();
            mConversationArrayAdapter.clear();
        }else{
            Toast.makeText(getApplicationContext(), "No Data Received!",
                    Toast.LENGTH_SHORT).show();
        }
    }





    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getParent();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
//                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_LONG).show();

                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
//                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
//                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(readMessage);
                    file = "File_" + System.nanoTime() + ".txt";
                    dataReceived=true;
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connecting to "+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };


    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    public String searchFile() {

        File file = new File(filepath);
        FileInputStream fileInputStream;
        String content = null;
        String text;

        try {
            fileInputStream = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuffer stringBuffer = new StringBuffer();
            while ((text = bufferedReader.readLine()) != null)
                stringBuffer.append(text).append("\n");
            content = stringBuffer.toString();
            Toast.makeText(getApplicationContext(),"File ready to be send",Toast.LENGTH_LONG).show();
            Log.i("MAIN",String.valueOf(content));
        }
        catch(FileNotFoundException e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),"File not found",Toast.LENGTH_LONG).show();
        } catch(IOException e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),"Loading not completed",Toast.LENGTH_LONG).show();
        }
        return content;
    }


    public void saveTxt(String file, String testo) {

        try {
            // Saving in blueHeartReceivedFiles folder
            File root = new File(Environment.getExternalStorageDirectory(),"blueHeartReceivedFiles");
            if (!root.exists()) {
                root.mkdirs();
            }
            Log.i("MAIN",String.valueOf(file));
            File blueFile = new File(root,file);
            String filepath = blueFile.getAbsolutePath();

            Log.i("MAIN","File path "+ String.valueOf(filepath));
            // If file doesn't exist it overwrites it else it creates a new file
            if (!blueFile.exists())
                blueFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(blueFile);
            fos.write(String.valueOf(testo).getBytes());
            Log.i("MAIN","File dimension : "+String.valueOf((testo).getBytes().length));
            fos.close();
            Log.i("MAIN", "File " + file + " saved");
            Toast.makeText(getApplicationContext(),"File saved ",Toast.LENGTH_LONG).show();
            attesa.setText(R.string.attesa);
            attesa.setVisibility(View.VISIBLE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("MAIN", "File " + file + " not found");
            Toast.makeText(getApplicationContext(),"File not found ",Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("MAIN", "Saving Error ");
            Toast.makeText(getApplicationContext(),"Saving Error ",Toast.LENGTH_LONG).show();
        }
    }
}
