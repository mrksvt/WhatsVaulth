package com.mrksvt.waen.xposed.bridge;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import com.mrksvt.waen.BuildConfig;
import com.mrksvt.waen.provider.PrefsProvider;
import com.mrksvt.waen.xposed.core.FeatureLoader;
import com.mrksvt.waen.xposed.utils.ReflectionUtils;
import com.mrksvt.waen.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScopeHook {

    private static final String SETTINGS_PROVIDER_CLASS = "com.android.providers.settings.SettingsProvider";

    private static final AtomicReference<Object> pmsRef = new AtomicReference<>();
    private static final AtomicBoolean settingsHooked = new AtomicBoolean(false);

    private static volatile Method getPackagesForUidMethod;
    private static volatile boolean scopeErrorLogged;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookService(lpparam);
                hookSettings(lpparam);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method attachInfo = ContentProvider.class.getDeclaredMethod("attachInfo", Context.class, ProviderInfo.class);
            XposedBridge.hookMethod(attachInfo, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Class<?> providerClass = param.thisObject.getClass();
                    if (!SETTINGS_PROVIDER_CLASS.equals(providerClass.getName())) return;
                    if (!settingsHooked.compareAndSet(false, true)) return;
                    hookSettingsProvider(providerClass);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    private static void hookSettingsProvider(Class<?> providerClass) {
        try {
            // Bundle call(String method, String arg, Bundle extras)
            Method mCall = providerClass.getMethod("call", String.class, String.class, Bundle.class);
            XposedBridge.hookMethod(mCall, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String method = (String) param.args[0];
                        String arg = (String) param.args[1];
                        if ("WaEnhancer".equals(method)) {
                            if ("getHookBinder".equals(arg)) {
                                Context context = ((ContentProvider) param.thisObject).getContext();
                                if (context == null) return;
                                XposedBridge.log("Wa Enhancer: Trying to allow blocking ");
                                try {
                                    XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                                } catch (Throwable ignored) {
                                }
                                var result = Utils.binderLocalScope(() -> {
                                    var uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".hookprovider");
                                    return context.getContentResolver().call(uri, "getHookBinder", null, null);
                                });
                                param.setResult(result);
                                try {
                                    XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                                } catch (Throwable ignored) {
                                }
                                XposedBridge.log("Wa Enhancer: Bypass Scope using Provider Settings");
                            } else if ("getPrefs".equals(arg)) {
                                Context context = ((ContentProvider) param.thisObject).getContext();
                                if (context == null) return;
                                try {
                                    XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                                } catch (Throwable ignored) {
                                }
                                var result = Utils.binderLocalScope(() -> {
                                    var uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".preferences");
                                    return context.getContentResolver().call(uri, PrefsProvider.METHOD_GET_ALL, null, null);
                                });
                                param.setResult(result);
                                try {
                                    XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                                } catch (Throwable ignored) {
                                }
                                XposedBridge.log("Wa Enhancer: Fetched prefs via Provider Settings");
                            }
                        }
                    } catch (Throwable ex) {
                        XposedBridge.log(ex);
                    }
                }
            });
            XposedBridge.log("Hooked SettingsProvider call in Android System");
        } catch (Throwable e) {
            settingsHooked.set(false);
            XposedBridge.log(e);
        }
    }

    private static void hookService(XC_LoadPackage.LoadPackageParam lpparam) {
        var serviceClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader);
        var addService = ReflectionUtils.findMethodUsingFilter(serviceClass, method -> method.getName().equals("addService"));
        var hookedService = new AtomicReference<XC_MethodHook.Unhook>();
        var hooked = XposedBridge.hookMethod(addService, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var service = (String) param.args[0];
                if (Objects.equals(service, "package")) {
                    if (hookedService.get() != null) {
                        hookedService.get().unhook();
                    }
                    new Thread(() -> hookScope(param.args[1], lpparam.classLoader)).start();
                }
            }
        });
        hookedService.set(hooked);
    }

    private static void hookScope(Object pms, ClassLoader loader) {
        pmsRef.set(pms);
        // OEMs may override shouldFilterApplication in AppsFilterImpl on API 33+
        // Thanks to frknkrc44
        List<String> classNames = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            classNames.add("com.android.server.pm.AppsFilterBase");
            classNames.add("com.android.server.pm.AppsFilterImpl");
        } else {
            classNames.add("com.android.server.pm.AppsFilter");
        }
        int hooked = 0;
        for (String className : classNames) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, loader);
                boolean hasMethod = false;
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals("shouldFilterApplication")) {
                        hasMethod = true;
                        break;
                    }
                }
                if (!hasMethod) continue;
                hooked += XposedBridge.hookAllMethods(clazz, "shouldFilterApplication", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (shouldAllowVisibility(param.args)) {
                                param.setResult(Boolean.FALSE);
                            }
                        } catch (Throwable e) {
                            if (!scopeErrorLogged) {
                                scopeErrorLogged = true;
                                XposedBridge.log("Error while hooking Android System");
                                XposedBridge.log(e);
                            }
                        }
                    }
                }).size();
            } catch (Throwable e) {
                XposedBridge.log("Scope hook not applied on " + className + ": " + e.getMessage());
            }
        }
        if (hooked > 0) {
            XposedBridge.log("Hooked visibility Scope in Android System (" + hooked + " methods)");
        } else {
            XposedBridge.log("Unable to hook visibility Scope in Android System");
        }
    }

    private static boolean shouldAllowVisibility(Object[] args) {
        if (args == null || args.length < 4) return false;
        String targetApp = getPackageNameFromPackageSettings(args[args.length - 2]);
        if (!BuildConfig.APPLICATION_ID.equals(targetApp)) return false;

        boolean tiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        Object uidArg = tiramisu ? args[1] : args[0];
        if (!(uidArg instanceof Integer)) return false;
        int callingUid = (Integer) uidArg;
        if (callingUid == Process.SYSTEM_UID) return false;

        String[] callingApps = getPackagesForUid(tiramisu ? args[0] : pmsRef.get(), callingUid);
        if (callingApps == null) return false;
        for (String caller : callingApps) {
            if (FeatureLoader.PACKAGE_WPP.equals(caller) || FeatureLoader.PACKAGE_BUSINESS.equals(caller)) {
                return true;
            }
        }
        return false;
    }

    private static String[] getPackagesForUid(Object computer, int uid) {
        if (computer == null) return null;
        return Utils.binderLocalScope(() -> {
            Method method = getPackagesForUidMethod;
            if (method == null || !method.getDeclaringClass().isInstance(computer)) {
                method = ReflectionUtils.findMethodUsingFilterIfExists(computer.getClass(), candidate ->
                        candidate.getName().equals("getPackagesForUid") &&
                                candidate.getParameterTypes().length == 1 &&
                                candidate.getParameterTypes()[0] == int.class);
                getPackagesForUidMethod = method;
            }
            if (method == null) return null;
            return (String[]) ReflectionUtils.callMethod(method, computer, uid);
        });
    }

    private static String getPackageNameFromPackageSettings(Object packageSettings) {
        if (packageSettings == null) return null;
        try {
            return (String) packageSettings.getClass().getMethod("getPackageName").invoke(packageSettings);
        } catch (Throwable ignored) {
        }
        for (String fieldName : new String[]{"mName", "name"}) {
            try {
                return (String) XposedHelpers.getObjectField(packageSettings, fieldName);
            } catch (Throwable ignored) {
            }
        }
        String packageSettingsString = packageSettings.toString();
        int startIndex = packageSettingsString.lastIndexOf(' ') + 1;
        int endIndex = packageSettingsString.lastIndexOf('/');
        if (endIndex < startIndex) return null;
        return packageSettingsString.substring(startIndex, endIndex);
    }
}
