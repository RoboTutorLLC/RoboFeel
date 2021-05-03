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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Trace;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.ImageUtils;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.PI;
import static java.lang.Math.atan2;
import static java.lang.Math.log;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */

public class finalOnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    //324, 648, 972, 1296, 224, 448, 672, 976, 1344
    private static final int INPUT_SIZE = 976;
    private static final String TAG = "finalOnGetImageListener";
    private static final Map<Integer, String> emotionMap = createMap();
    private int mScreenRotation = 90;
    private List<VisionDetRet> results;
    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mResizedBitmap = null;
    private Bitmap mInversedBipmap = null;
    private boolean mIsComputing = false;
    private Handler mInferenceHandler;
    private Context mContext;
    private FaceDet mFaceDet;
    private ImageView imageView;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;
    private Paint myPaint;
    private Paint bluePaint;
    private int mframeNum = 0;
    private EmotionInference emotionInference;
    private String filename;
    private Canvas canvas;
    private ArrayList<Point> landmarks;
    private int count = 0;
    private File mediaStorageDir;
    private File mediaStorageDir2;
    private File mediaStorageDir3;
    private BufferedWriter bufferedWriter;
    private String LogFileName;
    private String tempLogFileName;
    private ArrayList<String> boxesList;
    private ArrayList<ArrayList> landmarksList;
    private String logOrNot;
    private String SaveOrNot;
    private File fileLogfile;
    private double imagefileCounter;

    private static Map<Integer, String> createMap() {
        Map<Integer, String> myMap = new HashMap<>();

        myMap.put(0, "Bored");
        myMap.put(1, "Confused");
        myMap.put(2, "Delighted");
        myMap.put(3, "Frustrated");


        return myMap;
    }

    public void initialize(
            final Context context,
            final AssetManager assetManager,
            final TrasparentTitleView scoreView,
            final Handler handler) throws IOException {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);
        mWindow.release();
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

        emotionInference = new EmotionInference(context);
        imagefileCounter = 0;
        mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "RoboFeel");
        //RoboFeel directory
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("App", "failed to create directory - Robofeel");
            }
        }
        mediaStorageDir2 = new File(mediaStorageDir, "Images");
        //images directory
        if (!mediaStorageDir2.exists()) {
            if (!mediaStorageDir2.mkdirs()) {
                Log.d("App", "failed to create directory - images");
            }
        }


        try {
            logOrNot = getToLogOrNotVariable();
        } catch (JSONException e) {
            //default
            logOrNot = "false";
            e.printStackTrace();
        }

        try {
            SaveOrNot = getToSaveImagesOrNotVariable();
        } catch (JSONException e) {
            //default
            SaveOrNot = "false";
            e.printStackTrace();
        }

        Date currentTime = Calendar.getInstance().getTime();
        Log.d("Listener","Creating log file for current suession");
        if(logOrNot.equals("true"));
        {
            mediaStorageDir3 = new File(mediaStorageDir2, "Images_"+currentTime);
            //images per session directory
            if (!mediaStorageDir3.exists()) {
                if (!mediaStorageDir3.mkdirs()) {
                    Log.d("App", "failed to create directory - images per session");
                }
            }

            LogFileName = mediaStorageDir + "/RoboFeelLogs_" + currentTime + ".json";
            tempLogFileName = mediaStorageDir + "/TEMPRoboFeelLogs_" + currentTime + ".json";
            // Define the File Path and its Name
            fileLogfile = new File(LogFileName);
            FileWriter fileWriter = new FileWriter(fileLogfile);
            bufferedWriter = new BufferedWriter(fileWriter);
        }

    }

    public void deInitialize() throws Exception {
        synchronized (finalOnGetImageListener.this) {
            cleanLogFile();
            if (mFaceDet != null) {
                mFaceDet.release();
            }

            if (mWindow != null) {
                mWindow.release();
            }
        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public void cleanLogFile() throws Exception {
        bufferedWriter.close();
        File file = new File(tempLogFileName);
        Log.d(TAG,"Delete last incomplete entry");
        FileInputStream reader = new FileInputStream(LogFileName);

        BufferedWriter writer = new BufferedWriter(new FileWriter(tempLogFileName));
        String jsonfiletext = convertStreamToString(reader);
        String finalText_for_comma = jsonfiletext;
        try {
            String finalText_for_bracket = jsonfiletext.substring(0, jsonfiletext.lastIndexOf('{'));
            finalText_for_comma = finalText_for_bracket.substring(0, finalText_for_bracket.length() - 1);
        }
        catch(Exception e){
            e.printStackTrace();
        }

        writer.write(finalText_for_comma);
        fileLogfile.delete();

        if(file.renameTo(fileLogfile)) {
            Log.d(TAG,"renamed");
        } else {
            Log.d(TAG,"Error");
        }

        writer.close();
        reader.close();
    }



    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;
        //Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = -90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }

        if (Build.MODEL.equals("Nexus 6P")){
            // rotate camera 180°
            //Log.d(TAG, "Nexus 6p device being used.");
            mScreenRotation = +90;
        }

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    public Bitmap imageSideInversion(Bitmap src) {
        Matrix sideInversion = new Matrix();
        sideInversion.setScale(-1, 1);
        Bitmap inversedImage = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), sideInversion, false);
        return inversedImage;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Log.e("finalOnGetImageListener","Image Avaialble");
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            /*
            TO LOG:
            1. imageFilename
            2. Number of faces detected
            3. Bounding boxes of faces
            4. Landmarks
            5. HOG

             */
            if (image == null) {
                Log.d(TAG,"returning null");
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                Log.d(TAG,"seting sizes..");
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();


                //Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            Log.d("Planes length", String.valueOf(planes.length));
            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        mInversedBipmap = imageSideInversion(mCroppedBitmap);
        mResizedBitmap = Bitmap.createScaledBitmap(mInversedBipmap, (int) (INPUT_SIZE / 4.5), (int) (INPUT_SIZE / 4.5), true);

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                            //mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                        }

                        if (mframeNum % 10 == 0) {
                            long startTime = System.currentTimeMillis();
                            synchronized (finalOnGetImageListener.this) {
                                Log.e(TAG,"detect being called");
                                results = mFaceDet.detect(mResizedBitmap);
                                if (results != null) {
                                    Log.e(TAG,results.toString());
                                }

                            }
                            long endTime = System.currentTimeMillis();
                            //mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                            Log.e("onGetImageListener",results.toString());

                            // Draw on bitmap
                            boxesList = new ArrayList();
                            landmarksList = new ArrayList<>();
                            if (results.size() != 0) {
                                for (final VisionDetRet ret : results) {
                                    float resizeRatio = 4.5f;

                                    // Draw landmark
                                    landmarks = ret.getFaceLandmarks();
                                    landmarksList.add(landmarks);
                                    boxesList.add(ret.toString());
                                    String s = ret.getLabel();
                                    Log.d(TAG, "label = " + s);

                                    findEmotion(landmarks, resizeRatio);

                                }

                            }
                        }
                             /*
                                        TO LOG:
                                        1. imageFilename
                                        2. Number of faces detected
                                        3. Bounding boxes of faces
                                        4. Landmarks
                                        5. HOG

                                         */
                        // Draw on bitmap
                        if (results.size() != 0) {
                            /*
                            READ CONFIG.JSON AND SEE IF VALUES HAVE TO BE LOGGED OR NOT
                            */

                            if(logOrNot.equalsIgnoreCase("true")) {
                                //logging code
                                Date imageTime = Calendar.getInstance().getTime();
                                Date time = Calendar.getInstance().getTime();

                                filename = mediaStorageDir3 + "/images_" + imageTime.toString() + "_" + imagefileCounter + ".jpg";
                                if(SaveOrNot.equalsIgnoreCase("true")){
                                    //to save images or not
                                    try (FileOutputStream out = new FileOutputStream(filename)) {
                                        //save bitmap
                                        mResizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                                        // PNG is a lossless format, the compression factor (100) is ignored
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                else {
                                    filename="Image-not-saved";
                                }

                                imagefileCounter++;
                                JSONObject objectToLog = new JSONObject();
                                try {
                                    objectToLog.put("ImageFilename", filename);
                                    objectToLog.put("Number of Faces", results.size());
                                    objectToLog.put("Bounding boxes", boxesList);
                                    objectToLog.put("Landmarks", landmarksList);

                                    String userString = objectToLog.toString();

                                    bufferedWriter.write(userString);
                                    bufferedWriter.write(",");

                                } catch (JSONException | IOException e) {
                                    e.printStackTrace();
                                }

                            }
                            // Draw on bitmap
                            for (final VisionDetRet ret : results) {
                                float resizeRatio = 4.5f;

                                // Draw landmark
                                landmarks = ret.getFaceLandmarks();

                                String s = ret.getLabel();

                                Log.d(TAG, "label = " + s);

                                //findEmotion(landmarks, resizeRatio);

                                drawOnBitmap(landmarks, resizeRatio);

                            }


                        }


                        mframeNum++;
                       // mWindow.setRGBBitmap(mInversedBipmap);

                        mIsComputing = false;
                    }

                });

        Trace.endSection();
    }

    private void drawOnBitmap(ArrayList<Point> landmarks, float resizeRatio) {

        Canvas canvas = new Canvas(mInversedBipmap);

        float[] xlist = new float[68];
        float[] ylist = new float[68];

        float x_coord;
        float y_coord;
        float x_mean;
        float y_mean;

        int index = 0;
        float x_sum = 0;
        float y_sum = 0;

        for (Point point : landmarks) {
            int pointX = (int) (point.x * resizeRatio);
            int pointY = (int) (point.y * resizeRatio);
            canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);

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

        x_mean = x_sum / index;
        y_mean = y_sum / index;


        canvas.drawCircle(x_mean, y_mean, 4, bluePaint);

        for (int i = 0; i < 68; i++) {
            canvas.drawLine(x_mean, y_mean, xlist[i], ylist[i], myPaint);
        }
    }

    private String getToLogOrNotVariable() throws IOException, JSONException {
        String dataPath = "/sdcard/Download" + "/config.json";
        String logOrNot;
        InputStream is = new FileInputStream(dataPath);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        String myJson = new String(buffer, "UTF-8");
        JSONObject obj = new JSONObject(myJson);
        logOrNot = obj.getString("robofeel_log_items_or_not");
        Log.d("myJson-LogOrNot",logOrNot);
        return logOrNot;
    }


    private String getToSaveImagesOrNotVariable() throws IOException, JSONException {
        String dataPath = "/sdcard/Download" + "/config.json";
        String SaveOrNot;
        InputStream is = new FileInputStream(dataPath);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        String myJson = new String(buffer, "UTF-8");
        JSONObject obj = new JSONObject(myJson);
        SaveOrNot = obj.getString("robofeel_save_images_or_not");
        Log.d("myJson-LogOrNot",SaveOrNot);
        return SaveOrNot;
    }



    private void findEmotion(ArrayList<Point> landmarks, float resizeRatio) {

        Log.d("Emotion", "Finding Emotion");

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
            //canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);
            //Log.d(TAG, "Landmark number - " + count);
            //Log.d(TAG, "X, Y Coordinates = (" + pointX + "," + pointY + ")");
            //count++;

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


        //canvas.drawCircle(x_mean, y_mean, 4, bluePaint);

        //for (int i = 0; i < 68; i++) {
        //    canvas.drawLine(x_mean, y_mean, xlist[i], ylist[i], myPaint);
        //}


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
            emotionPrediction(input);
            Log.d(TAG, "Size of input = " + required_size);
            Log.d(TAG, "Input = " + input.toString());
        }

    }

    private void emotionPrediction(List input) {

        List random_list;
        random_list = new ArrayList();

        float n1 = 1;
        float n2 = 2;
        float n3 = 3;
        float n4 = 4;
        float n5 = 5;
        float n6 = 6;
        random_list.add(n1);
        random_list.add(n2);
        random_list.add(n3);
        random_list.add(n4);
        random_list.add(n5);
        random_list.add(n6);


        Log.d("Mayank", "Random list = " + random_list.toString());

        float[] random_array = toFloatArray(random_list);

        Log.d("Mayank", "Random array = " + random_array.toString());

        float[] results = emotionInference.getEmotionProb(toFloatArray(input));

        Log.d(TAG, "In emotion prediction function");
        Log.d("Mayank", "results = " + results.toString());

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
            //Toast.makeText(mContext.getApplicationContext(), "Emotion = " + emotion, Toast.LENGTH_SHORT).show();
            try {
                mTransparentTitleView.setText("Emotion = " + emotion);
            }
            catch(Exception e)
            {
                Log.d(TAG, String.valueOf(e.getStackTrace()));
            }

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
