/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.*;
import android.net.wifi.*;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.*;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.android.settingslib.net.DataUsageController;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.UserIcons;

import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.drawable.CircleFramedDrawable;
import com.android.systemui.R;

import java.lang.reflect.Method;

import java.util.List;

/**
 * Footer of expanded Quick Settings, tiles page indicator, (optionally) build number and
 * {@link FooterActionsView}
 */
public class QSFooterView extends FrameLayout {
	
    private PageIndicator mPageIndicator;
    private TextView mUserName, mUsageText, mUsageHeader;
    private View mShorcutsButton, mSettingButton, mEditButton, mRunningServiceButton, mInterfaceButton, mUserButton, mDataUsageButton;

    private DataUsageController mDataController;
    private ConnectivityManager mConnectivityManager;
    private WifiManager mWifiManager;
    private SubscriptionManager mSubManager;
    private UserManager mUserManager;

    private ImageView mUserAvatar;

    private boolean mQsDisabled, mExpanded, mCustomAvatarEnabled, mUserEnabled;
    private float mExpansionAmount;

    @Nullable
    private OnClickListener mExpandClickListener;

    public QSFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDataController = new DataUsageController(context);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mSubManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPageIndicator = findViewById(R.id.footer_page_indicator);
		mEditButton = findViewById(android.R.id.edit);
		mSettingButton = findViewById(R.id.settings_button);
		mRunningServiceButton = findViewById(R.id.running_services_button);
		mInterfaceButton = findViewById(R.id.interface_button);
		mShorcutsButton = findViewById(R.id.qs_footer_pager);
		mUserButton = findViewById(R.id.user_button);
		mUserAvatar = findViewById(R.id.user_picture);
		mUserAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
		mUserAvatar.setClipToOutline(true);
		mUserAvatar.setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(100));
			}
		});
		mUserName = findViewById(R.id.username);
		mUserName.setSelected(true);
		mDataUsageButton = findViewById(R.id.data_usage_button);
		mUsageHeader = findViewById(R.id.data_usage_title);
		mUsageText = findViewById(R.id.data_usage_text);
		mUsageText.setSelected(true);
		mUsageHeader.setSelected(true);
		
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
		setUsageText();
		updateProfileView();
	}
	
	private void updateProfileView() {
		final Drawable avatarDrawable = getCircularUserIcon(mContext);
		mUserAvatar.setImageDrawable(avatarDrawable);
		mUserName.setText(getUserName());
	}
	
	public void setUsageText() {
		if (mUsageText == null || mUsageHeader == null) return;
		DataUsageController.DataUsageInfo info;
		String suffix;
		if (isWifiConnected()) {
			info = mDataController.getWifiDailyDataUsageInfo();
			suffix = getWifiSsid();
		} else {
			mDataController.setSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
			info = mDataController.getDailyDataUsageInfo();
			suffix = getSlotCarrierName();
		}
		mUsageHeader.setText(" " + suffix);
		mUsageText.setText(formatDataUsage(info.usageLevel) + " " + mContext.getResources().getString(R.string.usage_data));
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }

    private boolean isWifiConnected() {
		final Network network = mConnectivityManager.getActiveNetwork();
		if (network != null) {
			NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
			return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
		} else {
			return false;
		}
    }
    
    private String getSlotCarrierName() {
		CharSequence result = mContext.getResources().getString(R.string.usage_data_default_suffix);
		int subId = mSubManager.getDefaultDataSubscriptionId();
        final List<SubscriptionInfo> subInfoList = mSubManager.getActiveSubscriptionInfoList(true);
		if (subInfoList != null) {
			for (SubscriptionInfo subInfo : subInfoList) {
				if (subId == subInfo.getSubscriptionId()) {
					result = subInfo.getDisplayName();
					break;
				}
			}
		}
		return result.toString();
    }
    
    private String getWifiSsid() {
		final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
		if (wifiInfo.getHiddenSSID() || wifiInfo.getSSID() == WifiManager.UNKNOWN_SSID) {
			return mContext.getResources().getString(R.string.usage_wifi_default_suffix);
		} else {
			return wifiInfo.getSSID().replace("\"", "");
		}
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    private void updateResources() {
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.qs_footers_margin_bottom);
        setLayoutParams(lp);
    }

    /** */
    public void setKeyguardShowing() {
        setExpansion(mExpansionAmount);
    }

    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

    /** */
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mUsageText == null) return;
        
        float alpha = Math.min(1, headerExpansionFraction);
        animateQsFooterWidgets(alpha);

        if (headerExpansionFraction == 1.0f) {
	        mUsageText.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mUsageText.setSelected(true);
                }
	        }, 1000);
        } else {
	        mUsageText.setSelected(false);
        }
    }
    
    private void animateQsFooterWidgets(float alpha) {
        mPageIndicator.setAlpha(alpha);
        mShorcutsButton.setAlpha(alpha);
        mUserButton.setAlpha(alpha);
        mDataUsageButton.setAlpha(alpha);
    }

    void disable(int state2) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    void updateEverything() {
        post(() -> {
            setUsageText();
            updateProfileView();
            setClickable(false);
        });
    }

    private void updateVisibilities() {
		mUsageText.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
		if (mExpanded) setUsageText();
		mUserAvatar.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
		mUserName.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
		if (mExpanded) updateProfileView();
    }
    
    public int dpToPx(int dp) {
        return (int) ((dp * mContext.getResources().getDisplayMetrics().density) + 0.5);
    }
    
    private String getUserName(){
        String username = mUserManager.getUserName();
        return (username != null || !username.isEmpty()) ? mUserManager.getUserName() : mContext.getResources().getString(R.string.quick_settings_user_title);
    }
    
    private Drawable getCircularUserIcon(Context context) {
        Bitmap bitmapUserIcon = mUserManager.getUserIcon(UserHandle.myUserId());
        if (bitmapUserIcon == null) {
            final Drawable defaultUserIcon = UserIcons.getDefaultUserIcon(
                    mContext.getResources(), UserHandle.myUserId(), false);
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon);
        }
        Drawable drawableUserIcon = new CircleFramedDrawable(bitmapUserIcon,
                (int) mContext.getResources().getDimension(R.dimen.qs_footer_avatar_size));
        return drawableUserIcon;
    }
}
