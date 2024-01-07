/*
 * Copyright (C) 2020 The Android Open Source Project
 * Copyright (C) 2023 The risingOS Android Project
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

package com.android.systemui.qs;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.hardware.camera2.CameraManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.jank.InteractionJankMonitor;

import com.android.settingslib.Utils;

import com.android.systemui.R;
import com.android.systemui.animation.ActivityLaunchAnimator;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.retail.domain.interactor.RetailModeInteractor;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.ViewController;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Controller for {@link QSFooterView}.
 */
@QSScope
public class QSFooterViewController extends ViewController<QSFooterView> implements QSFooter {

    private final static String PERSONALIZATIONS_ACTIVITY = "com.android.settings.Settings$personalizationSettingsLayoutActivity";

    private final UserTracker mUserTracker;
    private final QSPanelController mQsPanelController;

    private final PageIndicator mPageIndicator;
    private View mSettingsButton, mEditButton, mRunningServiceButton, mInterfaceButton, mUserAvatar, mUserButton, mUserName;
    private View mDataUsageText, mDataUsageTitle, mDataUsageButton, mClockTimer, mCalculator, mCamera;
    private ImageView mTorch;
    private final FalsingManager mFalsingManager;
    private final ActivityStarter mActivityStarter;
    private final RetailModeInteractor mRetailModeInteractor;

    private ViewPager mViewPager;
    private List<View> mWidgetViews;

    private PagerAdapter pagerAdapter;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;

