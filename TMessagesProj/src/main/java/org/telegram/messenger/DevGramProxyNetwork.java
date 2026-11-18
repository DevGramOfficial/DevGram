package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

/** Temporary network exclusions, following exteraGram's proxy disable conditions. */
public final class DevGramProxyNetwork {
    public static final int VPN = 1, MOBILE = 2, WIFI = 4;
    private static final String CONDITIONS = "devgram_proxy_disabled_on";
    private static final String SUSPENDED = "devgram_proxy_auto_disabled";
    private static final int STANDARD = 1, BUILTIN = 2;
    private static final ThreadLocal<Boolean> applying = new ThreadLocal<>();
    private static final Runnable CHECK = DevGramProxyNetwork::checkNow;

    private DevGramProxyNetwork() { }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }

    public static int conditions() {
        return preferences().getInt(CONDITIONS, 0) & (VPN | MOBILE | WIFI);
    }

    public static void setConditions(int value) {
        preferences().edit().putInt(CONDITIONS, value & (VPN | MOBILE | WIFI)).apply();
        requestCheck();
    }

    // Query the network applicable to this app, including per-app VPN exclusions.
    // Unknown/offline is not evidence that a VPN was switched off.
    private static int networkMask() {
        try {
            ConnectivityManager manager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : manager.getNetworkCapabilities(network);
            if (caps == null) return -1;
            return (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? VPN : 0)
                    | (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? MOBILE : 0)
                    | (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? WIFI : 0);
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean shouldDisable() {
        if (ApplicationLoader.applicationContext == null) return false;
        int conditions = conditions();
        if (conditions == 0) return false;
        int network = networkMask();
        return network < 0 ? preferences().getInt(SUSPENDED, 0) != 0 : (conditions & network) != 0;
    }

    public static void requestCheck() {
        AndroidUtilities.cancelRunOnUIThread(CHECK);
        AndroidUtilities.runOnUIThread(CHECK);
    }

    /** Explicit user disable cancels standard-proxy restoration. */
    public static void onManualDisable() {
        if (!Boolean.TRUE.equals(applying.get()) && ApplicationLoader.applicationContext != null) {
            clearRestore(STANDARD);
        }
    }

    public static void clearBuiltinRestore() {
        clearRestore(BUILTIN);
    }

    private static void clearRestore(int bit) {
        SharedPreferences p = preferences();
        int old = p.getInt(SUSPENDED, 0);
        if ((old & bit) != 0) p.edit().putInt(SUSPENDED, old & ~bit).apply();
    }

    static void applyAutomatic(boolean enabled, ProxySettings settings) {
        applying.set(true);
        try {
            ConnectionsManager.setProxySettings(enabled, settings);
        } finally {
            applying.remove();
        }
    }

    private static void checkNow() {
        if (ApplicationLoader.applicationContext == null) return;
        SharedPreferences p = preferences();
        int conditions = conditions(), network = conditions == 0 ? 0 : networkMask();
        if (network < 0) return;
        int suspended = p.getInt(SUSPENDED, 0);
        boolean standard = p.getBoolean("proxy_enabled", false);
        boolean builtin = p.getBoolean("devgram_proxy_on", false);
        if ((conditions & network) != 0) {
            if (!standard && !builtin) return;
            suspended |= (standard ? STANDARD : 0) | (builtin ? BUILTIN : 0);
            p.edit().putInt(SUSPENDED, suspended).putBoolean("proxy_enabled", false)
                    .putBoolean("devgram_proxy_on", false).apply();
            if (builtin) DevGramPlugins.stopWsProxy();
            applyAutomatic(false, null);
        } else if (suspended != 0) {
            ProxySettings settings = ProxySettings.fromSharedPreferences(p);
            standard |= (suspended & STANDARD) != 0 && settings.isValid();
            builtin |= (suspended & BUILTIN) != 0;
            p.edit().putInt(SUSPENDED, 0).putBoolean("proxy_enabled", standard)
                    .putBoolean("devgram_proxy_on", builtin).apply();
            if (builtin) {
                DevGramProxy.ensureLocalStarted();
                applyAutomatic(true, DevGramProxy.ourProxySettings());
            } else {
                applyAutomatic(standard, standard ? settings : null);
            }
        } else {
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }
}
