package com.android.systemui.lockscreen;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.AttributeSet;
import android.os.UserHandle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View;
import androidx.annotation.StringRes;

import com.android.settingslib.Utils;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.tuner.TunerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LockScreenWidgets extends LinearLayout implements TunerService.Tunable {

    private static final String LOCKSCREEN_WIDGETS =
            "system:lockscreen_widgets";

    private static final String LOCKSCREEN_WIDGET_PICTURE_TILE =
            "system:lockscreen_widget_picture_tile";

    private static final int[] WIDGETS_VIEW_IDS = {
            R.id.kg_item_placeholder1,
            R.id.kg_item_placeholder2,
            R.id.kg_item_placeholder3,
            R.id.kg_item_placeholder4
    };

    private ActivityStarter mActivityStarter;

    private Context mContext;
    private ImageView mWidget1, mWidget2, mWidget3, mWidget4;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;
    
    private String lockscreenWidgetsList;
    private ImageView[] widgetViews;
    private List<String> widgetsList;
    private String widgetImagePath;

    public LockScreenWidgets(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
		Dependency.get(TunerService.class).addTunable(this, LOCKSCREEN_WIDGETS);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        widgetViews = new ImageView[WIDGETS_VIEW_IDS.length];
        for (int i = 0; i < widgetViews.length; i++) {
            widgetViews[i] = findViewById(WIDGETS_VIEW_IDS[i]);
        }
        updateWidgetViews();
    }

    private void updateContainerVisibility() {
        boolean isEmpty = TextUtils.isEmpty(lockscreenWidgetsList);
        setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateWidgetViews() {
        if (widgetViews != null) {
            for (int i = 0; i < widgetViews.length; i++) {
                if (widgetViews[i] != null) {
                    widgetViews[i].setVisibility(i < widgetsList.size() ? View.VISIBLE : View.GONE);
                }
            }
        }
        if (widgetViews != null && widgetsList != null) {
            for (int i = 0; i < Math.min(widgetsList.size(), widgetViews.length); i++) {
                String widgetType = widgetsList.get(i);
                if (widgetType != null && i < widgetViews.length && widgetViews[i] != null) {
                    setViewOnClickListener(widgetViews[i], widgetType);
                }
            }
        }
        updateContainerVisibility();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWidgetViews();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_WIDGETS:
                lockscreenWidgetsList = (String) newValue;
                if (lockscreenWidgetsList != null) {
                    widgetsList = Arrays.asList(lockscreenWidgetsList.split(","));
                }
                updateWidgetViews();
                break;
            case LOCKSCREEN_WIDGET_PICTURE_TILE:
                widgetImagePath = (String) newValue;
                updateWidgetViews();
                break;
            default:
                break;
        }
    }

    private void setViewOnClickListener(ImageView v, String type) {
        switch (type) {
            case "torch":
                v.setOnClickListener(view -> toggleFlashlight(v));
                v.setImageResource(R.drawable.ic_camera);
                break;
            case "timer":
                v.setOnClickListener(view -> launchTimer());
                v.setImageResource(R.drawable.ic_alarm);
                break;
            case "calculator":
                v.setOnClickListener(view -> launchCalculator());
                v.setImageResource(R.drawable.ic_calculator);
                break;
            case "media":
                v.setOnClickListener(view -> toggleMediaPlayback(v));
                v.setImageResource(R.drawable.ic_media_play);
                break;
            case "gallery":
                v.setOnClickListener(view -> launchGallery());
                v.setImageResource(R.drawable.ic_pages);
                setImageTile(v);
                break;
            default:
                break;
        }
    }

    private void toggleMediaPlayback(ImageView v) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.isMusicActive()) {
            KeyEvent pauseDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
            KeyEvent pauseUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE);
            audioManager.dispatchMediaKeyEvent(pauseDownEvent);
            audioManager.dispatchMediaKeyEvent(pauseUpEvent);
            v.setImageResource(R.drawable.ic_media_play);
            v.setBackgroundResource(R.drawable.qs_footer_action_circle);
        } else {
            KeyEvent playDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
            KeyEvent playUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY);
            audioManager.dispatchMediaKeyEvent(playDownEvent);
            audioManager.dispatchMediaKeyEvent(playUpEvent);
            v.setImageResource(R.drawable.ic_media_pause);
            v.setBackgroundResource(R.drawable.qs_footer_action_circle_active);
        }
    }

    public void setActivityStarter(ActivityStarter as) {
        mActivityStarter = as;
    }

    private void setImageTile(ImageView iv) {
        Bitmap bitmap = BitmapFactory.decodeFile(widgetImagePath);
        if (bitmap != null) {
            iv.setImageBitmap(bitmap);
        }
    }

    private void launchAppIfAvailable(Intent launchIntent, @StringRes int appTypeResId) {
        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> apps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!apps.isEmpty() && mActivityStarter != null) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(appTypeResId);
        }
    }

    private void launchGallery() {
        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
        launchIntent.setType("image/*");
        launchAppIfAvailable(launchIntent, R.string.gallery);
    }

    private void launchTimer() {
        Intent launchIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
        launchAppIfAvailable(launchIntent, R.string.clock_timer);
    }

    private void launchCalculator() {
        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        launchAppIfAvailable(launchIntent, R.string.calculator);
    }

    private void toggleFlashlight(ImageView iv) {
        try {
            cameraManager.setTorchMode(cameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            int textColorPrimary = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
            int textColorPrimaryInverse = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimaryInverse);
            int tintColor = isFlashOn ? textColorPrimaryInverse : textColorPrimary;
            iv.setBackgroundResource(isFlashOn ?  R.drawable.qs_footer_action_circle_active :  R.drawable.qs_footer_action_circle);
            iv.setImageTintList(ColorStateList.valueOf(tintColor));
        } catch (Exception e) {}
    }
    
    private void showNoDefaultAppFoundToast(@StringRes int appTypeResId) {
        String appType = mContext.getString(appTypeResId);
        String message = mContext.getString(R.string.no_default_app_found, appType);
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }
}
