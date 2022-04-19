package com.example.contacttracingapplication;

import static java.lang.Math.abs;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.contacttracingapplication.database.ContactTracingDatabase;
import com.example.contacttracingapplication.database.ContactTracingModel;
import com.example.contacttracingapplication.services.CalculateHeartRateService;
import com.example.contacttracingapplication.services.CalculateRespiratoryRateService;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;

public class Assignment1MainScreen extends AppCompatActivity {

    private TextView heartRateMeasureText;
    private TextView breathingRateMeasureText;
    private float breathingRate;
    private String rootDirectoryPath = Environment.getExternalStorageDirectory().getPath();
    private boolean heartRateMeasureInProgress = false;
    private static final int CAPTURE_VIDEO = 101;
    private Uri filePath;
    private ContactTracingDatabase contactTracingDatabase;
    private boolean uploadSignsButtonClicked = false;
    private boolean breathingRateMeasureInProgress = false;
    private int windows = 9;
    long startExecutionTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assignment1_main_screen);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Button recordHeartVideoButton = (Button) findViewById(R.id.record_video_button);
        Button uploadSignsButton = (Button) findViewById(R.id.upload_signs_button);
        Button heartRateMeasureButton = (Button) findViewById(R.id.measure_heart_rate_button);
        Button uploadSymptomsButton = (Button) findViewById(R.id.log_symptoms_button);
        Button breatheRateMeasureButton = (Button) findViewById(R.id.measure_respiratory_rate_button);
        breathingRateMeasureText = (TextView) findViewById(R.id.breathing_rate_value);
        heartRateMeasureText = (TextView) findViewById(R.id.heart_rate_value);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    contactTracingDatabase = ContactTracingDatabase.getDBInstance(getApplicationContext(), "");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

        if(!checkIfHasCamera()){
            recordHeartVideoButton.setEnabled(false);
        }
