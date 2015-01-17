package me.etckeepers.listen;

import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;

import com.getpebble.android.kit.PebbleKit;
import com.google.android.gms.location.LocationServices;

public class ListernActivity extends ActionBarActivity implements
        RecognitionListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private SpeechRecognizer recognizer = null;
    private Intent recognizerIntent;
    private Boolean isListening = false;
    private String LOG_TAG = "ListernActivity";
    private Boolean currentlyListening = false;
    private int messageCount = 0;
    private ArrayList<String> messages = new ArrayList<String>();
    private GoogleApiClient gapi = null;
    private Location location = null;
    private String[] pbEmails = {"foxwilson123@gmail.com"};

    class SubmitDataTask extends AsyncTask<Object, Void, Void>{
        protected Void doInBackground(Object... param){
            HttpClient httpclient = (HttpClient) param[1];
            HttpPost httppost = (HttpPost) param[0];
            Integer time = (Integer) param[2];
            ArrayList<String> localMessages;
            boolean override = false;
            if (param.length > 3){
                localMessages = (ArrayList<String>) param[3];
                override = true;
            }
            else{
                localMessages = messages;
            }
            String payload = "";
            String delim = "";
            for(String m : localMessages){
                payload = payload + delim + m;
                delim = ",";
            }
            if(!override) messages = new ArrayList<String>();
            if(gapi != null){
                Log.i(LOG_TAG, "Using google api");
                try {
                    location = LocationServices.FusedLocationApi.getLastLocation(gapi);
                    if(location != null) Log.i(LOG_TAG, "Got location " + location.toString());
                    else Log.e(LOG_TAG, "NULL Location");
                }
                catch (Exception e){
                    Log.e(LOG_TAG, "Getting location error " + e.toString());
                }
            }
            String loc = "0,0";
            if(location != null){
                loc = String.valueOf(location.getLatitude()) + "," + String.valueOf(location.getLongitude());
            }
            Log.i(LOG_TAG, "Sending coords " + loc);

            List<NameValuePair> params = new ArrayList<NameValuePair>(3);
            params.add(new BasicNameValuePair("text", payload));
            params.add(new BasicNameValuePair("location", loc));
            params.add(new BasicNameValuePair("key", "1282825906610821073492C58AE04EC5"));
            try {
                httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
            }
            catch(UnsupportedEncodingException uee){
                Log.e(LOG_TAG, "UnsupportedEncodingException while submitting.");
            }
            Log.d(LOG_TAG, "Sending data to server with payload " + payload + ".");
            try {
                HttpResponse response = httpclient.execute(httppost);
                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    InputStream instream = entity.getContent();
                    try {
                        Log.d(LOG_TAG, "Data submitted.");
                    } finally {
                        instream.close();
                    }
                }
            }
            catch(IOException ioe){
                Log.e(LOG_TAG, "IOException while submitting. Sleeping " + String.valueOf(time) + " seconds.");
                try {
                    Thread.sleep(time * 1000);
                }
                catch (InterruptedException ie){
                    Log.e(LOG_TAG, "Sleep interrupted.");
                }
                new SubmitDataTask().execute(httppost, httpclient, time * 2);
            }
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listern);
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                "5000");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                "5000");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                "5000");
        gapi = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_listern, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void bullshitClick(View view){
        if (isListening){
            recognizer.cancel();
            recognizer.stopListening();
            updateStatus("Listening stopped.");
            Button listenButton = (Button) findViewById(R.id.bullshitbutton);
            listenButton.setText("Start Listening");
            isListening = false;
        }
        else {
            recognizer.startListening(recognizerIntent);
            updateStatus("Listening started.");
            Button listenButton = (Button) findViewById(R.id.bullshitbutton);
            listenButton.setText("Stop Listening");
            isListening = true;

        }
    }

    public void forceSend(View view){
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost("http://shrekislove.ngrok.com/420/blazeit/");
        updateStatus("Submitting data...");
        new SubmitDataTask().execute(httppost, httpclient, Integer.valueOf(2));
    }

    public void updateStatus(String text) {

        TextView textView = (TextView) findViewById(R.id.statusText);
        textView.setText(text);

        return;
    }

    public void updateResults(String text) {

        TextView textView = (TextView) findViewById(R.id.results);
        textView.setText(text);

        return;
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        gapi.connect();
    }

    @Override
    public void onStop() {
        gapi.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
        updateStatus("Speech started, actively listening...");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
        updateStatus("End of speech, generating results...");
        currentlyListening = false;
        if (isListening) {
            recognizer.startListening(recognizerIntent);
            currentlyListening = true;
        }

    }

    @Override
    public void onError(int errorCode) {
        currentlyListening = false;
        if(errorCode != SpeechRecognizer.ERROR_RECOGNIZER_BUSY && isListening){
            recognizer.startListening(recognizerIntent);
            currentlyListening = true;
        }
        String errorMessage = getErrorText(errorCode);
        if(errorMessage.equals("")) return;
        Log.d(LOG_TAG, "FAILED " + errorMessage);
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
        updateStatus("Listening for start of speech...");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = matches.get(0);
        if(text.split(" ").length < 3){
            Log.i(LOG_TAG, "Skipping 2 word or less result, " + text);
            return;
        }
        updateResults(text);

        messageCount++;
        Log.i(LOG_TAG, "GOT RESULT: "+text);
        sendAlertToPebble("Result", text);
        messages.add(text);
        if(messageCount % 5 == 0){
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("http://shrekislove.ngrok.com/420/blazeit/");
            updateStatus("Submitting data...");
            new SubmitDataTask().execute(httppost, httpclient, Integer.valueOf(2));
        }

    }

    @Override
    public void onConnected(Bundle connectionHint){
        Log.i(LOG_TAG, "Connected to GPS service");
    }

    @Override
    public void onConnectionSuspended(int something){
        Log.e(LOG_TAG, "Disconnected from GPS :>");

    }

    @Override
    public void onConnectionFailed(ConnectionResult conn){
        Log.e(LOG_TAG, "Connection failed: " + String.valueOf(conn.getErrorCode()));
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        return;
    }

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "";
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

    public void sendAlertToPebble(String title, String text) {
        boolean connected = PebbleKit.isWatchConnected(getApplicationContext());
        final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map data = new HashMap();
        data.put("title", title);
        data.put("body", text);
        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();

        i.putExtra("messageType", "PEBBLE_ALERT");
        i.putExtra("sender", "Listen");
        i.putExtra("notificationData", notificationData);

        Log.d(LOG_TAG, "About to send a modal alert to Pebble: " + notificationData);
        sendBroadcast(i);
    }
}
