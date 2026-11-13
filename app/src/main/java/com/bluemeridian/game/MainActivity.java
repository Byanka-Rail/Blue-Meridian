package com.bluemeridian.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4109;
    private static final String HOME_URL = "https://appassets.androidplatform.net/assets/index.html";

    // 포병(FIRE DIRECTION LAB)과 같은 업데이트 방식:
    // 저장소 최상위 update.json 을 보고, 더 높은 build 면 그 HTML 을 받아 같은 주소(/assets/index.html)로 띄운다.
    // 주소(origin)가 그대로라 localStorage(캠페인 · 설정)는 유지된다.
    private static final String RAW_BASE = "https://raw.githubusercontent.com/Byanka-Rail/Blue-Meridian/main/";
    private static final String UPDATE_JSON = RAW_BASE + "update.json";
    private static final long MAX_GAME_BYTES = 40L * 1024L * 1024L;
    private static final Pattern TITLE_VER = Pattern.compile("<title>[^<]*?v(\\d+)\\.(\\d+)\\.(\\d+)");

    private WebView webView;
    private SharedPreferences prefs;
    private int bundledBuild = 0;
    private ValueCallback<Uri[]> filePathCallback;
    private volatile boolean checking = false;
    private long lastCheckMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " BlueMeridianApp/" + BuildConfig.VERSION_NAME);

        prefs = getSharedPreferences("bm_update", MODE_PRIVATE);
        bundledBuild = readBundledBuild();
        // 새 APK 에 든 판이 받아 둔 판보다 같거나 높으면 받아 둔 것을 버린다
        if (prefs.getInt("current_build", 0) <= bundledBuild) dropDownloaded();

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.addJavascriptInterface(new AndroidBridge(), "BlueMeridianAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost()) && "/assets/index.html".equals(u.getPath())) {
                    File f = downloadedFile();
                    if (f.isFile() && prefs.getInt("current_build", 0) > bundledBuild) {
                        try {
                            return new WebResourceResponse("text/html", "utf-8", new FileInputStream(f));
                        } catch (Exception ignored) {}
                    }
                }
                return assetLoader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                Intent i = new Intent(Intent.ACTION_VIEW, u);
                try { startActivity(i); } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePath,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePath;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(bestMimeType(fileChooserParams.getAcceptTypes()));
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        if (savedInstanceState == null) webView.loadUrl(HOME_URL);
        else webView.restoreState(savedInstanceState);

        checkForUpdate();
    }

    // ── 업데이트 ─────────────────────────────────────────────
    private File downloadedFile() {
        return new File(new File(getFilesDir(), "game"), "index.html");
    }

    private void dropDownloaded() {
        File f = downloadedFile();
        if (f.exists()) f.delete();
        prefs.edit().remove("current_build").remove("current_version").apply();
    }

    private int readBundledBuild() {
        try (InputStream in = getAssets().open("index.html")) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            return buildFromHtmlHead(new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int buildFromHtmlHead(String head) {
        Matcher m = TITLE_VER.matcher(head);
        if (!m.find()) return 0;
        return Integer.parseInt(m.group(1)) * 100000 + Integer.parseInt(m.group(2)) * 1000 + Integer.parseInt(m.group(3));
    }

    private int currentBuild() {
        return Math.max(bundledBuild, prefs.getInt("current_build", 0));
    }

    private void toast(final String msg, final boolean longer) {
        runOnUiThread(() -> { if (!isFinishing()) Toast.makeText(MainActivity.this, msg, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show(); });
    }

    private String currentVersionLabel() {
        String v = prefs.getString("current_version", "");
        if (!v.isEmpty() && prefs.getInt("current_build", 0) > bundledBuild) return v;
        int b = bundledBuild;
        return (b / 100000) + "." + ((b / 1000) % 100) + "." + (b % 1000);
    }

    // manual=true 면 "최신입니다" · 실패 이유까지 알려 준다. 자동 확인은 새 판이 있을 때만 말한다.
    private void checkForUpdate() { checkForUpdate(false); }

    private void checkForUpdate(final boolean manual) {
        if (checking) { if (manual) toast("업데이트 확인 중입니다", false); return; }
        checking = true;
        lastCheckMs = System.currentTimeMillis();
        if (manual) toast("업데이트 확인 중… (현재 v" + currentVersionLabel() + ")", false);
        new Thread(() -> {
            String stage = "update.json";
            try {
                JSONObject o = new JSONObject(new String(readUrl(UPDATE_JSON + "?_=" + System.currentTimeMillis(), 64 * 1024), StandardCharsets.UTF_8));
                int build = o.optInt("build", 0);
                String file = o.optString("file", "");
                String version = o.optString("version", "");
                String sha = o.optString("sha256", "").trim();
                if (build <= currentBuild() || file.isEmpty()) {
                    if (manual) toast("최신 버전입니다 · v" + currentVersionLabel(), false);
                    return;
                }
                toast("새 버전 v" + version + " 받는 중… 게임은 계속 하셔도 됩니다", true);
                stage = "게임 파일";

                String url = file.startsWith("https://") ? file : RAW_BASE + URLEncoder.encode(file, "UTF-8").replace("+", "%20");
                byte[] bytes = readUrl(url + "?_=" + System.currentTimeMillis(), MAX_GAME_BYTES);
                if (!sha.isEmpty() && !sha.equalsIgnoreCase(sha256(bytes))) throw new Exception("SHA-256 검증 실패");
                String head = new String(bytes, 0, Math.min(bytes.length, 8192), StandardCharsets.UTF_8);
                if (!head.toLowerCase().contains("<html")) throw new Exception("HTML 이 아닙니다");

                File dir = downloadedFile().getParentFile();
                if (dir != null && !dir.isDirectory()) dir.mkdirs();
                File tmp = new File(dir, "index.html.tmp");
                try (FileOutputStream os = new FileOutputStream(tmp)) { os.write(bytes); }
                if (!tmp.renameTo(downloadedFile())) throw new Exception("파일 교체 실패");
                prefs.edit().putInt("current_build", build).putString("current_version", version).apply();

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("BLUE MERIDIAN 업데이트")
                            .setMessage("v" + version + " 을 받았습니다. 지금 다시 불러올까요?\n(나중에 앱을 다시 켜도 적용됩니다)")
                            .setPositiveButton("지금", (d, w) -> webView.loadUrl(HOME_URL + "?_=" + System.currentTimeMillis()))
                            .setNegativeButton("나중에", null)
                            .show();
                });
            } catch (Exception e) {
                // 네트워크 실패나 검증 실패는 게임 실행을 막지 않는다. 다만 새 판을 받던 중이거나 직접 확인한 경우엔 알려 준다.
                if (manual || !"update.json".equals(stage))
                    toast("업데이트 실패 (" + stage + ") · " + e.getMessage(), true);
            } finally {
                checking = false;
            }
        }).start();
    }

    private static byte[] readUrl(String url, long max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) throw new Exception("파일이 너무 큽니다");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String bestMimeType(String[] accepts) {
        if (accepts == null || accepts.length == 0) return "*/*";
        for (String a : accepts) if (a != null && !a.trim().isEmpty() && !"*/*".equals(a)) return a;
        return "*/*";
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("(function(){try{if(document.pointerLockElement)document.exitPointerLock();" +
                    "var ids=['manualPanel','replayWrap','editorPanel','optionsPanel','storyPanel','specPanel'];" +
                    "for(var i=0;i<ids.length;i++){var e=document.getElementById(ids[i]);if(e&&(e.classList.contains('open')||getComputedStyle(e).display!=='none')){" +
                    "var b=e.querySelector('[id$=Close],#replayBack,#storyClose,#editorClose,#optionsClose,#specClose');if(b){b.click();return 'handled';}}}" +
                    "return 'none';}catch(e){return 'none';}})()", value -> {
                if (value == null || !value.contains("handled")) showAppMenu();
            });
        } else {
            super.onBackPressed();
        }
    }

    // 뒤로가기(열린 창이 없을 때) → 앱 메뉴. 종료 확인을 겸하고, 여기서 업데이트를 직접 확인할 수 있다.
    private void showAppMenu() {
        if (isFinishing()) return;
        String[] items = {"업데이트 확인", "게임 종료"};
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("BLUE MERIDIAN · v" + currentVersionLabel())
                .setItems(items, (d, which) -> {
                    if (which == 0) checkForUpdate(true);
                    else MainActivity.this.finish();
                })
                .setNegativeButton("계속하기", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 앱을 오래 백그라운드에 두었다가 돌아오면 다시 확인 (30분 간격)
        if (prefs != null && lastCheckMs > 0 && System.currentTimeMillis() - lastCheckMs > 30L * 60L * 1000L) checkForUpdate(false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private final class AndroidBridge {
        // 게임 화면에서 부를 수 있는 업데이트 훅: BlueMeridianAndroid.checkUpdate() / BlueMeridianAndroid.appInfo()
        @JavascriptInterface
        public void checkUpdate() { runOnUiThread(() -> checkForUpdate(true)); }

        @JavascriptInterface
        public String appInfo() {
            return "{\"app\":\"" + BuildConfig.VERSION_NAME + "\",\"game\":\"" + currentVersionLabel() + "\"}";
        }

        @JavascriptInterface
        public void saveDataUrl(String requestedName, String dataUrl) {
            runOnUiThread(() -> {
                try {
                    if (dataUrl == null || !dataUrl.startsWith("data:")) throw new IllegalArgumentException("bad data URL");
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0) throw new IllegalArgumentException("bad data URL");
                    String meta = dataUrl.substring(5, comma);
                    String payload = dataUrl.substring(comma + 1);
                    String mime = meta.split(";")[0];
                    if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                    boolean b64 = meta.contains(";base64");
                    byte[] bytes = b64 ? Base64.decode(payload, Base64.DEFAULT)
                            : URLDecoder.decode(payload, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);

                    String name = sanitizeFilename(requestedName, mime);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlueMeridian");

                    Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri uri = getContentResolver().insert(collection, values);
                    if (uri == null) throw new IllegalStateException("MediaStore insert failed");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new IllegalStateException("openOutputStream failed");
                        os.write(bytes);
                    }
                    Toast.makeText(MainActivity.this, "저장됨 · Downloads/BlueMeridian/" + name, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "내보내기 실패 · " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        private String sanitizeFilename(String raw, String mime) {
            String name = (raw == null || raw.trim().isEmpty()) ? "BLUE_MERIDIAN_export" : raw.trim();
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!name.contains(".")) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (ext != null && !ext.isEmpty()) name += "." + ext;
            }
            return name;
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("BlueMeridianAndroid");
            webView.destroy();
        }
        super.onDestroy();
    }
}
