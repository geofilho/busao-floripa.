package br.com.busfloripa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Verifica se há versão nova publicada no GitHub (Releases do repositório), mostra as novidades
 * e, com o OK da pessoa, baixa e abre o instalador. Mesmo esquema do Gerenciador de TTS G.
 * O repositório precisa estar público para o app conseguir ler.
 */
final class AppUpdater {

    /** Última versão publicada pelo GitHub Actions (tag build-N, arquivo BusaoFloripa.apk). */
    static final String INFO_URL = "https://api.github.com/repos/geofilho/busao-floripa./releases/latest";

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy;
    /** Quando precisa liberar "instalar apps desconhecidos", instala ao voltar. */
    private boolean installAfterPermission;

    AppUpdater(Activity act) { this.act = act; }

    void check() {
        if (busy) return;
        busy = true;
        Ui.say(act, "Procurando atualização");
        new Thread(() -> {
            String error = null;
            JSONObject info = null;
            try {
                info = new JSONObject(new String(download(INFO_URL), "UTF-8"));
            } catch (IOException e) {
                error = e.getMessage() != null && e.getMessage().contains("404")
                        ? "Não encontrei versões publicadas. O repositório do app no GitHub precisa estar público."
                        : "Não foi possível verificar a atualização. Confira se o celular está conectado à internet e tente de novo.";
            } catch (Exception e) {
                error = "Não foi possível ler a resposta do GitHub. Tente de novo mais tarde.";
            }
            final JSONObject result = info;
            final String err = error;
            ui.post(() -> {
                busy = false;
                if (act.isFinishing()) return;
                if (err != null) message("Atualizar aplicativo", err);
                else showResult(result);
            });
        }).start();
    }

    /** Chamar no onResume da tela: continua a instalação depois da permissão. */
    void onResume() {
        if (installAfterPermission && canInstall()) {
            installAfterPermission = false;
            install();
        }
    }

    /** Número da versão a partir da tag "build-15". */
    static int codeFromTag(String tag) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(tag == null ? "" : tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void showResult(JSONObject info) {
        int remoteCode = codeFromTag(info.optString("tag_name"));
        String apkUrl = "";
        JSONArray assets = info.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name").endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break; }
            }
        }
        String news = info.optString("body", "").trim();
        int localCode = 0;
        String localName = "";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            localCode = pi.versionCode;
            localName = pi.versionName;
        } catch (Exception ignored) { }
        if (remoteCode <= localCode || apkUrl.isEmpty()) {
            message("Atualizar aplicativo", "Você já está com a versão mais recente, " + localName + ".");
            return;
        }
        StringBuilder msg = new StringBuilder();
        msg.append("Versão instalada: ").append(localName).append(".\n");
        msg.append("Nova versão: 1.").append(remoteCode).append(".\n\n");
        if (!news.isEmpty()) msg.append("Novidades:\n").append(news).append("\n\n");
        msg.append("Deseja baixar e instalar agora? Suas listas continuam salvas.");
        final String url = apkUrl;
        new AlertDialog.Builder(act)
                .setTitle("Atualização disponível")
                .setMessage(msg.toString())
                .setPositiveButton("OK, baixar", (d, w) -> downloadApk(url))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void downloadApk(final String url) {
        final AlertDialog wait = new AlertDialog.Builder(act)
                .setTitle("Atualizar aplicativo")
                .setMessage("Baixando a atualização, aguarde.")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            boolean ok;
            try {
                byte[] data = download(url);
                File f = ApkProvider.file(act);
                try (OutputStream os = new FileOutputStream(f)) { os.write(data); }
                // Confere se o arquivo é mesmo uma versão deste aplicativo.
                PackageInfo pi = act.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                ok = pi != null && act.getPackageName().equals(pi.packageName);
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            ui.post(() -> {
                try { wait.dismiss(); } catch (Exception ignored) { }
                if (act.isFinishing()) return;
                if (success) install();
                else message("Atualizar aplicativo", "Não foi possível baixar a atualização. Tente de novo mais tarde.");
            });
        }).start();
    }

    private boolean canInstall() {
        return act.getPackageManager().canRequestPackageInstalls();
    }

    private void install() {
        if (!canInstall()) {
            new AlertDialog.Builder(act)
                    .setTitle("Permissão necessária")
                    .setMessage("Para instalar a atualização, o Android precisa que você permita que o "
                            + "Busão Floripa G instale aplicativos. Na próxima tela, ative a opção "
                            + "Permitir desta fonte e depois volte. A instalação continua sozinha.")
                    .setPositiveButton("Abrir configuração", (d, w) -> {
                        installAfterPermission = true;
                        try {
                            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + act.getPackageName())));
                        } catch (ActivityNotFoundException e) {
                            act.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        i.setData(ApkProvider.URI);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(i);
        } catch (ActivityNotFoundException e) {
            message("Atualizar aplicativo", "O instalador do Android não abriu.");
        }
    }

    // ----- utilidades -----

    private static byte[] download(String address) throws IOException {
        // O GitHub manda o APK por redirecionamento para outro endereço https: segue até 5 vezes.
        String current = address;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setUseCaches(false);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("User-Agent", "BusaoFloripaG");
            c.setRequestProperty("Accept", current.contains("api.github.com") ? "application/vnd.github+json" : "*/*");
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    current = new URL(new URL(current), c.getHeaderField("Location")).toString();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    return bos.toByteArray();
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("Redirecionamentos demais");
    }

    private void message(String title, String text) {
        new AlertDialog.Builder(act).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
    }
}
