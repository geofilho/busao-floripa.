package br.com.busfloripa;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tira a localização (latitude, longitude) de um link compartilhado do Google Maps. */
public final class MapsLink {

    private MapsLink() { }

    private static final Pattern[] PATTERNS = {
            Pattern.compile("!3d(-?\\d{1,2}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)"),
            Pattern.compile("@(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)"),
            Pattern.compile("[?&](?:q|ll|query|center|destination)=(-?\\d{1,2}\\.\\d+),\\s*(-?\\d{1,3}\\.\\d+)"),
            Pattern.compile("/place/(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)"),
    };

    /** Procura coordenadas num texto (link ou página). Nulo se não achar. */
    public static double[] find(String text) {
        if (text == null) return null;
        String t = text;
        try { t = URLDecoder.decode(text, "UTF-8"); } catch (Exception ignored) { }
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                double lat = Double.parseDouble(m.group(1)), lon = Double.parseDouble(m.group(2));
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) return new double[]{lat, lon};
            }
        }
        return null;
    }

    /** Extrai o primeiro link http(s) de um texto colado (o Android costuma colar "Nome do lugar https://..."). */
    public static String firstUrl(String text) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(text == null ? "" : text);
        return m.find() ? m.group() : null;
    }

    /** Segue o link curto (maps.app.goo.gl) e devolve as coordenadas. Faz acesso à internet. */
    public static double[] resolve(String url) throws IOException {
        double[] c = find(url);
        if (c != null) return c;
        String current = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection con = (HttpURLConnection) new URL(current).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BusFloripa/1.0");
            try {
                int code = con.getResponseCode();
                String loc = con.getHeaderField("Location");
                if (code >= 300 && code < 400 && loc != null) {
                    current = new URL(new URL(current), loc).toString();
                    c = find(current);
                    if (c != null) return c;
                    continue;
                }
                if (code != 200) return null;
                try (InputStream in = con.getInputStream()) {
                    byte[] buf = new byte[262144];
                    int off = 0, n;
                    while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                    return find(new String(buf, 0, off, "UTF-8"));
                }
            } finally { con.disconnect(); }
        }
        return null;
    }
}
