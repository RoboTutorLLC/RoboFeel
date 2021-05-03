package mayank.example.rtcamera;

import android.Manifest;
import android.annotation.SuppressLint;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import hugo.weaving.DebugLog;
import timber.log.Timber;

public class RoboService extends Service {
    protected static final int CAMERA_CALIBRATION_DELAY = 500;
    protected static final String TAG = "RoboService";
    protected static final int CAMERACHOICE = CameraCharacteristics.LENS_FACING_BACK;
    protected static long cameraCaptureStartTime;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;
    private HandlerThread backgroundThread;
    private finalOnGetImageListener mListener;
    private SurfaceHolder mSurfaceHolder;
    private static Camera mServiceCamera;
    private SurfaceView mSurfaceView;
    private ImageView mImageView;
    private Size previewSize;
    private static final int MINIMUM_PREVIEW_SIZE = 320;
    private Notification notification;
    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingStatus;
    private MediaRecorder mMediaRecorder;
    private SessionVariables sessionVariables;


    private static final int LayoutParamFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

    private LayoutInflater inflater;
    private Display mDisplay;
    private View layoutView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler backgroundHandler;

    /**
     * An additional thread for running inference so as not to block the camera.
     */
    private HandlerThread inferenceThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler inferenceHandler;

    /**
     * An {@link ImageReader} that handles preview frame capture.
     */

