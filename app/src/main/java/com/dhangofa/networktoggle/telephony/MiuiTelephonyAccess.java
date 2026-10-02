package com.dhangofa.networktoggle.telephony;

import android.os.IBinder;
import android.os.IInterface;
import java.lang.reflect.Method;
import java.util.function.IntSupplier;

/** Uses the ROM's AIDL stub, so transaction numbers match the installed ROM. */
final class MiuiTelephonyAccess {
    private final Object service;
    private final Class<?> api;

    MiuiTelephonyAccess(IBinder binder) throws Exception {
        if (binder == null || !binder.pingBinder()) throw new IllegalStateException("Xiaomi telephony Binder unavailable");
        api = Class.forName("miui.telephony.IMiuiTelephony");
        Class<?> stub = Class.forName("miui.telephony.IMiuiTelephony$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        asInterface.setAccessible(true);
        service = asInterface.invoke(null, binder);
        if (service == null) throw new IllegalStateException("IMiuiTelephony unavailable");
    }

    static IBinder findBinder() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method get = sm.getDeclaredMethod("getService", String.class);
        get.setAccessible(true);
        IBinder binder = (IBinder) get.invoke(null, "miui.radio.extphone");
        if (binder != null && binder.pingBinder()) return binder;
        for (String name : new String[] {"miui.telephony.TelephonyManager", "miui.telephony.SubscriptionManager"}) {
            try {
                Class<?> type = Class.forName(name);
                Method getDefault = type.getDeclaredMethod("getDefault");
                getDefault.setAccessible(true);
                Object manager = getDefault.invoke(null);
                Method getMiui = type.getDeclaredMethod("getMiuiTelephony");
                getMiui.setAccessible(true);
                Object phone = getMiui.invoke(manager);
                if (phone instanceof IInterface) return ((IInterface) phone).asBinder();
            } catch (ReflectiveOperationException ignored) {
                // Try the next ROM accessor.
            }
        }
        throw new IllegalStateException("Cannot find miui.radio.extphone Binder");
    }

    boolean read(int slot, IntSupplier defaultDataSlot) throws Exception {
        Method getter = TelephonyMethodHelper.find(api, "isUserFiveGEnabled",
                new Class<?>[] {int.class}, new Class<?>[] {});
        if (getter == null) throw new NoSuchMethodException("isUserFiveGEnabled");
        getter.setAccessible(true);
        if (getter.getParameterCount() == 0) requireDefaultSlot(slot, defaultDataSlot.getAsInt());
        Object value = getter.getParameterCount() == 1 ? getter.invoke(service, slot) : getter.invoke(service);
        if (!(value instanceof Boolean)) throw new IllegalStateException("Invalid Xiaomi 5G readback");
        return (Boolean) value;
    }

    void write(boolean enabled, int slot, IntSupplier defaultDataSlot) throws Exception {
        Method setter = TelephonyMethodHelper.find(api, "setUserFiveGEnabled",
                new Class<?>[] {boolean.class, int.class}, new Class<?>[] {boolean.class});
        if (setter == null) throw new NoSuchMethodException("setUserFiveGEnabled");
        setter.setAccessible(true);
        if (setter.getParameterCount() == 1) requireDefaultSlot(slot, defaultDataSlot.getAsInt());
        Object result = setter.getParameterCount() == 2
                ? setter.invoke(service, enabled, slot) : setter.invoke(service, enabled);
        if (Boolean.FALSE.equals(result)) throw new IllegalStateException("Xiaomi setter returned false");
    }

    private static void requireDefaultSlot(int slot, int defaultDataSlot) {
        if (slot != defaultDataSlot) throw new IllegalStateException("This ROM only supports its default data SIM 5G switch");
    }
}
