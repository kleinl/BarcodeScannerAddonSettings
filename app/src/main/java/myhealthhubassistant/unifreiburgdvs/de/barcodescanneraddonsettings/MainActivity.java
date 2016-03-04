package myhealthhubassistant.unifreiburgdvs.de.barcodescanneraddonsettings;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import de.tudarmstadt.dvs.myhealthassistant.myhealthhub.events.AbstractChannel;
import myhealthhubassistant.unifreiburgdvs.de.barcodescanneraddonsettings.comm.CommBroadcastReceiver;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;
    private CommBroadcastReceiver commUnit;
    private Intent myHealthHubIntent;
    private boolean isConnectedToMhh;
    private ArrayList<JSONObject> ListWithBarcodes;
    private int counter = 0;
    private ProgressBar pbar;
    private boolean isFirst = true;
    private ArrayList<Uri> listOfUri;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectToMhh();

        // to exchange data with myHealthHub
        commUnit = new CommBroadcastReceiver(this.getApplicationContext(),
                jResult);
        this.getApplicationContext().registerReceiver(commUnit,
                new IntentFilter(AbstractChannel.MANAGEMENT));

        pbar = (ProgressBar) findViewById(R.id.progressBar);
        pbar.setVisibility(View.INVISIBLE);

        Button createDb = (Button) findViewById(R.id.button_createDb);
        createDb.setVisibility(View.INVISIBLE);
        createDb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (ListWithBarcodes != null) {
                        createBarcodeDB(ListWithBarcodes);
                    } else {
                        Toast.makeText(getApplicationContext(), "Scan barcodes first", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        Button scan = (Button) findViewById(R.id.button_scan);
        scan.setText("Create DB");
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createDb();
            }
        });

        Button reset = (Button) findViewById(R.id.button_reset);
        reset.setText("Reset");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        Button sendDb = (Button) findViewById(R.id.sendDb);
        sendDb.setText("Send DB via Email");
        sendDb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDb();
            }
        });
    }

    private void sendDb() {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File[] files = new File[2];
        files[0] = new File(root + "/BarcodeScannerAddonData/Barcodes.csv");
        files[1] = new File(root + "/BarcodeScannerAddonData/Umfrageergebnisse.csv");
        emailMultipleFiles(files,"BarcodeScannerAddon Database","Files are attached");
    }

    private void emailMultipleFiles(File[] attachmentFiles, String subjectContent, String messageContent) {
        Intent email = new Intent(Intent.ACTION_SEND_MULTIPLE);
        email.putExtra(Intent.EXTRA_SUBJECT, subjectContent);
        email.putExtra(Intent.EXTRA_TEXT, messageContent);

        ArrayList<Uri> uriList = new ArrayList<Uri>();
        for (int i = 0; i < attachmentFiles.length; i++) {
            uriList.add(Uri.fromFile(attachmentFiles[i]));
        }

        email.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
        email.setType("text/plain");
        /** or use **/
        email.setType("message/rfc822");
        startActivity(email);
    }

    private void reset() {
        Intent intent = new Intent("myhealthhubassistant.unifreiburgdvs.de.barcodescanneraddon.RESET");
        startActivity(intent);
        finish();
    }

    private void createDb() {
        // to exchange data with myHealthHub
        commUnit = new CommBroadcastReceiver(this.getApplicationContext(),
                jResult);
        this.getApplicationContext().registerReceiver(commUnit,
                new IntentFilter(AbstractChannel.MANAGEMENT));
        commUnit.getJSONEntryList();
    }

    private final CommBroadcastReceiver.JSONResult jResult = new CommBroadcastReceiver.JSONResult() {
        @Override
        public void gotResult(JSONArray jObjArray) throws IOException, JSONException {
            ArrayList<JSONObject> mListObj = new ArrayList<>();
            boolean addToList;

            JSONArray jObjToDel = new JSONArray(); // list of duplicates to be removed
            for (int i = 0; i < jObjArray.length(); i++) {
                JSONObject jObj = jObjArray.optJSONObject(i);
                if (jObj != null) {
                    if (jObj.optString("KIND").equals("survey")) {
                        addToList = true;
                        Log.e("test", jObj.optString("TIME"));
                        String survey = jObj.optString("SURVEY");
                        String objDate = jObj.optString("DATE");
                        String objTime = jObj.optString("TIME");

                        for (JSONObject jCompare : mListObj) {
                            String surveyCompare = jCompare.optString("SURVEY");
                            String dateCompare = jCompare.optString("DATE");
                            String timeCompare = jCompare.optString("TIME");

                            if (objDate.equals(dateCompare) && objTime.equals(timeCompare) && survey.equals(surveyCompare)) {
                                addToList = false;
                                // all entries with same date and time will be put here to delete
                                jObjToDel.put(jObj);
                                break;
                            }
                        }
                        if (addToList)
                            mListObj.add(jObj);
                    }
                }
            }
            createSurveyDB(mListObj);



            mListObj.clear();
            JSONArray jObjToDel2 = new JSONArray(); // list of duplicates to be removed
            for (int i = 0; i < jObjArray.length(); i++) {
                JSONObject jObj = jObjArray.optJSONObject(i);
                if (jObj != null) {
                    if (jObj.optString("KIND").equals("item")) {
                        addToList = true;
                        String objDate = jObj.optString("DATE");
                        String objTime = jObj.optString("TIME");

                        for (JSONObject jCompare : mListObj){
                            String dateCompare = jCompare.optString("DATE");
                            String timeCompare = jCompare.optString("TIME");

                            if (objDate.equals(dateCompare) && objTime.equals(timeCompare)){
                                addToList = false;
                                // all entries with same date and time will be put here to delete
                                jObjToDel2.put(jObj);
                                break;
                            }
                        }
                        if (addToList)
                            mListObj.add(jObj);
                    }
                }
            }
            if(isFirst) {
                isFirst = false;
                createBarcodeDB(mListObj);
            }
        }
    };



    private void searchBarcodes(ArrayList<JSONObject> mListObj) throws JSONException {
        ListWithBarcodes = new ArrayList<>();
        for(int i = 0; i < mListObj.size(); i++) {
            JSONObject jsonObject = mListObj.get(i);
            String barcode = jsonObject.getString("BARCODE");
            MyTask myTask = new MyTask();
            MyTaskParams params = new MyTaskParams(i, barcode);
            myTask.execute(params);
            ListWithBarcodes.add(jsonObject);
        }
    }

    private void createBarcodeDB(ArrayList<JSONObject> mListObj) throws IOException, JSONException {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File myDir = new File(root + "/BarcodeScannerAddonData");
        myDir.mkdirs();
        myDir.setExecutable(true);
        myDir.setReadable(true);
        myDir.setWritable(true);
        File csv = new File (myDir, "Barcodes.csv");
        CSVWriter writer = new CSVWriter(new FileWriter(csv), ';');
        ArrayList<String[]> arrayOfArrays = new ArrayList<>();
        String[] stringArray1 = new String[10];
        stringArray1[0] = "ID";
        stringArray1[1] = "DATE";
        stringArray1[2] = "TIME";
        stringArray1[3] = "NAME";
        stringArray1[4] = "AMOUNT";
        stringArray1[5] = "BARCODE";
        stringArray1[6] = "BARCODENAME";
        stringArray1[7] = "SSB";
        stringArray1[8] = "Long";
        stringArray1[9] = "LAT";
        writer.writeNext(stringArray1);
        for (int i = 0; i < mListObj.size(); i++) {
            JSONObject innerJsonArray =  mListObj.get(i);
            stringArray1[0]= innerJsonArray.getString("USER_ID");
            stringArray1[1]= innerJsonArray.getString("DATE");
            stringArray1[2]= innerJsonArray.getString("TIME");
            stringArray1[3]= innerJsonArray.getString("NAME");
            stringArray1[4]= innerJsonArray.getString("AMOUNT");
            stringArray1[5]= innerJsonArray.getString("BARCODE");
            stringArray1[6]= "-";
            stringArray1[7]= innerJsonArray.getString("SSB");
            stringArray1[8]= innerJsonArray.getString("LONGITUDE");
            stringArray1[9]= innerJsonArray.getString("LATITUDE");
            arrayOfArrays.add(stringArray1);
            writer.writeNext(arrayOfArrays.get(i));
        }
        writer.close();
    }

    private void createSurveyDB(ArrayList<JSONObject> mListObj) throws IOException, JSONException {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File myDir = new File(root + "/BarcodeScannerAddonData");
        myDir.mkdirs();
        myDir.setExecutable(true);
        myDir.setReadable(true);
        myDir.setWritable(true);
        File csv = new File (myDir, "Umfrageergebnisse.csv");
        CSVWriter writer = new CSVWriter(new FileWriter(csv), ';');
        String[][] arrayOfArrays = new String[(mListObj.size() *34) + 1][];
        int lineCounter = 1;
        String[] stringArray1 = new String[8];
        stringArray1[0]= "ID";
        stringArray1[1]= "DATE";
        stringArray1[2]= "TIME";
        stringArray1[3]= "SURVEY";
        stringArray1[4]= "QUESTION";
        stringArray1[5]= "ANSWER";
        stringArray1[6]= "DAY";
        stringArray1[7]= "SIGNAL";
        arrayOfArrays[0] = stringArray1;
        writer.writeNext(arrayOfArrays[0]);
        for (int i = 0; i < mListObj.size(); i++) {
            JSONObject innerJsonArray =  mListObj.get(i);
            for (int j = 0; j < innerJsonArray.getJSONArray("RESULT").length(); j++) {
                stringArray1[0]= innerJsonArray.getString("USER_ID");
                stringArray1[1]= innerJsonArray.getString("DATE");
                stringArray1[2]= innerJsonArray.getString("TIME");
                stringArray1[3]= innerJsonArray.getString("SURVEY");
                stringArray1[4]= String.valueOf(j + 1);
                Integer result = Integer.valueOf(innerJsonArray.getJSONArray("RESULT").getString(j));
                if (result != -1) {
                    switch (Integer.valueOf(stringArray1[3])) {
                        case 4:
                            result = result - 1;
                            break;
                        case 5:
                            result = result * 10;
                            break;
                    }
                }
                stringArray1[5]= String.valueOf(result);
                stringArray1[6]= innerJsonArray.getString("DAY");
                stringArray1[7]= innerJsonArray.getString("SIGNAL");
                arrayOfArrays[lineCounter] = stringArray1;
                writer.writeNext(arrayOfArrays[lineCounter]);
                lineCounter++;
            }
        }
        writer.close();
        Toast.makeText(this, "DB created .", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    /**
     * Service connection to myHealthHub remote service. This connection is
     * needed in order to start myHealthHub. Furthermore, it is used inform the
     * application about the connection status.
     */
    private final ServiceConnection myHealthAssistantRemoteConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Toast.makeText(getApplicationContext(),
                    "Connected to myHealthAssistant", Toast.LENGTH_SHORT)
                    .show();
            isConnectedToMhh = true;

            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(getApplicationContext(),
                    "disconnected with myHealthAssistant", Toast.LENGTH_SHORT)
                    .show();
            isConnectedToMhh = false;
        }
    };


    /** setting up the connection with myHealthHub */
    private void connectToMhh() {
        if (!isConnectedToMhh) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setTitle("Connect to myHealthHub");
            progressDialog.setMessage("Loading...");
            progressDialog.setCancelable(false);
            progressDialog.setIndeterminate(true);
            progressDialog.show();

            myHealthHubIntent = new Intent("de.tudarmstadt.dvs.myhealthassistant.myhealthhub.IMyHealthHubRemoteService");
            myHealthHubIntent.setPackage("de.tudarmstadt.dvs.myhealthassistant.myhealthhub");
            this.getApplicationContext()
                    .bindService(myHealthHubIntent,
                            myHealthAssistantRemoteConnection,
                            Context.BIND_AUTO_CREATE);
        }
    }

    private void disconnectMHH() {
        if (isConnectedToMhh) {
            this.getApplicationContext().unbindService(
                    myHealthAssistantRemoteConnection);
            isConnectedToMhh = false;
        }
        if (myHealthHubIntent != null)
            this.getApplicationContext().stopService(myHealthHubIntent);
    }


    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private class MyTask extends AsyncTask<MyTaskParams, String, String> {
        private int itemNumber;
        public MyTask() {

        }

        @Override
        protected void onPreExecute() {
            pbar.setVisibility(View.VISIBLE);
            pbar.setMax(ListWithBarcodes.size() + 1);
        }

        @Override
        protected String doInBackground(MyTaskParams... params) {
            itemNumber = params[0].itemNumber;
            String barcode = params[0].barcode;
            HttpURLConnection urlConnection = null;
            URL url;
            String[] data = new String[19];
            for (int i = 0; i < 19; i++) {
                data[i] = "";
            }
            int errorLine = 0;
            InputStream inStream = null;
            StringBuilder sb = new StringBuilder();
            sb.append("http://opengtindb.org/?ean=");
            sb.append(barcode);
            sb.append("&cmd=query&queryid=477909028");
            try {
                url = new URL(sb.toString());
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.connect();
                inStream = urlConnection.getInputStream();
                BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));
                String temp;
                int i = 0;
                while ((temp = bReader.readLine()) != null) {
                    if (temp.contains("error")) {
                        errorLine = i;
                    }
                    data[i] = temp;
                    i++;
                }
                if (errorLine == 0) {
                    return "-";
                }
            } catch (Exception e) {

            } finally {
                if (inStream != null) {
                    try {
                        // this will close the bReader as well
                        inStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            if (!data[errorLine].split("=")[1].equals("0")) {
                return "-";
            }
            String name = data[errorLine + 4].split("=")[1];
            if (name.equals("")) {
                return data[errorLine + 5].split("=")[1];
            }
            return name;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.e("counter", String.valueOf(counter));
            Log.e("barcode", result);
            counter++;
            pbar.setProgress(counter);
            if (counter == ListWithBarcodes.size()) {
                pbar.setVisibility(View.INVISIBLE);
            }
            JSONObject jsonObj = ListWithBarcodes.get(itemNumber);
            try {
                jsonObj.put("BARCODENAME", result);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            ListWithBarcodes.set(itemNumber, jsonObj);
        }
    }

    private static class MyTaskParams {
        int itemNumber;
        String barcode;

        MyTaskParams(int itemNumber, String barcode) {
            this.itemNumber = itemNumber;
            this.barcode = barcode;
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnectMHH();
    }
}