//        handlingPermission(Assignment1MainScreen.this);

        uploadSignsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                uploadSignsButtonClicked = true;
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ContactTracingModel data = new ContactTracingModel();
                        data.heart_rate_value = Float.parseFloat(heartRateMeasureText.getText().toString());
                        data.breathing_rate_value = breathingRate;
                        data.timestamp = new Date(System.currentTimeMillis());
                        contactTracingDatabase.userInfoDao().insert(data);
                    }
                });
                thread.start();

                Toast.makeText(Assignment1MainScreen.this, "Uploading signs successful!", Toast.LENGTH_SHORT).show();
            }

        });

        heartRateMeasureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                File videoFileLocation = new
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()
                        + "/heart_rate_value.mp4");
                filePath = Uri.fromFile(videoFileLocation);
                if(heartRateMeasureInProgress == true) {
                    Toast.makeText(Assignment1MainScreen.this, "The heart rate is being calculated. Please wait!", Toast.LENGTH_SHORT).show();
                } else if (videoFileLocation.exists()) {
                    heartRateMeasureInProgress = true;
                    heartRateMeasureText.setText("Calculating...");

                    startExecutionTime = System.currentTimeMillis();
                    System.gc();
                    Intent intentForHeartRate = new Intent(Assignment1MainScreen.this, CalculateHeartRateService.class);
                    startService(intentForHeartRate);

                } else {
                    Toast.makeText(Assignment1MainScreen.this, "Please record a video for measuring heart rate", Toast.LENGTH_SHORT).show();
                }
            }
        });

        uploadSymptomsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(Assignment1MainScreen.this, LogCOVIDSymptomsScreen.class);
                intent.putExtra("uploadSignsButtonClicked", uploadSignsButtonClicked);
                startActivity(intent);
            }
        });

        breatheRateMeasureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(breathingRateMeasureInProgress == true) {
                    Toast.makeText(Assignment1MainScreen.this, "The respiratory rate is being calculated. Please wait!",
                            Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(Assignment1MainScreen.this, "Place the phone on your abdomen \nfor 50s to measure your respiratory rate", Toast.LENGTH_LONG).show();
                    breathingRateMeasureInProgress = true;
                    breathingRateMeasureText.setText("Measuring..");
                    Intent intentForBreathingRate = new Intent(Assignment1MainScreen.this, CalculateRespiratoryRateService.class);
                    startService(intentForBreathingRate);
                }
            }
        });

        recordHeartVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(heartRateMeasureInProgress == true) {
                    Toast.makeText(Assignment1MainScreen.this, "Your heart rate is being calculated. Please wait", Toast.LENGTH_SHORT).show();
                } else {
                    startRecordingVideo();
                }
            }
        });

        LocalBroadcastManager.getInstance(Assignment1MainScreen.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle bundle = intent.getExtras();
                DetectBreathingRate detectBreathingRateRunnable = new DetectBreathingRate(bundle.getIntegerArrayList("xAcceleratorValues"));

                Thread thread = new Thread(detectBreathingRateRunnable);
                thread.start();

                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                breathingRate = detectBreathingRateRunnable.currentBreathingRate;
                breathingRateMeasureText.setText("Your respiratory rate is: " + detectBreathingRateRunnable.currentBreathingRate + "");

                Toast.makeText(Assignment1MainScreen.this, "Yay! Respiratory rate calculation completed!", Toast.LENGTH_SHORT).show();
                breathingRateMeasureInProgress = false;
                bundle.clear();
                System.gc();

            }
        }, new IntentFilter("broadcastingAcceleratorRespiratoryData"));

        LocalBroadcastManager.getInstance(Assignment1MainScreen.this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                float currentHeartRate = 0;
                int fail = 0;
                Bundle bundle = intent.getExtras();
                //Processes 9 windows of 5 second video snippets separately to calculate heart rate
                for (int i = 0; i < windows; i++) {

                    ArrayList<Integer> currentHeartData = null;
                    currentHeartData = bundle.getIntegerArrayList("heartData"+i);
                    ArrayList<Integer> refactoredNoiseRedness = removeNoise(currentHeartData, 5);
                    float peakFindZeroCrossing = findPeak(refactoredNoiseRedness);
                    currentHeartRate += peakFindZeroCrossing/2;
                    Log.i("log", "heart rate for " + i + ": " + peakFindZeroCrossing/2);

                    String pathToCSVFile = rootDirectoryPath + "/heart_rate_value" + i + ".csv";
                    savingStuffToCSV(currentHeartData, pathToCSVFile);
                    pathToCSVFile = rootDirectoryPath + "/heart_rate_value_denoised" + i + ".csv";
                    savingStuffToCSV(refactoredNoiseRedness, pathToCSVFile);
                }

                currentHeartRate = (currentHeartRate*12)/ windows;
                Log.i("log", "Final heart rate: " + currentHeartRate);
                heartRateMeasureText.setText(currentHeartRate + "");
                heartRateMeasureInProgress = false;
                Toast.makeText(Assignment1MainScreen.this, "Yay! Heart rate calculated!", Toast.LENGTH_SHORT).show();
                System.gc();
                bundle.clear();

            }
        }, new IntentFilter("broadcastingHeartRateData"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        uploadSignsButtonClicked = false;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        boolean doDeleteFile = false;
        if (requestCode == CAPTURE_VIDEO) {
            if (resultCode == RESULT_OK) {

                FileInputStream inputFileStream = null;
                MediaMetadataRetriever videoMetadataRetriever = new MediaMetadataRetriever();
                try {
                    inputFileStream = new FileInputStream(filePath.getPath());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                try {
                    videoMetadataRetriever.setDataSource(inputFileStream.getFD());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String timeStringMetadata = videoMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long time = Long.parseLong(timeStringMetadata)/1000;

                if(time<45) {
                    Toast.makeText(this, "Please record your heart rate for atleast 45 seconds to process! ", Toast.LENGTH_SHORT).show();
                    doDeleteFile = true;
                } else{
                    Toast.makeText(this, "Your heart measure video has been saved to:\n" + data.getData(), Toast.LENGTH_SHORT).show();
                }

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Uh-oh! You cancelled video recording for measuring your heart rate", Toast.LENGTH_SHORT).show();
                doDeleteFile = true;
            } else {
                Toast.makeText(this, "Uh-oh! Failed to record video. Try again", Toast.LENGTH_SHORT).show();
            }

            if(doDeleteFile) {
                File fileDelete = new File(filePath.getPath());

                if (fileDelete.exists()) {
                    if (fileDelete.delete()) {
                        System.out.println("Recording deleted");
                    }
                }
            }
            filePath = null;
        }
    }

    public class DetectBreathingRate implements Runnable{
        DetectBreathingRate(ArrayList<Integer> xAcceleratorValues){
            this.xAcceleratorValues = xAcceleratorValues;
        }

        ArrayList<Integer> xAcceleratorValues;
        public float currentBreathingRate;

        @Override
        public void run() {

            String pathToCSVFile = rootDirectoryPath + "/x_values.csv";
            savingStuffToCSV(xAcceleratorValues, pathToCSVFile);

            //Noise reduction from Accelerometer X values
            ArrayList<Integer> accelValuesXDenoised = removeNoise(xAcceleratorValues, 10);

            pathToCSVFile = rootDirectoryPath + "/x_values_denoised.csv";
            savingStuffToCSV(accelValuesXDenoised, pathToCSVFile);

            //Peak detection algorithm running on denoised Accelerometer X values
            int  denoisedPeakDetection = findPeak(accelValuesXDenoised);
            currentBreathingRate = (denoisedPeakDetection*60)/90;
            Log.i("log", "Respiratory rate" + currentBreathingRate);
        }

    }

    public void savingStuffToCSV(ArrayList<Integer> data, String path){

        File file = new File(path);

        try {
            FileWriter outputFileWriter = new FileWriter(file);
            CSVWriter csvWriter = new CSVWriter(outputFileWriter);
            String[] columns = { "Index", "Data"};
            csvWriter.writeNext(columns);
            int i = 0;
            for (int d : data) {
                String dataRow[] = {i + "", d + ""};
                csvWriter.writeNext(dataRow);
                i++;
            }
            csvWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public ArrayList<Integer> removeNoise(ArrayList<Integer> data, int filter){
        int movingAverage = 0;
        ArrayList<Integer> movingAverageSlidingWindow = new ArrayList<>();

        for(int i=0; i< data.size(); i++){
            movingAverage += data.get(i);
            if(i+1 < filter) {
                continue;
            }
            movingAverageSlidingWindow.add((movingAverage)/filter);
            movingAverage -= data.get(i+1 - filter);
        }

        return movingAverageSlidingWindow;

    }

    public void startRecordingVideo() {
        File locationMediaFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()  + "/heart_rate_value.mp4");

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT,30);
        filePath = Uri.fromFile(locationMediaFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, filePath);
        startActivityForResult(intent, CAPTURE_VIDEO);
    }

    public int findPeak(ArrayList<Integer> data) {
        int j = 0;
        int difference, previous, slope = 0, zeroCrossings = 0;
        previous = data.get(0);

        //Get initial slope
        while(slope == 0 && j + 1 < data.size()){
            difference = data.get(j + 1) - data.get(j);
            if(difference != 0){
                slope = difference/abs(difference);
            }
            j++;
        }

        //Get total number of zero crossings in data curve
        for(int i = 1; i<data.size(); i++) {

            difference = data.get(i) - previous;
            previous = data.get(i);

            if(difference == 0) continue;

            int currSlope = difference/abs(difference);

            if(currSlope == -1* slope){
                slope *= -1;
                zeroCrossings++;
            }
        }

        return zeroCrossings;
    }

    private boolean checkIfHasCamera() {
        if (getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    public static void handlingPermission(Activity activity) {
        int REQUEST_EXTERNAL_STORAGE = 1;
        int permissionToStore = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        String[] PERMISSIONS = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA

        };

        if (permissionToStore != PackageManager.PERMISSION_GRANTED) {
            Log.i("log", "Read/Write Permissions needed!");
        }

        ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS,
                REQUEST_EXTERNAL_STORAGE
        );
        Log.i("log", "Permissions Granted!");
    }
}
