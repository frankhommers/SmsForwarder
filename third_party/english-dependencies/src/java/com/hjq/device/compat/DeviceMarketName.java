package com.hjq.device.compat;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

/**
 * Device identity for diagnostics, based on DeviceCompat 2.3 by getActivity.
 * Apache-2.0; see licenses/DeviceCompat-LICENSE.
 * Modified by the SmsForwarder Private contributors: use the manufacturer's
 * stable model identifier instead of a localized marketing-alias dictionary.
 * No permission, OS detection, property mutation or network access is involved.
 */
public final class DeviceMarketName {
    private DeviceMarketName() {}

    @NonNull
    public static String getMarketName(@NonNull Context context) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.toLowerCase(java.util.Locale.ROOT).startsWith(manufacturer.toLowerCase(java.util.Locale.ROOT))) {
            return model;
        }
        return (manufacturer + " " + model).trim();
    }
}
