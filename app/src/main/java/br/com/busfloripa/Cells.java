package br.com.busfloripa;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Células da rede da operadora (antenas) que o celular está vendo agora.
 * Serve para reconhecer um lugar sem esperar o GPS: é lido na hora, mesmo dentro de casa.
 * Regra do Android: só funciona com a localização do celular ligada e a permissão de localização.
 */
public final class Cells {

    private Cells() { }

    /** A localização do celular (o botão "Usar localização") está ligada? */
    public static boolean locationOn(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /** Identificadores das células vistas agora, ex.: "LTE:724-05-1234-56789012". Vazio se não der. */
    @SuppressLint("MissingPermission")
    public static List<String> current(Context c) {
        Set<String> out = new LinkedHashSet<>();
        if (!Loc.granted(c)) return new ArrayList<>(out);
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return new ArrayList<>(out);
        List<CellInfo> infos;
        try { infos = tm.getAllCellInfo(); } catch (SecurityException e) { infos = null; }
        if (infos == null) return new ArrayList<>(out);
        for (CellInfo ci : infos) {
            String id = null;
            try {
                if (ci instanceof CellInfoLte) {
                    CellIdentityLte x = ((CellInfoLte) ci).getCellIdentity();
                    id = valid(x.getCi()) ? "LTE:" + plmnOf(Build.VERSION.SDK_INT >= 28 ? x.getMccString() : null, Build.VERSION.SDK_INT >= 28 ? x.getMncString() : null, x.getMcc(), x.getMnc()) + "-" + x.getTac() + "-" + x.getCi() : null;
                } else if (ci instanceof CellInfoGsm) {
                    CellIdentityGsm x = ((CellInfoGsm) ci).getCellIdentity();
                    id = valid(x.getCid()) ? "GSM:" + plmnOf(Build.VERSION.SDK_INT >= 28 ? x.getMccString() : null, Build.VERSION.SDK_INT >= 28 ? x.getMncString() : null, x.getMcc(), x.getMnc()) + "-" + x.getLac() + "-" + x.getCid() : null;
                } else if (ci instanceof CellInfoWcdma) {
                    CellIdentityWcdma x = ((CellInfoWcdma) ci).getCellIdentity();
                    id = valid(x.getCid()) ? "WCDMA:" + plmnOf(Build.VERSION.SDK_INT >= 28 ? x.getMccString() : null, Build.VERSION.SDK_INT >= 28 ? x.getMncString() : null, x.getMcc(), x.getMnc()) + "-" + x.getLac() + "-" + x.getCid() : null;
                } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr) {
                    CellIdentityNr x = (CellIdentityNr) ((CellInfoNr) ci).getCellIdentity();
                    long nci = x.getNci();
                    id = nci > 0 && nci != Long.MAX_VALUE ? "NR:" + plmn(x.getMccString(), x.getMncString()) + "-" + x.getTac() + "-" + nci : null;
                }
            } catch (Throwable ignored) { }
            if (id != null) out.add(id);
        }
        return new ArrayList<>(out);
    }

    /** Android 9 ou mais novo tem os textos; no Android 8, usa os números antigos. */
    private static String plmnOf(String mccS, String mncS, int mcc, int mnc) {
        return plmn(mccS != null ? mccS : valid(mcc) ? String.valueOf(mcc) : null,
                mncS != null ? mncS : valid(mnc) ? String.valueOf(mnc) : null);
    }

    private static boolean valid(int v) { return v > 0 && v != Integer.MAX_VALUE; }

    private static String plmn(String mcc, String mnc) {
        return (mcc == null ? "?" : mcc) + "-" + (mnc == null ? "?" : mnc);
    }

    /** Alguma célula em comum? */
    public static boolean match(Collection<String> saved, Collection<String> now) {
        if (saved == null || now == null) return false;
        for (String s : now) if (saved.contains(s)) return true;
        return false;
    }

    /** Junta células novas às já gravadas (o celular vê antenas diferentes em cada momento), até 40. */
    public static void merge(List<String> saved, Collection<String> now) {
        for (String s : now) if (!saved.contains(s)) saved.add(s);
        while (saved.size() > 40) saved.remove(0);
    }
}
