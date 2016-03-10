package net.ubikapps.marsquerade.camera;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import net.ubikapps.marsquerade.CameraActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by user1 on 10/03/2016.
 */
public class PictureCallbackImpl implements CameraSource.PictureCallback {

    private static final String TAG = "PictureCallbackImpl";
    private CameraActivity mCameraActivity;
    private Context mContext;

    public PictureCallbackImpl(Context context, CameraActivity cameraActivity){
        mContext = context;
        mCameraActivity = cameraActivity;
    }

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
        if(mCameraActivity != null && mContext != null) {
            try {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();
                Toast.makeText(mContext, "New Image saved:" + file,
                        Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
            mCameraActivity.restartCameraSource();
        }
    }
}
