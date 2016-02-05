package net.ubikapps.marsquerade;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardDeviceParams;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import net.ubikapps.marsquerade.camera.CameraSource;
import net.ubikapps.marsquerade.camera.GraphicOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, OnFrameAvailableListener {

    private static final String TAG = "MainActivity";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private CameraSource mCameraSource;
    private GraphicOverlay mGraphicOverlay;
    private int mEyeType = Eye.Type.LEFT;
    private static final int RC_HANDLE_GMS = 9001;
    private boolean mFlashEnabled = false;
    private boolean mSurfaceCreated = false;
    private CameraSource.PictureCallback mPictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new Date());
            File mediaStorageDir = new File(
                    Environment
                            .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "mArsquerade");
            mediaStorageDir.mkdirs();

            String fname = "IMG_" + timeStamp + ".jpg";
            File file = new File(mediaStorageDir.getPath(), fname);
            Log.d(TAG, "Saving pic to: " + file.getAbsolutePath());
            if (file.exists()) file.delete();
            try {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();
                Toast.makeText(getApplicationContext(), "New Image saved:" + file,
                        Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
            restartCameraSource();
        }
    };

    private final String vertexShaderCode =
            "attribute vec4 position;" +
                    "attribute vec2 inputTextureCoordinate;" +
                    "varying vec2 textureCoordinate;" +
                    "void main()" +
                    "{" +
                    "gl_Position = position;" +
                    "textureCoordinate = inputTextureCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;                            \n" +
                    "uniform samplerExternalOES s_texture;               \n" +
                    "void main(void) {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    //"  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                    "}";

    private FloatBuffer vertexBuffer, textureVerticesBuffer;
    private ShortBuffer drawListBuffer;
    private int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mTextureCoordHandle;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in counterclockwise order:
            -1.0f, -1.0f,   // 0.left - mid
            1.0f, -1.0f,   // 1. right - mid
            -1.0f, 1.0f,   // 2. left - top
            1.0f, 1.0f,   // 3. right - top
    };

    private short drawOrder[] = {0, 2, 1, 1, 2, 3}; // order to draw vertices

    static float textureVertices90[] = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f
    };
    static float textureVerticesLandscape[] = {
            0.0f, 1.0f, // A. left-bottom
            1.0f, 1.0f, // B. right-bottom
            0.0f, 0.0f, // C. left-top
            1.0f, 0.0f  // D. right-top
    };
    static float textureVertices270[] = {
            //For e.g. Nexus 5X
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f

    };
    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex


    private int mTexture;

    private CardboardView mCardboardView;
    private SurfaceTexture mSurfaceTexture;
