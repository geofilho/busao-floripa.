package br.com.busfloripa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.function.Consumer;

/** GPS mínimo: só para saber perto de qual lista você está. */
public final class Loc {

    public static final int REQUEST = 41;
    public static final float NEAR_METERS = 500f;

    private Loc() { }

    public static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void request(Activity a) {
        a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST);
    }

    public static float distance(Location l, double lat, double lon) {
        float[] r = new float[1];
        Location.distanceBetween(l.getLatitude(), l.getLongitude(), lat, lon, r);
        return r[0];
    }

    /** Devolve a posição atual (ou nulo) uma única vez. */
    @SuppressLint("MissingPermission")
    public static void current(Context c, int maxAgeMs, Consumer<Location> cb) {
        if (!granted(c)) { cb.accept(null); return; }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.accept(null); return; }
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < maxAgeMs) { cb.accept(best); return; }

        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) { cb.accept(best); return; }

        final Location fallback = best;
        final boolean[] done = {false};
        Handler h = new Handler(Looper.getMainLooper());
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (done[0]) return;
                done[0] = true;
                lm.removeUpdates(this);
                cb.accept(l);
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        };
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
        } catch (SecurityException e) { cb.accept(fallback); return; }
        h.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            lm.removeUpdates(listener);
            cb.accept(fallback);
        }, 15000);
    }
}
