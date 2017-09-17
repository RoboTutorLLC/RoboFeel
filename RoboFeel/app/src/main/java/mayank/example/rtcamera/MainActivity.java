/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mayank.example.rtcamera;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import com.dexafree.materialList.card.Card;
import com.dexafree.materialList.card.provider.BigImageCardProvider;
import com.dexafree.materialList.view.MaterialListView;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.PedestrianDet;
import com.tzutalin.dlib.VisionDetRet;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hugo.weaving.DebugLog;
import timber.log.Timber;

import static java.lang.Math.PI;
import static java.lang.Math.atan2;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {
    private static final int RESULT_LOAD_IMG = 1;
    private static final int REQUEST_CODE_PERMISSION = 2;

    private static final String TAG = "MainActivity";
    private static final Map<Integer, String> emotionMap = createMap();
    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };
    protected String mTestImgPath;
    // UI
    @ViewById(R.id.material_listview)
    protected MaterialListView mListView;
    @ViewById(R.id.fab)
    protected FloatingActionButton mFabActionBt;
    @ViewById(R.id.fab_cam)
    protected FloatingActionButton mFabCamActionBt;
    @ViewById(R.id.toolbar)
    protected Toolbar mToolbar;
    FaceDet mFaceDet;
    PedestrianDet mPersonDet;
    private Paint myPaint;
    private Paint bluePaint;
    private Paint mFaceLandmardkPaint;
    private EmotionInference emotionInference;
    // ==========================================================
    // Tasks inner class
    // ==========================================================
    private ProgressDialog mDialog;

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    @DebugLog
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_persmission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_persmission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    private static Map<Integer, String> createMap() {
        Map<Integer, String> myMap = new HashMap<>();
        myMap.put(0, "Neutral");
        myMap.put(1, "Frustration");
        myMap.put(2, "Confusion");
        myMap.put(3, "Boredum");
        myMap.put(4, "Surprise");
        myMap.put(5, "Delight");
        return myMap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListView = (MaterialListView) findViewById(R.id.material_listview);
        setSupportActionBar(mToolbar);
        // Just use hugo to print log
        isExternalStorageWritable();
        isExternalStorageReadable();

        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.M) {
            verifyPermissions(this);
        }

        //inferenceInterface.initializeTensorFlow(getAssets(), "file:///android_asset/optimized_rt.pb");


        //startActivity(new Intent(this, CameraActivity.class));

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);

        myPaint = new Paint();
        myPaint.setColor(Color.RED);
        myPaint.setStrokeWidth(1);
        myPaint.setStyle(Paint.Style.STROKE);

        bluePaint = new Paint();
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStrokeWidth(4);
        bluePaint.setStyle(Paint.Style.STROKE);

        emotionInference = new EmotionInference(getApplicationContext());

    }

    @AfterViews
    protected void setupUI() {
        mToolbar.setTitle(getString(R.string.app_name));
        Toast.makeText(MainActivity.this, getString(R.string.description_info), Toast.LENGTH_LONG).show();
    }

    @Click({R.id.fab})
    protected void launchGallery() {
        Toast.makeText(MainActivity.this, "Pick one image", Toast.LENGTH_SHORT).show();
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }

    @Click({R.id.fab_cam})
    protected void launchCameraPreview() {
        startActivity(new Intent(this, CameraActivity.class));
    }

    /* Checks if external storage is available for read and write */
    @DebugLog
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    @DebugLog
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    @DebugLog
    protected void demoStaticImage() {
        if (mTestImgPath != null) {
            Timber.tag(TAG).d("demoStaticImage() launch a task to det");
            runDetectAsync(mTestImgPath);
        } else {
            Timber.tag(TAG).d("demoStaticImage() mTestImgPath is null, go to gallery");
            Toast.makeText(MainActivity.this, "Pick an image to run algorithms", Toast.LENGTH_SHORT).show();
            // Create intent to Open Image applications like Gallery, Google Photos
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Toast.makeText(MainActivity.this, "Demo using static images", Toast.LENGTH_SHORT).show();
            demoStaticImage();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                // Get the Image from data
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                mTestImgPath = cursor.getString(columnIndex);
                cursor.close();
                if (mTestImgPath != null) {
                    runDetectAsync(mTestImgPath);
                    //Toast.makeText(this, "Img Path:" + mTestImgPath, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "You haven't picked Image", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG).show();
        }
    }

    @Background
    @NonNull
    protected void runDetectAsync(@NonNull String imgPath) {
        //showDiaglog();

        final String targetPath = Constants.getFaceShapeModelPath();
        if (!new File(targetPath).exists()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Copy landmark model to " + targetPath, Toast.LENGTH_SHORT).show();
                }
            });
            FileUtils.copyFileFromRawToOthers(getApplicationContext(), R.raw.shape_predictor_68_face_landmarks, targetPath);
        }
        // Init
        if (mPersonDet == null) {
            mPersonDet = new PedestrianDet();
        }
        if (mFaceDet == null) {
            mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        }

        Timber.tag(TAG).d("Image path: " + imgPath);
        List<Card> cardrets = new ArrayList<>();
        List<VisionDetRet> faceList = mFaceDet.detect(imgPath);
        if (faceList.size() > 0) {
            Card card = new Card.Builder(MainActivity.this)
                    .withProvider(BigImageCardProvider.class)
                    .setDrawable(drawRect(imgPath, faceList, Color.GREEN))
                    .setTitle("Face det")
                    .endConfig()
                    .build();
            cardrets.add(card);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "No face", Toast.LENGTH_SHORT).show();
                }
            });
        }

        /* List<VisionDetRet> personList = mPersonDet.detect(imgPath);
        if (personList.size() > 0) {
            Card card = new Card.Builder(MainActivity.this)
                    .withProvider(BigImageCardProvider.class)
                    .setDrawable(drawRect(imgPath, personList, Color.BLUE))
                    .setTitle("Person det")
                    .endConfig()
                    .build();
            cardrets.add(card);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "No person", Toast.LENGTH_SHORT).show();
                }
            });
        } */

        addCardListView(cardrets);
        dismissDialog();
    }

    @UiThread
    protected void addCardListView(List<Card> cardrets) {
        for (Card each : cardrets) {
            mListView.add(each);
        }
    }

    @UiThread
    protected void showDiaglog() {
        mDialog = ProgressDialog.show(MainActivity.this, "Wait", "Face detection", true);
    }

    @UiThread
    protected void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @DebugLog
    protected BitmapDrawable drawRect(String path, List<VisionDetRet> results, int color) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        android.graphics.Bitmap.Config bitmapConfig = bm.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bm = bm.copy(bitmapConfig, true);
        int width = bm.getWidth();
        int height = bm.getHeight();
        // By ratio scale
        float aspectRatio = bm.getWidth() / (float) bm.getHeight();

        final int MAX_SIZE = 512;
        int newWidth = MAX_SIZE;
        int newHeight = MAX_SIZE;
        float resizeRatio = 1;
        newHeight = Math.round(newWidth / aspectRatio);
        if (bm.getWidth() > MAX_SIZE && bm.getHeight() > MAX_SIZE) {
            Timber.tag(TAG).d("Resize Bitmap");
            bm = getResizedBitmap(bm, newWidth, newHeight);
            resizeRatio = (float) bm.getWidth() / (float) width;
            Timber.tag(TAG).d("resizeRatio " + resizeRatio);
        }

        // Create canvas to draw
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        // Loop result list
        for (VisionDetRet ret : results) {
            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            canvas.drawRect(bounds, paint);

            /*// Get landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                canvas.drawCircle(pointX, pointY, 2, paint);
            }*/

            // Draw landmark
            int count = 0;
            ArrayList<Point> landmarks = ret.getFaceLandmarks();

            String s = ret.getLabel();
            Log.d(TAG, "label = " + s);

            ArrayList<Point> l = ret.getFaceLandmarks();

            int index = 0;
            float x_sum = 0;
            float y_sum = 0;


            float[] xlist = new float[68];
            float[] ylist = new float[68];

            float x_coord;
            float y_coord;
            float x_mean;
            float y_mean;


            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);
                //Log.d(TAG, "Landmark number - " + count);
                //Log.d(TAG, "X, Y Coordinates = (" + pointX + "," + pointY + ")");
                count++;

                x_coord = pointX;
                y_coord = pointY;

                //Log.d("TAG", "x coord = " + x_coord);
                //Log.d("TAG", "y coord = " + y_coord);

                x_sum += x_coord;
                y_sum += y_coord;

                //Log.d("TAG", "x sum = " + x_sum);
                //Log.d("TAG", "y sum= " + y_sum);

                xlist[index] = x_coord;
                ylist[index] = y_coord;

                index++;

            }

            //Log.d(TAG, "Xlist = " + xlist);
            //Log.d(TAG, "Ylist = " + ylist.toString());

            x_mean = x_sum / index;
            y_mean = y_sum / index;

            //Log.d(TAG, "x_central = " + x_mean);
            //Log.d(TAG, "y_central = " + y_mean);

            //Log.d(TAG, "Index = " + index);

            //Log.d("TAG", "OUTSIDE LOOP");
            //Log.d("TAG", "Index = " + index);
            //Log.d("TAG", "x sum = " + x_sum);
            //Log.d("TAG", "y sum= " + y_sum);


            canvas.drawCircle(x_mean, y_mean, 4, bluePaint);

            for (int i = 0; i < 68; i++) {
                canvas.drawLine(x_mean, y_mean, xlist[i], ylist[i], myPaint);
            }


            float[] xcentral = new float[68];
            float[] ycentral = new float[68];
            float n1;
            float n2;

            for (int i = 0; i < 68; i++) {
                n1 = xlist[i] - x_mean;
                n2 = ylist[i] - y_mean;

                xcentral[i] = n1;
                ycentral[i] = n2;
            }

            float[] landmarks_vectorised = new float[136];
            int landmarks_index = 0;
            float max = -1;

            for (int i = 0; i < 68; i++) {

                float d = (float) Math.sqrt(Math.pow(xcentral[i] - x_mean, 2) + Math.pow(ycentral[i] - y_mean, 2));
                landmarks_vectorised[landmarks_index] = (float) d;
                //Log.d(TAG, "landmarks_index = " + landmarks_index + " d = " + d);
                if (max <= d) max = d;

                landmarks_index++;

                float angle = (float) (atan2(ycentral[i] - y_mean, xcentral[i] - x_mean) * 180 / PI);
                landmarks_vectorised[landmarks_index] = (float) angle;
                //Log.d(TAG, "landmarks_index = " + landmarks_index + " angle = " + angle);
                if (max <= angle) max = angle;

                landmarks_index++;

            }

            List input;
            input = new ArrayList();

            for (int i = 0; i < landmarks_vectorised.length; i++) {
                //Log.d(TAG, "index = " + i + " val = " + landmarks_vectorised[i]);
                float n;
                n = landmarks_vectorised[i];
                n = n / max;
                input.add(n);
            }

            int required_size = 136;
            if (input.size() == required_size) {// calling emotionPrediction function only when input size is 136
                Log.d(TAG, "Input = " + input.toString());
                Log.d(TAG, "Size of input = " + required_size);
                //dismissDialog();
                emotionPrediction(input);
            }
        }

        return new BitmapDrawable(getResources(), bm);
    }

    @DebugLog
    protected Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
        return resizedBitmap;
    }

    private void emotionPrediction(List input) {

        float[] results = emotionInference.getEmotionProb(toFloatArray(input));

        Log.d(TAG, "In emotion prediction function");

        if (results.length > 0) {
            Log.d(TAG, "Length of results > 0");

            float max_emotion = -1;
            int max_index = -1;

            for (int i = 0; i < results.length; i++) {
                Log.d(TAG, "results[" + i + "] = " + results[i]);
                if (max_emotion < results[i]) {
                    max_emotion = results[i];
                    max_index = i;
                }
            }

            String emotion = emotionMap.get(max_index);
            Log.d(TAG, "emotion = " + emotion);
            //Toast.makeText(getApplicationContext(), "Emotion = " + emotion, Toast.LENGTH_SHORT).show();

        }

    }

    private float[] toFloatArray(List list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Object f : list) {
            array[i++] = f != null ? (float) f : Float.NaN;
        }
        return array;
    }
}
