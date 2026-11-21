package br.com.busfloripa;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Baixa visão: o app segue as configurações de acessibilidade do Android.
 * Tamanho da fonte, texto em negrito, tamanho de exibição, ampliação, inversão e correção de cor,
 * tema escuro e texto em alto contraste já funcionam sozinhos (textos em sp, tema DayNight do sistema).
 * Aqui: quando o Android está com Contraste médio ou alto (Android 14 ou mais novo),
 * as telas usam o tema de alto contraste, e mudam na hora se a pessoa trocar o contraste.
 */
public class App extends Application {

    private static final java.util.WeakHashMap<Activity, Boolean> applied = new java.util.WeakHashMap<>();

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityPreCreated(Activity a, Bundle b) { apply(a); }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) {
                Boolean was = applied.get(a);
                if (was != null && was != highContrast(a)) a.recreate();
            }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { applied.remove(a); }
        });
    }

    /** Aplica o alto contraste antes de a tela montar os textos. */
    static void apply(Activity a) {
        boolean hc = highContrast(a);
        if (hc) a.getTheme().applyStyle(R.style.HighContrast, true);
        applied.put(a, hc);
    }

    /** Contraste do sistema (Configurações, Acessibilidade, Cor e movimento, Contraste): médio ou alto. */
    static boolean highContrast(android.content.Context c) {
        if (android.os.Build.VERSION.SDK_INT < 34) return false;
        android.app.UiModeManager um = c.getSystemService(android.app.UiModeManager.class);
        return um != null && um.getContrast() >= 0.5f;
    }
}