    @Inject
    QSFooterViewController(QSFooterView view,
            UserTracker userTracker,
            FalsingManager falsingManager,
            ActivityStarter activityStarter,
            QSPanelController qsPanelController,
            RetailModeInteractor retailModeInteractor
    ) {
        super(view);
        mUserTracker = userTracker;
        mQsPanelController = qsPanelController;
        mFalsingManager = falsingManager;
        mActivityStarter = activityStarter;
        mRetailModeInteractor = retailModeInteractor;

        mPageIndicator = mView.findViewById(R.id.footer_page_indicator);
		mUserAvatar = mView.findViewById(R.id.user_picture);
		mUserName = mView.findViewById(R.id.username);
		mUserButton = mView.findViewById(R.id.user_button);
		mDataUsageButton = mView.findViewById(R.id.data_usage_button);
		mDataUsageText = mView.findViewById(R.id.data_usage_text);
		mDataUsageTitle = mView.findViewById(R.id.data_usage_title);
		mViewPager = mView.findViewById(R.id.qs_footer_pager);
		initViewPager();
        cameraManager = (CameraManager) mView.getContext().getSystemService(Context.CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
    }

    private void initViewPager() {
        mWidgetViews = new ArrayList<>();
        View accessLayout = LayoutInflater.from(mView.getContext()).inflate(R.layout.qs_footer_shortcut_access, null);
        View widgetsLayout = LayoutInflater.from(mView.getContext()).inflate(R.layout.qs_footer_shortcut_widgets, null);
        mEditButton = accessLayout.findViewById(android.R.id.edit);
        mSettingsButton = accessLayout.findViewById(R.id.settings_button);
		mRunningServiceButton = accessLayout.findViewById(R.id.running_services_button);
		mInterfaceButton = accessLayout.findViewById(R.id.interface_button);
        mTorch = widgetsLayout.findViewById(R.id.qs_flashlight);
        mClockTimer = widgetsLayout.findViewById(R.id.qs_clock_timer);
		mCalculator = widgetsLayout.findViewById(R.id.qs_calculator);
		mCamera = widgetsLayout.findViewById(R.id.qs_camera);
        if (accessLayout != null && widgetsLayout != null) {
            mWidgetViews.add(accessLayout);
            mWidgetViews.add(widgetsLayout);
        }
        pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return mWidgetViews.size();
            }
            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                View view = mWidgetViews.get(position);
                container.addView(view);
                return view;
            }
            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        };

        mViewPager.setAdapter(pagerAdapter);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    accessLayout.setVisibility(View.VISIBLE);
                    widgetsLayout.setVisibility(View.GONE);
                } else {
                    accessLayout.setVisibility(View.GONE);
                    widgetsLayout.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }


    public void setWidgetLayouts(List<View> newLayouts) {
        mWidgetViews.clear();
        mWidgetViews.addAll(newLayouts);
        pagerAdapter.notifyDataSetChanged();
    }
		
    @Override
    protected void onViewAttached() {
        mEditButton.setOnClickListener(view -> {
            if (mFalsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                return;
            }
            mActivityStarter
                    .postQSRunnableDismissingKeyguard(() -> mQsPanelController.showEdit(view));
        });
        mTorch.setOnClickListener(view -> toggleFlashlight());
        mClockTimer.setOnClickListener(view -> launchTimer());
        mCalculator.setOnClickListener(view -> launchCalculator());
        mCamera.setOnClickListener(view -> launchCamera());
        mSettingsButton.setOnClickListener(mSettingsOnClickListener);
        mRunningServiceButton.setOnClickListener(mSettingsOnClickListener);
        mInterfaceButton.setOnClickListener(mSettingsOnClickListener);
        mUserAvatar.setOnClickListener(mSettingsOnClickListener);
        mUserButton.setOnClickListener(mSettingsOnClickListener);
        mUserName.setOnClickListener(mSettingsOnClickListener);
        mDataUsageText.setOnClickListener(mSettingsOnClickListener);
        mDataUsageTitle.setOnClickListener(mSettingsOnClickListener);
        mDataUsageButton.setOnClickListener(mSettingsOnClickListener);
        mQsPanelController.setFooterPageIndicator(mPageIndicator);
        mView.updateEverything();
    }
    
    private final View.OnClickListener mSettingsOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
	        if (mFalsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
		        return;
	        }
	        if (v == mSettingsButton) {
		        startSettingsActivity();
	        } else if (v == mRunningServiceButton) {
		        launchSettingsComponent("com.android.settings.Settings$DevRunningServicesActivity");
	        } else if (v == mInterfaceButton) {
		        launchSettingsComponent(PERSONALIZATIONS_ACTIVITY);
	        } else if (v == mDataUsageText || v == mDataUsageTitle || v == mDataUsageButton) {
	            launchSettingsComponent("com.android.settings.Settings$DataUsageSummaryActivity");
	        } else if (v == mUserName || v == mUserButton || v == mUserAvatar) {
	            launchSettingsComponent("com.android.settings.Settings$UserSettingsActivity");
	        }
        }
    };

    @Override
    protected void onViewDetached() {}

    @Override
    public void setVisibility(int visibility) {
        mView.setVisibility(visibility);
        mEditButton
                .setVisibility(mRetailModeInteractor.isInRetailMode() ? View.GONE : View.VISIBLE);
        mEditButton.setClickable(visibility == View.VISIBLE);
    }

    @Override
    public void setExpanded(boolean expanded) {
        mView.setExpanded(expanded);
    }

    @Override
    public void setExpansion(float expansion) {
        mView.setExpansion(expansion);
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        mView.setKeyguardShowing();
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        mView.disable(state2);
    }
    
    private void startSettingsActivity() {
		mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS), true /* dismissShade */);
    }
    
    private void launchSettingsComponent(String className) {
        Intent intent = className.equals(PERSONALIZATIONS_ACTIVITY) ? new Intent(Intent.ACTION_MAIN) : new Intent();
        intent.setComponent(new ComponentName("com.android.settings", className));
        mActivityStarter.startActivity(intent, true);
    }
    
    private void launchCamera() {
        Intent launchIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        PackageManager packageManager = mView.getContext().getPackageManager();
        List<ResolveInfo> cameraApps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!cameraApps.isEmpty()) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(R.string.camera);
        }
    }
    
    private void launchTimer() {
        Intent launchIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
        PackageManager packageManager = mView.getContext().getPackageManager();
        List<ResolveInfo> timerApps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!timerApps.isEmpty()) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(R.string.clock_timer);
        }
    }

    private void launchCalculator() {
        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        PackageManager packageManager = mView.getContext().getPackageManager();
        List<ResolveInfo> calculatorApps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!calculatorApps.isEmpty()) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(R.string.calculator);
        }
    }

    private void toggleFlashlight() {
        try {
            cameraManager.setTorchMode(cameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            int textColorPrimary = Utils.getColorAttrDefaultColor(mView.getContext(), android.R.attr.textColorPrimary);
            int textColorPrimaryInverse = Utils.getColorAttrDefaultColor(mView.getContext(), android.R.attr.textColorPrimaryInverse);
            int tintColor = isFlashOn ? textColorPrimaryInverse : textColorPrimary;
            mTorch.setBackgroundResource(isFlashOn ?  R.drawable.qs_footer_action_circle_active :  R.drawable.qs_footer_action_circle);
            mTorch.setImageTintList(ColorStateList.valueOf(tintColor));
        } catch (Exception e) {}
    }
    
    private void showNoDefaultAppFoundToast(@StringRes int appTypeResId) {
        String appType = mView.getContext().getString(appTypeResId);
        String message = mView.getContext().getString(R.string.no_default_app_found, appType);
        Toast.makeText(mView.getContext(), message, Toast.LENGTH_SHORT).show();
    }
}
