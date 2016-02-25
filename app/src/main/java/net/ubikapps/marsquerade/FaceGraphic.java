/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ubikapps.marsquerade;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.google.android.gms.vision.face.Face;
import com.google.vrtoolkit.cardboard.Eye;
import net.ubikapps.marsquerade.camera.GraphicOverlay;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic extends GraphicOverlay.Graphic {

    private static final String TAG = "FaceGraphic";
    private Bitmap mSadBitmap;
    private Bitmap mHappyBitmap;
    private RectF mRectf;


    private volatile Face mFace;
    private int mFaceId;
    private Paint mAntiAliasPaint;
    private int mEyeType = 0;
    private int mCardBoardViewWidth;

    public FaceGraphic(Context context, GraphicOverlay overlay){
        super(overlay);
        setupBitmaps(context);
    }

    public FaceGraphic(Context context, GraphicOverlay overlay, int eyeType, int cardBoardViewWidth) {
        super(overlay);
        mEyeType = eyeType;
        mCardBoardViewWidth = cardBoardViewWidth;
        Resources resources =  context.getResources();

        setupBitmaps(context);
    }

    private void setupBitmaps(Context context){
        Resources resources =  context.getResources();

        mHappyBitmap = BitmapFactory.decodeResource(resources, R.drawable.happy);
        mSadBitmap = BitmapFactory.decodeResource(resources, R.drawable.sad);
        mAntiAliasPaint = new Paint();
        mAntiAliasPaint.setAntiAlias(true);
    }

    void setId(int id) {
        mFaceId = id;
    }


    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            return;
        }
        float smileProbability = face.getIsSmilingProbability();

        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        float xOffset = scaleX((face.getWidth() / 2.0f));
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset + getEyeXOffset();
        float top = y - yOffset + 50.0f;
        float right = x + xOffset + getEyeXOffset();
        float bottom = y + yOffset + 50.0f;
        if(mRectf == null) {
           mRectf = new RectF(left, top, right, bottom);
        } else {
            mRectf.top = top;
            mRectf.right = right;
            mRectf.bottom = bottom;
            mRectf.left = left;
        }

        if(smileProbability >= 0.5F){
            canvas.drawBitmap(mHappyBitmap, null, mRectf, mAntiAliasPaint);
        } else {
            canvas.drawBitmap(mSadBitmap, null, mRectf, mAntiAliasPaint);
        }
    }

    private float getEyeXOffset(){
        if(mEyeType == Eye.Type.RIGHT){
            return (mCardBoardViewWidth/2.0f) - 72.0f;
        } else {
            return 0.0f;
        }
    }
}