/* Old Camera API before face tracking
	public void startCamera(int mTexture)
    {
        Log.d(TAG, "startCamera");
        mSurfaceTexture = new SurfaceTexture(mTexture);

        mSurfaceTexture.setOnFrameAvailableListener(this);

        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        Log.d(TAG, "Camera orientation: " + cameraInfo.orientation);

        Camera.Parameters parameters = camera.getParameters();

        Log.d(TAG, "Orig prev size: " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);

        Log.d(TAG, "Size of cardboard view: " + mCardboardView.getWidth() + ", " + mCardboardView.getHeight());
        //Camera.Size optPreviewSize = getOptimalPreviewSize(camera.getParameters().getSupportedPreviewSizes(), mCardboardView.getWidth() / 2, mCardboardView.getHeight());
        //Log.d(TAG, "New prev size: " + optPreviewSize.width + ", " + optPreviewSize.height);

        /* Log all preview sizes
        for (Camera.Size previewSize: camera.getParameters().getSupportedPreviewSizes())
        {
            Log.d(TAG, "Preview size: " + previewSize.width + "," +  previewSize.height);
        }
        try
        {
            //Preview size for Note 4
            parameters.setPreviewSize(1088, 1088);
            //parameters.setPreviewSize(optPreviewSize.width, optPreviewSize.height);
            //parameters.set("orientation", "portrait");
            //Correct for Note 4
            parameters.setRotation(90);
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);

            camera.setPreviewTexture(mSurfaceTexture);
            camera.startPreview();
        }
        catch (IOException ioe)
        {
            Log.w("MainActivity","CAM LAUNCH FAILED");
        }
    }*/

    static private int createTexture() {
        int[] textureArr = new int[1];

        GLES20.glGenTextures(1, textureArr, 0);
        if ("Nexus 5".equals(Build.MODEL)) {
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureArr[0]);
        } else {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureArr[0]);
        }
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return textureArr[0];
    }

    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }
        mCameraSource = new CameraSource.Builder(context, detector)
                //Hard coded for Note 4
                .setRequestedPreviewSize(1088, 1088)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     * @param type The type of shader we will be creating.
     * @param code The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    private int loadGLShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mCardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        //createEyeDialog();
        mCardboardView.setRestoreGLStateEnabled(false);
        mCardboardView.setSettingsButtonEnabled(false);
        mCardboardView.setAlignmentMarkerEnabled(false);
        CardboardDeviceParams deviceParams = mCardboardView.getCardboardDeviceParams();
        deviceParams.setModel("Cardboard v2");
        updateCardboardDeviceParams(deviceParams);
        mCardboardView.setRenderer(this);
        setCardboardView(mCardboardView);
        //Log.d(TAG, "Model: " + mCardboardView.getCardboardDeviceParams().getModel());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSurfaceCreated) {
            restartCameraSource();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    public void toggleFlash(View view) {
        ImageButton flashButton = (ImageButton) view;
        if (mCameraSource != null) {
            mFlashEnabled = !mFlashEnabled;
            if (mFlashEnabled) {
                mCameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_on_white_36dp));
            } else {
                mCameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                flashButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_flash_off_white_36dp));
            }
        }
    }

    public void takePic(View view) {
        if (mCameraSource != null) {
            mCameraSource.takePicture(null, mPictureCallback);
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        mSurfaceTexture = new SurfaceTexture(mTexture);

        mSurfaceTexture.setOnFrameAvailableListener(this);
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                mCameraSource.start(mSurfaceTexture);

                Size size = mCameraSource.getPreviewSize();
                //Log.d(TAG, "Preview size: " + size.getWidth() + ", " + size.getHeight());
                int min = Math.min(size.getWidth(), size.getHeight());
                int max = Math.max(size.getWidth(), size.getHeight());
                Log.d(TAG, "Overlay size: " + min + ", " + max);
                mGraphicOverlay.setCameraInfo(min, max, mCameraSource.getCameraFacing());
                mGraphicOverlay.clear();
                if(mFlashEnabled) {
                    mCameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void restartCameraSource(){
        if(mCameraSource != null){
            mCameraSource.stop();
            mCameraSource.release();
        }
        createCameraSource();
        startCameraSource();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     * @param config The EGL configuration used when creating the mSurfaceTexture.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        mSurfaceCreated = true;
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);


        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);
        ByteBuffer bb2 = ByteBuffer.allocateDirect(getTextureVertices().length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(getTextureVertices());
        textureVerticesBuffer.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);

        mTexture = createTexture();
        Log.d(TAG, "Texture created: " + mTexture);
        createCameraSource();
        startCameraSource();

    }

    private float[] getTextureVertices() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        Log.d(TAG, "Camera orientation: " + cameraInfo.orientation);
        switch(cameraInfo.orientation){
            case 90:
                return textureVerticesLandscape;
            case 270:
                return textureVertices270;
            default:
                return textureVertices90;
        }
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
    	float[] mtx = new float[16];
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mtx);
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GL_TEXTURE_EXTERNAL_OES);
        if("Nexus 5".equals(Build.MODEL)) {
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTexture);
        } else {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture);
        }

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, vertexBuffer);

        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, textureVerticesBuffer);

        mColorHandle = GLES20.glGetAttribLocation(mProgram, "s_texture");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, drawListBuffer);


        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
    }

    @Override
	public void onFrameAvailable(SurfaceTexture arg0) {
		this.mCardboardView.requestRender();
	}

    @Override
    public void onFinishFrame(Viewport viewport) {
        // Do nowt
    }

    @Override
    public void onCardboardTrigger() {
        ImageButton flashButton = (ImageButton)findViewById(R.id.flashButton);
        toggleFlash(flashButton);
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent){
        Log.d(TAG, "Keydown: " + keyCode);
        if(keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            Log.d(TAG, "Vol up pressed");
            if(mCameraSource != null){
                mCameraSource.takePicture(null,mPictureCallback);
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, keyEvent);
        }
    }

    public int getEyeType(){
        return mEyeType;
    }

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mLeftFaceGraphic;
        private FaceGraphic mRightFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mLeftFaceGraphic = new FaceGraphic(getApplicationContext(), overlay, Eye.Type.LEFT, mCardboardView.getWidth());
            mRightFaceGraphic = new FaceGraphic(getApplicationContext(), overlay, Eye.Type.RIGHT, mCardboardView.getWidth());
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mLeftFaceGraphic.setId(faceId);
            mRightFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mLeftFaceGraphic);
            mOverlay.add(mRightFaceGraphic);
            mLeftFaceGraphic.updateFace(face);
            mRightFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mLeftFaceGraphic);
            mOverlay.remove(mRightFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mLeftFaceGraphic);
            mOverlay.remove(mRightFaceGraphic);
        }
    }
}
