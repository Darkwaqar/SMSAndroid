package com.example.sms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.OkHttpResponseAndStringRequestListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.arnaudguyon.xmltojsonlib.XmlToJson;
import okhttp3.Response;

/**
 * This app provides SMS features that enable the user to:
 * - Enter a phone number.
 * - Enter a message and send the message to the phone number.
 * - Receive SMS messages and display them in a toast.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
    private IntentFilter receiveFilter;
    private MessageReceiver messageReceiver;
    private MySmsReceiver mySmsReceiver;
    private TextView sender;
    private TextView content;

    /**
     * Creates the activity, sets the view, and checks for SMS permission.
     *
     * @param savedInstanceState Instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sender = (TextView) findViewById(R.id.sender);
        content = (TextView) findViewById(R.id.content);

        // Check to see if SMS is enabled.
        checkForSmsPermission();

        receiveFilter = new IntentFilter();
        receiveFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
        receiveFilter.setPriority(100);

        messageReceiver = new MessageReceiver();
        mySmsReceiver = new MySmsReceiver();
        registerReceiver(mySmsReceiver, receiveFilter);


    }

    /**
     * Checks whether the app has SMS permission.
     */
    private void checkForSmsPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.permission_not_granted));
            // Permission not yet granted. Use requestPermissions().
            // MY_PERMISSIONS_REQUEST_SEND_SMS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);
        } else {
            // Permission already granted. Enable the SMS button.
            enableSmsButton();
        }
    }

    /**
     * Processes permission request codes.
     *
     * @param requestCode  The request code passed in requestPermissions()
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        // For the requestCode, check if permission was granted or not.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_SEND_SMS: {
                if (permissions[0].equalsIgnoreCase(Manifest.permission.SEND_SMS)
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted. Enable sms button.
                    enableSmsButton();
                } else {
                    // Permission denied.
                    Log.d(TAG, getString(R.string.failure_permission));
                    Toast.makeText(this, getString(R.string.failure_permission),
                            Toast.LENGTH_LONG).show();
                    // Disable the sms button.
                    disableSmsButton();
                }
            }
        }
    }

    /**
     * Defines a string (destinationAddress) for the phone number
     * and gets the input text for the SMS message.
     * Uses SmsManager.sendTextMessage to send the message.
     * Before sending, checks to see if permission is granted.
     *
     * @param view View (message_icon) that was clicked.
     */
    public void smsSendMessage(View view) {
        EditText editText = (EditText) findViewById(R.id.editText_main);
        // Set the destination phone number to the string in editText.
        String destinationAddress = editText.getText().toString();
        // Find the sms_message view.
        EditText smsEditText = (EditText) findViewById(R.id.sms_message);
        // Get the text of the sms message.
        String smsMessage = smsEditText.getText().toString();
        // Set the service center address if needed, otherwise null.
        String scAddress = null;
        // Set pending intents to broadcast
        // when message sent and when delivered, or set to null.
        PendingIntent sentIntent = null, deliveryIntent = null;
        // Check for permission first.
        checkForSmsPermission();
        // Use SmsManager.
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(destinationAddress, scAddress, smsMessage,
                sentIntent, deliveryIntent);
    }

    /**
     * Makes the sms button (message icon) invisible so that it can't be used,
     * and makes the Retry button visible.
     */
    private void disableSmsButton() {
        Toast.makeText(this, R.string.sms_disabled, Toast.LENGTH_LONG).show();
        ImageButton smsButton = (ImageButton) findViewById(R.id.message_icon);
        smsButton.setVisibility(View.INVISIBLE);
        Button retryButton = (Button) findViewById(R.id.button_retry);
        retryButton.setVisibility(View.VISIBLE);
    }

    /**
     * Makes the sms button (message icon) visible so that it can be used.
     */
    private void enableSmsButton() {
        ImageButton smsButton = (ImageButton) findViewById(R.id.message_icon);
        smsButton.setVisibility(View.VISIBLE);
    }

    /**
     * Sends an intent to start the activity.
     *
     * @param view View (Retry button) that was clicked.
     */
    public void retryApp(View view) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        startActivity(intent);
    }

    class MySmsReceiver extends BroadcastReceiver {
        public static final String pdu_type = "pdus";

        /**
         * Called when the BroadcastReceiver is receiving an Intent broadcast.
         *
         * @param context The Context in which the receiver is running.
         * @param intent  The Intent received.
         */
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "Intent recieved: " + intent.getAction());
            // Get the SMS message.
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs;
            String strMessage = "";
            String format = bundle.getString("format");
            // Retrieve the SMS message received.
            Object[] pdus = (Object[]) bundle.get(pdu_type);
            if (pdus != null) {
                // Fill the msgs array.
                msgs = new SmsMessage[pdus.length];
                for (int i = 0; i < msgs.length; i++) {
                    // Check Android version and use appropriate createFromPdu.
                    // Check the Android version.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // If Android version M or newer:
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    } else {
                        // If Android version L or older:
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }


                    // Build the message to show.
                    strMessage += "SMS from " + msgs[i].getOriginatingAddress();
                    strMessage += " :" + msgs[i].getMessageBody() + "\n";
                    // Log and display the SMS message.
                    Log.d(TAG, "onReceive: " + strMessage);

                    String address = msgs[i].getOriginatingAddress();
                    String fullMessage = "";
                    for (SmsMessage message : msgs) {
                        fullMessage += message.getMessageBody();
                    }

                    sender.setText(address);
                    content.setText(fullMessage);

                    EditText editText = (EditText) findViewById(R.id.editText_main);
                    editText.setText(msgs[i].getOriginatingAddress());
                    abortBroadcast();

                    if (isValidMobileNo(msgs[i].getOriginatingAddress())) {
                        AndroidNetworking.post("https://www.gomobishop.com/Mservices/SMS/SMSWebhook")
                                .addBodyParameter("ToCountry", "US")
                                .addBodyParameter("ToState", "KY")
                                .addBodyParameter("SmsMessageSid", "SMcf52063ea17ac2b90ad0cf66dcc3a2e7")
                                .addBodyParameter("NumMedia", "0")
                                .addBodyParameter("ToCity", "MAYSVILLE")
                                .addBodyParameter("FromZip", "")
                                .addBodyParameter("SmsSid", "SMcf52063ea17ac2b90ad0cf66dcc3a2e7")
                                .addBodyParameter("FromState", "")
                                .addBodyParameter("SmsStatus", "received")
                                .addBodyParameter("FromCity", "")
                                .addBodyParameter("FromCity", "")
                                .addBodyParameter("Body", fullMessage)
                                .addBodyParameter("FromCountry", "PK")
                                .addBodyParameter("To", "+16067320885")
                                .addBodyParameter("MessagingServiceSid", "MGa6a9860b3cad8acccc5f9396cb8b3850")
                                .addBodyParameter("ToZip", "41055")
                                .addBodyParameter("ToZip", "41055")
                                .addBodyParameter("From", address)
                                .setTag("test")
                                .setPriority(Priority.MEDIUM)
                                .build()
                                .getAsOkHttpResponseAndString(new OkHttpResponseAndStringRequestListener() {
                                    @Override
                                    public void onResponse(Response okHttpResponse, String response) {
                                        XmlToJson xmlToJson = new XmlToJson.Builder(response).build();
                                        // convert to a JSONObject
                                        JSONObject jsonObject = xmlToJson.toJson();
                                        try {
                                            JSONObject responseIn = jsonObject.getJSONObject("Response");
                                            String MessageIn = responseIn.getString("Message");
                                            EditText smsEditText = (EditText) findViewById(R.id.sms_message);
                                            smsEditText.setText(MessageIn);
                                            ImageButton smsButton = (ImageButton) findViewById(R.id.message_icon);
                                            smsButton.performClick();
                                            Log.d(TAG, "onReceive: " + xmlToJson.toString());

                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onError(ANError anError) {
                                        Log.d(TAG, "onReceive: " + anError.toString());
                                    }
                                })
                        ;
                    }


                }
            }
        }
    }

    class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, "Intent recieved: " + intent.getAction());
            Bundle bundle = intent.getExtras();
            Object[] pdus = (Object[]) bundle.get("pdus"); // Retrieve SMS messages
            SmsMessage[] messages = new SmsMessage[pdus.length];
            for (int i = 0; i < messages.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }
            String address = messages[0].getOriginatingAddress();
            String fullMessage = "";
            for (SmsMessage message : messages) {
                fullMessage += message.getMessageBody();
            }
            sender.setText(address);
            content.setText(fullMessage);
            abortBroadcast();

        }
    }

    //function to check if the mobile number is valid or not
    public static boolean isValidMobileNo(String str) {
//(0/91): number starts with (0/91)
//[7-9]: starting of the number may contain a digit between 0 to 9
//[0-9]: then contains digits 0 to 9
        Pattern ptrn = Pattern.compile("[+]*(0/91)?[7-9][0-9]{11}");
//the matcher() method creates a matcher that will match the given input against this pattern
        Matcher match = ptrn.matcher(str);
//returns a boolean value
        return (match.find() && match.group().equals(str));
    }
}