    protected CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            actOnReadyCameraDevice();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback onError " + error);
        }
    };
    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {
                }
            };


    protected CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onReady(CameraCaptureSession session) {
            RoboService.this.session = session;
            try {
                session.setRepeatingRequest(createCaptureRequest(), captureCallback, backgroundHandler);
                cameraCaptureStartTime = System.currentTimeMillis();
            } catch (CameraAccessException | IllegalStateException e) {
                Log.e(TAG, e.getMessage());
            }
        }


        @Override
        public void onConfigured(CameraCaptureSession session) {

        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            Image img = reader.acquireLatestImage();
            if (img != null) {
                if (System.currentTimeMillis() > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {
                    processImage(img);
                }
                img.close();
            }
        }
    };

    public void readyCamera() throws CameraAccessException, IOException {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "RoboFeel");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("App", "failed to create directory");
            }
        }


        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);

        String pickedCamera = getCamera(manager);
        Log.d("Manager",manager.toString());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs(0,0);

        Log.d("PickedCamera  ",pickedCamera);
        String CameraId = "1";
        manager.openCamera(CameraId, cameraStateCallback, null);

        startBackgroundThread();

        // imageReader = ImageReader.newInstance(1920, 1088, ImageFormat.JPEG, 2 /* images buffered */);

        mListener = new finalOnGetImageListener();
        Log.d("RoboService","Initialized from RoboService");
        mListener.initialize(getApplicationContext(), getAssets(), null, inferenceHandler);
        imageReader = ImageReader.newInstance( 1, 1, ImageFormat.YUV_420_888, 2);
        //imageReader.setOnImageAvailableListener(onImageAvailableListener,backgroundHandler);
        imageReader.setOnImageAvailableListener(mListener,backgroundHandler);
        Log.d(TAG, "imageReader created");

    }



    @SuppressLint("LongLogTag")
    @DebugLog
    private static Size chooseOptimalSize(
            final Size[] choices, final int width, final int height, final Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        final List<Size> bigEnough = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
                Timber.tag(TAG).i("Adding size: " + option.getWidth() + "x" + option.getHeight());
                bigEnough.add(option);
            } else {
                Timber.tag(TAG).i("Not adding size: " + option.getWidth() + "x" + option.getHeight());
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CameraConnectionFragment.CompareSizesByArea());
            Timber.tag(TAG).i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Timber.tag(TAG).e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    @DebugLog
    private void setUpCameraOutputs(final int width, final int height) {
        final CameraManager manager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            SparseArray<Integer> cameraFaceTypeMap = new SparseArray<>();
            // Check the facing types of camera devices
            for (final String cameraId : manager.getCameraIdList()) {
                Log.d(TAG,"CameraID : " + cameraId);
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT, cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT, 1);
                    }
                }

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK, cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_BACK) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK, 1);
                    }
                }
            }

            Integer num_facing_back_camera = cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_BACK);
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // If facing back camera or facing external camera exist, we won't use facing front camera
                if (num_facing_back_camera != null && num_facing_back_camera > 0) {
                    // We don't use a front facing camera in this sample if there are other camera device facing types
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                final Size largest =
                        Collections.max(
                                Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                                new CameraConnectionFragment.CompareSizesByArea());

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
                // We fit the aspect ratio of TextureView to the size of preview we picked.
                final int orientation = getResources().getConfiguration().orientation;

                return;
            }
        } catch (final CameraAccessException e) {
            Timber.tag(TAG).e("Exception!", e);
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.

        }
    }


    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        Log.d(TAG,"inferenceThread started");
        inferenceHandler = new Handler(inferenceThread.getLooper());

    }
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceThread = null;
        } catch (final InterruptedException e) {
            Timber.tag(TAG).e("error", e);
        }
    }
    public boolean startRecording(){
        try {
            Toast.makeText(getBaseContext(), "Recording Started", Toast.LENGTH_SHORT).show();
            //mSurfaceTexture = mSurfaceView.getTexture();
            //mServiceCamera = Camera.open();
            //  CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Camera.Parameters params = mServiceCamera.getParameters();
            mServiceCamera.setParameters(params);
            Camera.Parameters p = mServiceCamera.getParameters();

            final List<Camera.Size> listSize = p.getSupportedPreviewSizes();
            Camera.Size mPreviewSize = listSize.get(2);
            Log.v(TAG, "use: width = " + mPreviewSize.width
                    + " height = " + mPreviewSize.height);
            p.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            p.setPreviewFormat(PixelFormat.YCbCr_420_SP);
            mServiceCamera.setParameters(p);
            try {
                mServiceCamera.setPreviewDisplay(mSurfaceHolder);
                mServiceCamera.startPreview();
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }

            File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "RoboFeel");

            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d("App", "failed to create directory");
                }
            }

            mServiceCamera.unlock();
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mServiceCamera);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            Date currentTime = Calendar.getInstance().getTime();
            mMediaRecorder.setOutputFile(mediaStorageDir+"/video"+ currentTime +".mp4");
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoSize(mPreviewSize.width, mPreviewSize.height);
            // mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

            mMediaRecorder.prepare();
            mMediaRecorder.start();
            Log.d(TAG,"Recording Started");

            mRecordingStatus = true;

        } catch (IllegalStateException e) {
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;

    }

    public void stopRecording() {
        Toast.makeText(getBaseContext(), "Recording Stopped", Toast.LENGTH_SHORT).show();
        try {
            mServiceCamera.reconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        mServiceCamera.stopPreview();
        mMediaRecorder.release();

        mServiceCamera.release();
        mServiceCamera = null;
    }

    public String getCamera(CameraManager manager) {
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Log.e("getCameraFor", manager.getCameraCharacteristics(cameraId).toString());
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CAMERACHOICE) {
                    Log.e("getCameraReturn", cameraId);
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flags " + flags + " startId " + startId);
        Log.d(TAG,"intent.getFAction - " + intent.getAction());
        if(intent.getAction()!=null) {
            if (intent.getAction().equals("STOP"))
                Log.d(TAG,"Exiting");
                stopSelf();
        }


        try{
            Bundle bundle = intent.getExtras();
            sessionVariables.setUniqueUserID(bundle.getString("studentId"));
            sessionVariables.setNewSessId(bundle.getString("sessionId"));
        } catch (Exception e) {
            e.printStackTrace();
        }



        return super.onStartCommand(intent, flags, startId);
    }

    private Notification getMyActivityNotification(String text){
        // The PendingIntent to launch our activity if the user selects this notification
        CharSequence title = "RoboFeel";
        Intent mIntent = new Intent(this, RoboService.class);
        mIntent.setAction("STOP");
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, new Intent(this, CameraRecorderServiceActivity.class),0);
        PendingIntent stopIntent = PendingIntent.getService(this,0,mIntent,0);

        return new Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.sample_android)
                .addAction(R.drawable.ic_stop,"Stop Service", stopIntent)
                .setContentIntent(contentIntent).getNotification();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "mayank.example.rtcamera";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);
        CharSequence title = "RoboFeel";
        Intent mIntent = new Intent(this, RoboService.class);
        mIntent.setAction("STOP");
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, new Intent(this, CameraRecorderServiceActivity.class),0);
        PendingIntent stopIntent = PendingIntent.getService(this,0,mIntent,0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);


        Notification notification =  notificationBuilder.setOngoing(true)
                .setContentTitle(title)
                .setContentText("Service is running.")
                .setSmallIcon(R.drawable.sample_android)
                .addAction(R.drawable.ic_stop,"Stop Service", stopIntent)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(contentIntent).build();

        startForeground(2, notification);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate service");
        mRecordingStatus = false;
        //mServiceCamera = CameraRecorder.mCamera;
        // mServiceCamera = Camera.open(1);
        mSurfaceView = CameraRecorderServiceActivity.mSurfaceView;
        mImageView = CameraRecorderServiceActivity.mImageView;
        mSurfaceHolder = CameraRecorderServiceActivity.mSurfaceHolder;

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY :
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER | Gravity.END;
        windowManager = (WindowManager) this.getSystemService(WINDOW_SERVICE);
        mDisplay = windowManager.getDefaultDisplay();
        inflater = LayoutInflater.from(this);
        //layoutView = inflater.inflate(R.layout.overlay_layout, null);
        //windowManager.addView(layoutView, params);
        try {
            readyCamera();
            // start the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG,"SDK - " + String.valueOf(Build.VERSION.SDK_INT));
                startMyOwnForeground();
            }
            else
                startForeground(1, getMyActivityNotification("Service is running."));

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

        super.onCreate();
    }

    public void actOnReadyCameraDevice() {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy()");
        try {
            //  stopForeground(1,notification);
            if (mListener != null) {
                try {
                    mListener.deInitialize();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            windowManager.removeView(layoutView);
            //stopRecording();
            mRecordingStatus = false;

            if(session!=null){
                session.abortCaptures();
                session.close();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG,"onTaskRemoved()");
        try {
            //  stopForeground(1,notification);
            if (mListener != null) {
                try {
                    mListener.deInitialize();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            windowManager.removeView(layoutView);
            //stopRecording();
            mRecordingStatus = false;

            session.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
        }
        session.close();
        super.onTaskRemoved(rootIntent);
    }


    private void processImage(Image image) {
        //Process image data
        ByteBuffer buffer;
        byte[] bytes;
        boolean success = false;
        File file = new File(Environment.getExternalStorageDirectory() + "/Pictures/image.jpg");
        FileOutputStream output = null;

        if (image.getFormat() == ImageFormat.JPEG) {
            buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.remaining()]; // makes byte array large enough to hold image
            buffer.get(bytes); // copies image from buffer to byte array
            try {
                output = new FileOutputStream(file);
                output.write(bytes);    // write the byte array to file
                success = true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                image.close(); // close this to free up buffer for other images
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }


    }

    protected CaptureRequest createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(imageReader.getSurface());
            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}