package pt.ricardo.falaxavier;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Locale;

/**
 * Pictovoz. Mostra o quadro de comunicação (assets/index.html) num WebView,
 * dá-lhe a voz do Android (TextToSpeech) e deixa tirar fotos com a câmara.
 */
public class MainActivity extends Activity {

    private static final int REQ_FOTO = 42;
    private static final String FOTO = "foto.jpg";

    private WebView web;
    private TextToSpeech tts;
    private volatile String estadoVoz = "a preparar";
    private ValueCallback<Uri[]> fotoCallback;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);
        esconderBarras();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fotoCallback != null) fotoCallback.onReceiveValue(null);
                fotoCallback = callback;
                abrirEscolhaDeFoto();
                return true;
            }
        });
        web.addJavascriptInterface(new Ponte(), "AndroidTTS");

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                estadoVoz = "sem voz";
                return;
            }
            int r = tts.setLanguage(new Locale("pt", "PT"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                estadoVoz = "sem voz de Portugal";
            } else {
                estadoVoz = "pt-PT";
            }
        });

        // Base https para o armazenamento (IndexedDB) funcionar e ficar guardado na app.
        web.loadDataWithBaseURL("https://fala-xavier.app/", lerAsset("index.html"),
                "text/html", "utf-8", null);
    }

    /** Funções que a página pode chamar. */
    public class Ponte {
        @JavascriptInterface
        public void speak(String texto, float velocidade) {
            if (tts == null) return;
            tts.setSpeechRate(velocidade);
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "fx");
        }

        @JavascriptInterface
        public String status() {
            return estadoVoz;
        }

        @JavascriptInterface
        public void openUrl(String url) {
            if (url == null) return;
            final String u = url;
            if (!(u.startsWith("https://") || u.startsWith("mailto:"))) return;
            runOnUiThread(() -> {
                try {
                    Intent i = u.startsWith("mailto:")
                            ? new Intent(Intent.ACTION_SENDTO, Uri.parse(u))
                            : new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public String appVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent("com.android.settings.TTS_SETTINGS");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) { }
                }
            });
        }
    }

    private void abrirEscolhaDeFoto() {
        Intent galeria = new Intent(Intent.ACTION_GET_CONTENT);
        galeria.addCategory(Intent.CATEGORY_OPENABLE);
        galeria.setType("image/*");
        Intent escolha = Intent.createChooser(galeria, "Tirar ou escolher foto");

        try {
            File f = new File(getCacheDir(), FOTO);
            if (f.exists()) f.delete();
            cameraUri = Uri.parse("content://" + getPackageName() + ".fotos/" + FOTO);
            Intent camara = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camara.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camara.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            escolha.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camara});
        } catch (Exception e) {
            cameraUri = null;
        }

        try {
            startActivityForResult(escolha, REQ_FOTO);
        } catch (ActivityNotFoundException e) {
            fotoCallback.onReceiveValue(null);
            fotoCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FOTO || fotoCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri[] resultado = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                resultado = new Uri[]{data.getData()};
            } else {
                File f = new File(getCacheDir(), FOTO);
                if (cameraUri != null && f.exists() && f.length() > 0) {
                    resultado = new Uri[]{cameraUri};
                }
            }
        }
        fotoCallback.onReceiveValue(resultado);
        fotoCallback = null;
    }

    @Override
    public void onBackPressed() {
        // O botão "voltar" só fecha o modo pais, para o Xavier não sair da app sem querer.
        web.evaluateJavascript("window.fxBack && window.fxBack()", null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) esconderBarras();
    }

    @SuppressWarnings("deprecation")
    private void esconderBarras() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private String lerAsset(String nome) {
        try (InputStream in = getAssets().open(nome)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<h1>Não foi possível abrir a app</h1>";
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
