package com.dhangofa.networktoggle.telephony;

import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Keep;
import java.lang.reflect.Method;

@Keep
public final class NetworkModeRootReadPayload {
    @Keep
    public static void main(String[] args) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/os/", "Lcom/android/internal/telephony/");
            }
            int subId = Integer.parseInt(args[0]);
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder raw = (IBinder) sm.getDeclaredMethod("getService", String.class).invoke(null, "phone");
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, raw);
            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");
            Method method = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? TelephonyMethodHelper.find(api, "effective".equals(args[1])
                        ? "getAllowedNetworkTypesBitmask" : "getAllowedNetworkTypesForReason",
                        "effective".equals(args[1]) ? new Class<?>[] {int.class} : new Class<?>[] {int.class, int.class})
                    : TelephonyMethodHelper.find(api, "getPreferredNetworkType", new Class<?>[] {int.class});
            Object result = method.getParameterCount() == 2 ? method.invoke(phone, subId, 0) : method.invoke(phone, subId);
            if (!(result instanceof Number)) throw new IllegalStateException("Invalid phone readback");
            System.out.println("mode=" + result);
        } catch (Throwable error) {
            System.err.println(TelephonyMethodHelper.describe(error));
            System.exit(1);
        }
    }
}
