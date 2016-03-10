package net.ubikapps.marsquerade;

import android.view.View;

/**
 * Created by msteven on 09/03/2016.
 */
public interface CameraActivity {

    int RC_HANDLE_GMS = 9001;

    void restartCameraSource();

    void createCameraSource();
    void startCameraSource();

    void takePic(View view);
}
