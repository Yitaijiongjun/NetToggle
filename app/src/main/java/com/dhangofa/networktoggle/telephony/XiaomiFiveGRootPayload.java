package com.dhangofa.networktoggle.telephony;

import androidx.annotation.Keep;

/** Runs the vendor setter under root, rather than the ordinary application UID. */
@Keep
public final class XiaomiFiveGRootPayload {
    @Keep
    public static void main(String[] args) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lmiui/telephony/", "Landroid/os/");
            }
            MiuiTelephonyAccess phone = new MiuiTelephonyAccess(MiuiTelephonyAccess.findBinder());
            int slot = Integer.parseInt(args[1]);
            int defaultSlot = Integer.parseInt(args[2]);
            if ("set".equals(args[0])) phone.write(Boolean.parseBoolean(args[3]), slot, defaultSlot);
            System.out.println("fiveg=" + phone.read(slot, defaultSlot));
        } catch (Throwable error) {
            System.err.println(TelephonyMethodHelper.describe(error));
            System.exit(1);
        }
    }
}
