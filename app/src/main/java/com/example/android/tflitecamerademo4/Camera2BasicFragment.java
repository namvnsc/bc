package com.example.android.tflitecamerademo4;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
//import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.support.v13.app.FragmentCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jp.co.cyberagent.android.gpuimage.GPUImageView;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter;


public class Camera2BasicFragment extends Fragment implements FragmentCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "TfLiteCameraDemo";
    private static final String HANDLE_THREAD_NAME = "CameraBackground";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private final Object lock = new Object();
    private boolean runsegmentor = false;
    private boolean checkedPermissions = false;
//    private TextView textView;
    public GPUImageView gpuImageView;
    public GPUImageToneCurveFilter curve_filter;
    InputStream is = null;
    public Bitmap bgd, bgd1;
    private String cameraId;
    public static AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Size previewSize;// = new Size(512, 512);

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

//    private ImageReader imageReader;
    private CaptureRequest.Builder previewRequestBuilder;

    private CaptureRequest previewRequest;

    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice currentCameraDevice) {
            cameraOpenCloseLock.release();
            cameraDevice = currentCameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
            cameraOpenCloseLock.release();
            currentCameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
            cameraOpenCloseLock.release();
            currentCameraDevice.close();
            cameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Failed to set up config to capture Camera", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                            showToast("Failed");
                        }
                    },
                    null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to preview Camera", e);
        }
    }

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
        }
    };

    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

//    private void showToast(String s) {
//        SpannableStringBuilder builder = new SpannableStringBuilder();
//        SpannableString str1 = new SpannableString(s);
//        builder.append(str1);
//        showToast(builder);
//    }
//
//    private void showToast(SpannableStringBuilder builder) {
//        final Activity activity = getActivity();
//        if (activity != null) {
//            activity.runOnUiThread(
//                    new Runnable() {
//                        @Override
//                        public void run() {
//                            textView.setText(builder, TextView.BufferType.SPANNABLE);
//                        }
//                    });
//        }
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

//    private void updateActiveModel() {
//        backgroundHandler.post(() -> {
//            if (segmentor != null) {
//                segmentor.close();
//                segmentor = null;
//            }
//            // Try to load model.
//            try {
//                segmentor = new ImageSegmentor(getActivity());
//                bgd = bgd1;
//            } catch (IOException e) {
//                Log.d(TAG, "Failed to load", e);
//                segmentor = null;
//            }
//            // Customize the interpreter to the type of device we want to use.
//            if (segmentor == null) {
//                return;
//            }
//        });
//    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
//        textView = (TextView) view.findViewById(R.id.text);

        //GPUImage
        gpuImageView = (GPUImageView) view.findViewById(R.id.gpuimageview);
        AssetManager as = this.getActivity().getAssets();
        curve_filter = new GPUImageToneCurveFilter();

        try {
            is = as.open("green.acv");
            curve_filter.setFromCurveFileInputStream(is);
            is.close();
            Log.e("MainActivity", "Success ACV Loaded");
        } catch (IOException e) {
            Log.e("MainActivity", "Error");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;

        // Load background images
//        bgd = bgd1 = BitmapFactory.decodeResource(getResources(), R.drawable.dock_vbig, options);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        startBackgroundThread();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        ImageSegmentor.close();
        closeCamera();
        stopBackgroundThread();
        super.onDestroy();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void openCamera() {
        if (!checkedPermissions && !allPermissionsGranted()) {
            FragmentCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
            return;
        } else {
            checkedPermissions = true;
        }
        setUpCameraOutputs();
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open Camera", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void setUpCameraOutputs() {
        try{
            this.cameraId = "1";
            Activity activity = getActivity();
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(this.cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        }catch(Exception e){
            Log.e("setUpCameraOutputs", "e");
        }
    }

    private String[] getRequiredPermissions() {
        Activity activity = getActivity();
        try {
          PackageInfo info = activity.getPackageManager()
                                  .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
          String[] ps = info.requestedPermissions;
          if (ps != null && ps.length > 0) {
            return ps;
          } else {
            return new String[0];
          }
        } catch (Exception e) {
          return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
          if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
            return false;
          }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
//            if (null != imageReader) {
//                imageReader.close();
//                imageReader = null;
//            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread(){
        backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        synchronized (lock) {
          runsegmentor = true;
        }
        backgroundHandler.post(periodicSegment);
    }

    private void stopBackgroundThread() {
        try {
            backgroundThread.quitSafely();
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
            synchronized (lock) {
                runsegmentor = false;
            }
        } catch (InterruptedException e) {
          Log.e(TAG, "Interrupted when stopping background thread", e);
        }catch (Exception e){

        }
    }

    private Runnable periodicSegment = new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (runsegmentor) {
              segmentFrame();
            }
          }
          try {
              backgroundHandler.post(periodicSegment);
          }catch (Exception e){

          }
        }
      };

    private void segmentFrame() {

        if (getActivity() == null || cameraDevice == null) {
          return;
        }
        long t1  = SystemClock.uptimeMillis();
        Bitmap fgd = textureView.getBitmap(ImageSegmentor.frame_width, ImageSegmentor.frame_height);
//        Bitmap bitmap = fgd.createScaledBitmap(fgd, ImageSegmentor.W, ImageSegmentor.H, true);
//        long t2 = SystemClock.uptimeMillis();
//        Log.e(TAG, "t get bit map: "+(t2-t1));
//        t1 = SystemClock.uptimeMillis();
        ImageSegmentor.segmentFrame(fgd.createScaledBitmap(fgd, ImageSegmentor.W, ImageSegmentor.H, true), fgd);
        long t2 = SystemClock.uptimeMillis();
        Log.e(TAG, "t total: "+(t2-t1) + " FPS: " + (1000.0/(t2-t1)));
//        bitmap.recycle();

        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            if(ImageSegmentor.result!=null) {
                gpuImageView.setImage(ImageSegmentor.result);
            }
          }
        });
    }

}
