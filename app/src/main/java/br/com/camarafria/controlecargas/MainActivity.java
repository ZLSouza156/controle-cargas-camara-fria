package br.com.camarafria.controlecargas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final int PICK_FILE = 100, SAVE_FILE = 101, MAX_BYTES = 15 * 1024 * 1024;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final AtomicBoolean fileActionBusy = new AtomicBoolean(false);
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private File pendingExport;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 38, 59));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(Color.rgb(242, 244, 247));
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                WebResourceResponse local = loader.shouldInterceptRequest(req.getUrl());
                return local != null ? local : new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                    Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !isAppPage(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAppPage(Uri.parse(url));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> result, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = result;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                try { startActivityForResult(intent, PICK_FILE); }
                catch (Exception e) { fileCallback.onReceiveValue(null); fileCallback = null; message("Não foi possível abrir o seletor de arquivos."); }
                return true;
            }
        });
        web.addJavascriptInterface(new AndroidFiles(), "CamaraAndroid");
        if (saved != null) {
            String name = saved.getString("pendingExport");
            if (name != null) {
                File pending = new File(exportDirectory(), cleanName(name));
                if (pending.isFile()) { pendingExport = pending; fileActionBusy.set(true); }
            }
        }
        web.loadUrl(START_URL);
    }

    private boolean isAppPage(Uri uri) {
        return "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())
            && "/assets/index.html".equals(uri.getPath());
    }
    private File exportDirectory() { File dir = new File(getCacheDir(), "exports"); dir.mkdirs(); return dir; }
    private String cleanName(String value) {
        String name = value == null ? "arquivo" : value.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        name = name.replaceAll("^\\.+", "");
        return name.isEmpty() ? "arquivo" : name.substring(0, Math.min(160, name.length()));
    }
    private void message(String text) { runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show()); }
    public final class AndroidFiles {
        @JavascriptInterface public void writeFile(String encoded, String fileName, String fileType, boolean share) {
            if (!fileActionBusy.compareAndSet(false, true)) { message("Conclua a operação de arquivo em andamento."); return; }
            files.execute(() -> {
                try {
                    if (encoded == null || encoded.length() > MAX_BYTES * 4 / 3 + 64) throw new IllegalArgumentException();
                    String mime = fileType == null ? "" : fileType.split(";")[0];
                    if (!mime.equals("application/json") && !mime.equals("text/plain")) throw new IllegalArgumentException();
                    byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException();
                    String name = cleanName(fileName), extension = mime.equals("application/json") ? ".json" : ".txt";
                    if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) name += extension;
                    // Each export has its own directory entry so an earlier shared file is not overwritten.
                    File file = new File(exportDirectory(), System.currentTimeMillis() + "-" + name);
                    try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                    String displayName = name;
                    runOnUiThread(() -> {
                        try {
                            if (share) {
                                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", file);
                                Intent intent = new Intent(Intent.ACTION_SEND).setType(mime)
                                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setClipData(ClipData.newRawUri(displayName, uri));
                                startActivity(Intent.createChooser(intent, "Compartilhar resumo diário"));
                                fileActionBusy.set(false);
                            } else {
                                pendingExport = file;
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(mime).putExtra(Intent.EXTRA_TITLE, displayName);
                                startActivityForResult(intent, SAVE_FILE);
                            }
                        } catch (Exception e) { pendingExport = null; fileActionBusy.set(false); message("Não foi possível abrir o destino do arquivo."); }
                    });
                } catch (Exception e) { fileActionBusy.set(false); message("Não foi possível preparar o arquivo (limite: 15 MB)."); }
            });
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILE && fileCallback != null) {
            Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
            fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri}); fileCallback = null;
        }
        if (request == SAVE_FILE) {
            File source = pendingExport; pendingExport = null;
            Uri destination = result == RESULT_OK && data != null ? data.getData() : null;
            if (destination == null || source == null) { if (source != null) source.delete(); fileActionBusy.set(false); return; }
            files.execute(() -> {
                try (InputStream in = new java.io.FileInputStream(source);
                     OutputStream out = getContentResolver().openOutputStream(destination)) {
                    if (out == null) throw new java.io.IOException("No output stream");
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    message("Arquivo salvo.");
                } catch (Exception e) { message("Não foi possível salvar o arquivo. Tente exportar novamente."); }
                finally { source.delete(); fileActionBusy.set(false); }
            });
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (pendingExport != null) out.putString("pendingExport", pendingExport.getName());
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("typeof window.CamaraNavegacaoVoltar==='function' ? window.CamaraNavegacaoVoltar() : false", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        web.removeJavascriptInterface("CamaraAndroid"); web.destroy(); files.shutdown(); super.onDestroy();
    }
}
