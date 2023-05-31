/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.os.VibrationEffect.createPredefined;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;

import java.util.function.Consumer;

/**
 * Wrapper around {@link Vibrator} to easily perform haptic feedback where necessary.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class VibratorWrapper {

    public static final MainThreadInitializedObject<VibratorWrapper> INSTANCE =
            new MainThreadInitializedObject<>(VibratorWrapper::new);

    public static final AudioAttributes VIBRATION_ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    public static final VibrationEffect EFFECT_TEXTURE_TICK =
            createPredefined(VibrationEffect.EFFECT_TEXTURE_TICK);

    public static final VibrationEffect EFFECT_TICK =
            createPredefined(VibrationEffect.EFFECT_TICK);

    public static final VibrationEffect EFFECT_CLICK =
            createPredefined(VibrationEffect.EFFECT_CLICK);

    public static final VibrationEffect EFFECT_HEAVY_CLICK =
            createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);

    private static final float DRAG_TEXTURE_SCALE = 0.03f;
    private static final float DRAG_COMMIT_SCALE = 0.5f;
    private static final float DRAG_BUMP_SCALE = 0.4f;
    private static final int DRAG_TEXTURE_EFFECT_SIZE = 200;

    private long mLastDragTime;
    private final int mThresholdUntilNextDragCallMillis = 0;

    /**
     * Haptic when entering overview.
     */
    public static final VibrationEffect OVERVIEW_HAPTIC = EFFECT_CLICK;

    private final Vibrator mVibrator;
    private final boolean mHasVibrator;
    
    private Context mContext;

    private VibratorWrapper(Context context) {
        mContext = context;
        mVibrator = context.getSystemService(Vibrator.class);
        mHasVibrator = mVibrator.hasVibrator();
    }

    /**
     *  This is called when the user swipes to/from all apps. This is meant to be used in between
     *  long animation progresses so that it gives a dragging texture effect. For a better
     *  experience, this should be used in combination with vibrateForDragCommit().
     */
    public void vibrateForDragTexture() {
        if (isHapticFeedbackEnabled()) {
            return;
        }
        long currentTime = SystemClock.elapsedRealtime();
        long elapsedTimeSinceDrag = currentTime - mLastDragTime;
        if (elapsedTimeSinceDrag >= mThresholdUntilNextDragCallMillis) {
            vibrate(getVibrationIntensity(mContext));
            mLastDragTime = currentTime;
        }
    }

    /**
     *  This is used when user reaches the commit threshold when swiping to/from from all apps.
     */
    public void vibrateForDragCommit() {
        if (isHapticFeedbackEnabled()) {
            vibrate(getVibrationIntensity(mContext));
        }
        // resetting dragTexture timestamp to be able to play dragTexture again
        mLastDragTime = 0;
    }

    /**
     *  The bump haptic is used to be called at the end of a swipe and only if it the gesture is a
     *  FLING going to/from all apps. Client can just call this method elsewhere just for the
     *  effect.
     */
    public void vibrateForDragBump() {
        if (isHapticFeedbackEnabled()) {
            vibrate(getVibrationIntensity(mContext));
        }
    }

    public static VibrationEffect getVibrationIntensity(Context context) {
        VibrationEffect effect;
        int vibIntensity = Utilities.getVibrationIntensity(context);
        switch (vibIntensity) {
            case 1:
                effect = EFFECT_TEXTURE_TICK;
                break;
            case 2: 
                effect = EFFECT_TICK;
                break;
            case 3:
                effect = EFFECT_CLICK;
                break;
            case 4:
                effect = EFFECT_HEAVY_CLICK;
                break;
            default:
                effect = EFFECT_TICK;
                break;
        }
        
        return effect;
    }

    /**
     * This should be used to cancel a haptic in case where the haptic shouldn't be vibrating. For
     * example, when no animation is happening but a vibrator happens to be vibrating still. Need
     * boolean parameter for {@link PendingAnimation#addEndListener(Consumer)}.
     */
    public void cancelVibrate(boolean unused) {
        UI_HELPER_EXECUTOR.execute(mVibrator::cancel);
        // reset dragTexture timestamp to be able to play dragTexture again whenever cancelled
        mLastDragTime = 0;
    }

    private boolean isHapticFeedbackEnabled() {
        int vibIntensity = Utilities.getVibrationIntensity(mContext);
        return vibIntensity != 0;
    }

    /** Vibrates with the given effect if haptic feedback is available and enabled. */
    public void vibrate(VibrationEffect vibrationEffect) {
        if (mHasVibrator && isHapticFeedbackEnabled()) {
            UI_HELPER_EXECUTOR.execute(() -> mVibrator.vibrate(vibrationEffect, VIBRATION_ATTRS));
        }
    }

    /**
     * Vibrates with a single primitive, if supported, or use a fallback effect instead. This only
     * vibrates if haptic feedback is available and enabled.
     */
    @SuppressLint("NewApi")
    public void vibrate(int primitiveId, float primitiveScale, VibrationEffect fallbackEffect) {
        if (mHasVibrator && isHapticFeedbackEnabled()) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (Utilities.ATLEAST_R && primitiveId >= 0
                        && mVibrator.areAllPrimitivesSupported(primitiveId)) {
                    mVibrator.vibrate(VibrationEffect.startComposition()
                            .addPrimitive(primitiveId, primitiveScale)
                            .compose(), VIBRATION_ATTRS);
                } else {
                    mVibrator.vibrate(fallbackEffect, VIBRATION_ATTRS);
                }
            });
        }
    }
}
