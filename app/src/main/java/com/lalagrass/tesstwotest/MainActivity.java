package com.lalagrass.tesstwotest;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends Activity {
    private boolean _createtessdata = false;

    private TextView tResult;
    private Camera mCamera0;
    private CameraPreview mPreview;
    private TessBaseAPI tessBaseAPI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tResult = (TextView) findViewById(R.id.tResult);
        if (null == savedInstanceState) {
            mCamera0 = getCameraInstance();
            ((FrameLayout) findViewById(R.id.camera_preview)).addView(new CameraPreview(this, mCamera0));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();
        if (tessBaseAPI != null) {
            tessBaseAPI.end();
            tessBaseAPI = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mCamera0 = getCameraInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera() {
        if (mCamera0 != null) {
            mCamera0.release();        // release the camera for other applications
            mCamera0 = null;
        }
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static Camera getCameraInstance() {
        Camera c = null;
        c = Camera.open();
        return c; // returns null if camera is unavailable
    }

    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private SurfaceHolder mHolder;
        private Camera mCamera;
        private int imageFormat;
        private boolean bProcessing = false;
        private int PreviewSizeHeight;
        private int PreviewSizeWidth;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            if (mCamera == null)
                return;
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                if (mCamera != null) {
                    mCamera.stopPreview();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (mHolder.getSurface() == null) {
                return;
            }
            try {
                mCamera.stopPreview();
                Camera.Parameters parameters;
                parameters = mCamera.getParameters();
                PreviewSizeWidth = parameters.getPreviewSize().width;
                PreviewSizeHeight = parameters.getPreviewSize().height;
                imageFormat = parameters.getPreviewFormat();
                mCamera.setPreviewDisplay(mHolder);
                mCamera.setPreviewCallback(this);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (imageFormat == ImageFormat.NV21) {
                if (!bProcessing) {
                    bProcessing = true;
                    new ImageProcessing().execute(data);
                }
            }
        }

        private class ImageProcessing extends AsyncTask<byte[], Void, String> {

            @Override
            protected String doInBackground(byte[]... params) {
                String ret = null;
                CheckTessdata();
                if (_createtessdata) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    YuvImage yuvImage = new YuvImage(params[0], ImageFormat.NV21, PreviewSizeWidth, PreviewSizeHeight, null);
                    yuvImage.compressToJpeg(new Rect(0, 0, PreviewSizeWidth, PreviewSizeHeight), 100, out);
                    byte[] imageBytes = out.toByteArray();
                    Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    ret = detectText(image);
                }
                return ret;
            }

            @Override
            protected void onPostExecute(String aVoid) {
                bProcessing = false;
                tResult.setText(aVoid);
            }
        }
    }

    public String detectText(Bitmap bitmap) {
        if (tessBaseAPI == null) {
            File d = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "TessTwoTest" + File.separator + "tessdata" + File.separator + "eng.traineddata");
            tessBaseAPI = new TessBaseAPI();
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "TessTwoTest";
            tessBaseAPI.setDebug(true);
            tessBaseAPI.init(path, "eng");
            //tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        }
        tessBaseAPI.setImage(bitmap);
        String text = tessBaseAPI.getUTF8Text();
        return text;
    }

    private void CheckTessdata() {
        if (!_createtessdata) {
            _createtessdata = true;
            File d = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "TessTwoTest" + File.separator + "tessdata" + File.separator);
            boolean r = d.mkdirs();
            AssetManager assetManager = getAssets();
            try {
                for (String assets : assetManager.list("tessdata")) {
                    File f = new File(d, assets);
                    if (!f.exists()) {
                        InputStream in = null;
                        OutputStream out = null;
                        in = assetManager.open("tessdata" + File.separator + assets);
                        out = new FileOutputStream(f);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        in.close();
                        out.flush();
                        out.close();
                    }
                }
            } catch (IOException e) {
                _createtessdata = false;
            }
        }
    }
}
