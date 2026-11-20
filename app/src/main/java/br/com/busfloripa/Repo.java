package br.com.busfloripa;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Busca dados: primeiro o que está salvo no celular, depois o site do Fênix.
 * Sem internet, usa a cópia salva.
 */
public final class Repo {

    public interface Callback<T> { void done(T result, String error); }


    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Store store;
    public Repo(Context c) { store = Store.get(c); }

    static String download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BusFloripa/1.0");
        c.setRequestProperty("Accept-Language", "pt-BR");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("O site respondeu com erro " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    /** Lista de todas as linhas. Usa a cópia salva e atualiza se estiver velha. */
    public void lines(boolean force, Callback<List<FenixParser.Line>> cb) {
        IO.execute(() -> {
            List<FenixParser.Line> cached = store.loadLines();
            // A atualização diária fica com o Updater; aqui só baixa se não houver cópia ou se pedirem.
            boolean stale = force || cached.isEmpty();
            if (!cached.isEmpty()) MAIN.post(() -> cb.done(cached, null));
            if (!stale) return;
            try {
                List<FenixParser.Line> fresh = FenixParser.parseIndex(download(FenixParser.INDEX_URL));
                if (fresh.isEmpty()) throw new IOException("Não encontrei linhas na página do Fênix");
                store.saveLines(fresh);
                MAIN.post(() -> cb.done(fresh, null));
            } catch (Exception e) {
                if (cached.isEmpty()) MAIN.post(() -> cb.done(null, message(e)));
            }
        });
    }

    /**
     * Linhas intermunicipais de todas as empresas. Usa a cópia salva; baixa se não houver ou se pedir.
     * Empresa que falhar mantém as linhas salvas dela.
     */
    public void interLines(boolean force, Callback<List<FenixParser.Line>> cb) {
        IO.execute(() -> {
            List<FenixParser.Line> cached = store.loadLines(Store.INTER_FILE);
            if (!cached.isEmpty()) MAIN.post(() -> cb.done(cached, null));
            if (!force && !cached.isEmpty()) return;
            List<FenixParser.Line> merged = new java.util.ArrayList<>();
            String lastErr = null;
            for (Providers.Source s : Providers.INTER) {
                try {
                    List<FenixParser.Line> l = Providers.parseIndex(s, download(s.indexUrl));
                    if (l.isEmpty()) throw new IOException("não encontrei linhas no site da " + s.company);
                    merged.addAll(l);
                } catch (Exception e) {
                    lastErr = s.company + ": " + message(e);
                    for (FenixParser.Line l : cached) if (l.code.startsWith(s.prefix + "#")) merged.add(l);
                }
            }
            if (!merged.isEmpty()) store.saveLines(Store.INTER_FILE, merged);
            final String err = lastErr;
            if (!merged.isEmpty()) MAIN.post(() -> cb.done(merged, null));
            else if (cached.isEmpty()) MAIN.post(() -> cb.done(null, err));
        });
    }

    /** Horários salvos, sem internet. Pode ser nulo. */
    public FenixParser.Timetable cached(String code) { return store.loadTimetable(code); }

    /** Baixa os horários de uma linha e salva. */
    public void download(String code, String name, String url, Callback<FenixParser.Timetable> cb) {
        IO.execute(() -> {
            try {
                FenixParser.Timetable tt = Providers.parseLine(code, download(url));
                tt.code = code;
                tt.name = name;
                int total = 0;
                for (FenixParser.Departure d : tt.departures) total += d.total();
                if (total == 0) throw new IOException("Não consegui ler os horários desta linha no site");
                store.saveTimetable(tt);
                MAIN.post(() -> cb.done(tt, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, message(e)));
            }
        });
    }

    /** Horários: cópia salva se houver; senão baixa. A atualização diária fica com o Updater. */
    public void timetable(String code, String name, String url, Callback<FenixParser.Timetable> cb) {
        FenixParser.Timetable c = cached(code);
        if (c != null) cb.done(c, null);
        else download(code, name, url, cb);
    }

    /** Falta de internet (e não problema no site). */
    static boolean isNetwork(Exception e) {
        return e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException || e instanceof java.net.NoRouteToHostException;
    }

    static String message(Exception e) {
        if (isNetwork(e)) return "Sem conexão com a internet";
        String m = e.getMessage();
        return m != null ? m : "Erro ao baixar";
    }
}
