package com.flutter_webview_plugin;

import static android.app.Activity.RESULT_OK;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Created by lejard_h on 20/12/2017. */
class WebviewManager {
  private static final String TAG = "WebviewManager";
  private ValueCallback<Uri> mUploadMessage;
  private ValueCallback<Uri[]> mUploadMessageArray;
  private static final int FILECHOOSER_RESULTCODE = 1;
  private static final int CAMERA_REQUEST_CODE = 2;
  private static final int LOCATION_REQUEST_CODE = 3;
  private Uri fileUri;
  private Uri videoUri;
  private LocationClient locationClient;

  private long getFileSize(Uri fileUri) {
    Cursor returnCursor = context.getContentResolver().query(fileUri, null, null, null, null);
    returnCursor.moveToFirst();
    int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
    return returnCursor.getLong(sizeIndex);
  }

  @TargetApi(7)
  class ResultHandler {
    public boolean handleResult(int requestCode, int resultCode, Intent intent) {
      boolean handled = false;
      if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
          Uri[] results = null;
          if (resultCode == Activity.RESULT_OK) {
            if (fileUri != null && getFileSize(fileUri) > 0) {
              results = new Uri[] {fileUri};
            } else if (videoUri != null && getFileSize(videoUri) > 0) {
              results = new Uri[] {videoUri};
            } else if (intent != null) {
              results = getSelectedFiles(intent);
            }
          }
          if (mUploadMessageArray != null) {
            mUploadMessageArray.onReceiveValue(results);
            mUploadMessageArray = null;
          }
          handled = true;
        }
      } else {
        if (requestCode == FILECHOOSER_RESULTCODE) {
          Uri result = null;
          if (resultCode == RESULT_OK && intent != null) {
            result = intent.getData();
          }
          if (mUploadMessage != null) {
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
          }
          handled = true;
        }
      }
      return handled;
    }

    public boolean handleRequestPermissionsResult(
        int requestCode, String[] permissions, int[] results) {
      if (LOCATION_REQUEST_CODE == requestCode) {
        int granted = PackageManager.PERMISSION_GRANTED;
        for (int grantResult : results) {
          if (grantResult != PackageManager.PERMISSION_GRANTED) {
            granted = granted|grantResult;
          }
        }
        Log.i(TAG, "授予定位权限：" + granted);
        if (granted==PackageManager.PERMISSION_GRANTED) {
          if (locationClient.isStarted()){
            locationClient.requestLocation();
          }else{
            locationClient.start();
          }
        } else {
          Toast.makeText(context, "您已拒绝应用使用定位的权限.",
              Toast.LENGTH_LONG).show();
          Log.w(TAG, "用户拒绝授予定位权限.");
          try{
            JSONObject params = new JSONObject();
            params.put("status", "03");
            webView.loadUrl("javascript:setLoc(" +params.toString() + ")");
          }catch (Exception e){
            Log.e(TAG, "回传位置失败", e);
          }
        }
      }
      if (CAMERA_REQUEST_CODE == requestCode){
        int granted = PackageManager.PERMISSION_GRANTED;
        for (int grantResult : results) {
          if (grantResult != PackageManager.PERMISSION_GRANTED) {
            granted = granted|grantResult;
          }
        }
        Log.i(TAG, "授予相机权限：" + granted);
        if (granted != PackageManager.PERMISSION_GRANTED){
          Toast.makeText(context, "您必须授予应用访问相机与文件的权限才能使用该功能.",
              Toast.LENGTH_LONG).show();
        }
      }
      return true;
    }
  }

  private Uri[] getSelectedFiles(Intent data) {
    // we have one files selected
    if (data.getData() != null) {
      String dataString = data.getDataString();
      if (dataString != null) {
        return new Uri[] {Uri.parse(dataString)};
      }
    }
    // we have multiple files selected
    if (data.getClipData() != null) {
      final int numSelectedFiles = data.getClipData().getItemCount();
      Uri[] result = new Uri[numSelectedFiles];
      for (int i = 0; i < numSelectedFiles; i++) {
        result[i] = data.getClipData().getItemAt(i).getUri();
      }
      return result;
    }
    return null;
  }

  boolean closed = false;
  WebView webView;
  Activity activity;
  BrowserClient webViewClient;
  ResultHandler resultHandler;
  Context context;

  WebviewManager(final Activity activity, final Context context) {
    this.webView = new ObservableWebView(activity);
    this.activity = activity;
    this.context = context;
    this.resultHandler = new ResultHandler();
    this.locationClient = initLocationClient();
    webViewClient = new BrowserClient(){
      @Override
      public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.proceed();
      }
    };
    webView.setOnKeyListener(
        new View.OnKeyListener() {
          @Override
          public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
              switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                  if (webView.canGoBack()) {
                    webView.goBack();
                  } else {
                    FlutterWebviewPlugin.channel.invokeMethod("onBack", null);
                  }
                  return true;
              }
            }

            return false;
          }
        });

    ((ObservableWebView) webView)
        .setOnScrollChangedCallback(
            new ObservableWebView.OnScrollChangedCallback() {
              @Override
              public void onScroll(int x, int y, int oldx, int oldy) {
                Map<String, Object> yDirection = new HashMap<>(1);
                yDirection.put("yDirection", (double) y);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollYChanged", yDirection);
                Map<String, Object> xDirection = new HashMap<>(1);
                xDirection.put("xDirection", (double) x);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollXChanged", xDirection);
              }
            });

    webView.setWebViewClient(webViewClient);
    webView.setWebChromeClient(
        new WebChromeClient() {
          // The undocumented magic method override
          // Eclipse will swear at you if you try to put @Override here
          // For Android 3.0+
          public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            if (!checkCameraPermissions()){
              requestCameraPermissions();
              return;
            }
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            activity.startActivityForResult(
                Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
          }

          // For Android 3.0+
          public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
            if (!checkCameraPermissions()){
              requestCameraPermissions();
              return;
            }
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            activity.startActivityForResult(
                Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
          }

          // For Android 4.1
          public void openFileChooser(
              ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            if (!checkCameraPermissions()){
              requestCameraPermissions();
              return;
            }
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            activity.startActivityForResult(
                Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
          }

          // For Android 5.0+
          @Override
          public boolean onShowFileChooser(
              WebView webView,
              ValueCallback<Uri[]> filePathCallback,
              FileChooserParams fileChooserParams) {
            if (!checkCameraPermissions()){
              requestCameraPermissions();
              return false;
            }
            if (mUploadMessageArray != null) {
              mUploadMessageArray.onReceiveValue(null);
            }
            mUploadMessageArray = filePathCallback;

            final String[] acceptTypes = getSafeAcceptedTypes(fileChooserParams);
            List<Intent> intentList = new ArrayList<Intent>();
            fileUri = null;
            videoUri = null;
            if (acceptsImages(acceptTypes)) {
              Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
              fileUri = getOutputFilename(MediaStore.ACTION_IMAGE_CAPTURE);
              takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
              intentList.add(takePhotoIntent);
            }
            if (acceptsVideo(acceptTypes)) {
              Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
              videoUri = getOutputFilename(MediaStore.ACTION_VIDEO_CAPTURE);
              takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
              intentList.add(takeVideoIntent);
            }
            Intent contentSelectionIntent;
            if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
              final boolean allowMultiple =
                  fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
              contentSelectionIntent = fileChooserParams.createIntent();
              contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            } else {
              contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
              contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
              contentSelectionIntent.setType("*/*");
            }
            Intent[] intentArray = intentList.toArray(new Intent[intentList.size()]);

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
            activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
            return true;
          }

          @Override
          public void onProgressChanged(WebView view, int progress) {
            Map<String, Object> args = new HashMap<>(1);
            args.put("progress", progress / 100.0);
            FlutterWebviewPlugin.channel.invokeMethod("onProgressChanged", args);
          }
        });
    webView.addJavascriptInterface(
        new BocNativeInterface() {

          @Override
          @JavascriptInterface
          public void getLocNotice() {
            Log.d(TAG, "getLocNotice: 执行Native方法.");
            int checkResult =
                ActivityCompat.checkSelfPermission(activity, permission.READ_PHONE_STATE)
                    | ActivityCompat.checkSelfPermission(activity, permission.ACCESS_FINE_LOCATION);
            Log.i(TAG, "getLocNotice: 权限验证结果:" + checkResult);
            if (checkResult == PackageManager.PERMISSION_GRANTED) {
              if (locationClient.isStarted()){
                locationClient.requestLocation();
              }else{
                locationClient.start();
              }
            } else {
              ActivityCompat.requestPermissions(activity, new String[]{
                  permission.READ_PHONE_STATE,
                  permission.ACCESS_FINE_LOCATION
              }, LOCATION_REQUEST_CODE);
            }
          }
        },
        "BocBridge");
    if(!checkCameraPermissions()){
      requestCameraPermissions();
    }
  }

  private Uri getOutputFilename(String intentType) {
    String prefix = "";
    String suffix = "";

    if (intentType == MediaStore.ACTION_IMAGE_CAPTURE) {
      prefix = "image-";
      suffix = ".jpg";
    } else if (intentType == MediaStore.ACTION_VIDEO_CAPTURE) {
      prefix = "video-";
      suffix = ".mp4";
    }

    String packageName = context.getPackageName();
    File capturedFile = null;
    try {
      capturedFile = createCapturedFile(prefix, suffix);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return FileProvider.getUriForFile(context, packageName + ".fileprovider", capturedFile);
  }

  private File createCapturedFile(String prefix, String suffix) throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = prefix + "_" + timeStamp;
    File storageDir = context.getExternalFilesDir(null);
    return File.createTempFile(imageFileName, suffix, storageDir);
  }

  private Boolean acceptsImages(String[] types) {
    return isArrayEmpty(types) || arrayContainsString(types, "image");
  }

  private Boolean acceptsVideo(String[] types) {
    return isArrayEmpty(types) || arrayContainsString(types, "video");
  }

  private Boolean arrayContainsString(String[] array, String pattern) {
    for (String content : array) {
      if (content.contains(pattern)) {
        return true;
      }
    }
    return false;
  }

  private Boolean isArrayEmpty(String[] arr) {
    // when our array returned from getAcceptTypes() has no values set from the
    // webview
    // i.e. <input type="file" />, without any "accept" attr
    // will be an array with one empty string element, afaik
    return arr.length == 0 || (arr.length == 1 && arr[0].length() == 0);
  }

  private String[] getSafeAcceptedTypes(WebChromeClient.FileChooserParams params) {

    // the getAcceptTypes() is available only in api 21+
    // for lower level, we ignore it
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return params.getAcceptTypes();
    }

    final String[] EMPTY = {};
    return EMPTY;
  }

  private void clearCookies() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance()
          .removeAllCookies(
              new ValueCallback<Boolean>() {
                @Override
                public void onReceiveValue(Boolean aBoolean) {}
              });
    } else {
      CookieManager.getInstance().removeAllCookie();
    }
  }

  private void clearCache() {
    webView.clearCache(true);
    webView.clearFormData();
  }

  void openUrl(
      boolean withJavascript,
      boolean clearCache,
      boolean hidden,
      boolean clearCookies,
      String userAgent,
      String url,
      Map<String, String> headers,
      boolean withZoom,
      boolean withLocalStorage,
      boolean scrollBar,
      boolean supportMultipleWindows,
      boolean appCacheEnabled,
      boolean allowFileURLs,
      boolean useWideViewPort,
      String invalidUrlRegex,
      boolean geolocationEnabled) {
    webView.getSettings().setJavaScriptEnabled(withJavascript);
    webView.getSettings().setBuiltInZoomControls(withZoom);
    webView.getSettings().setSupportZoom(withZoom);
    webView.getSettings().setDomStorageEnabled(withLocalStorage);
    webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(supportMultipleWindows);

    webView.getSettings().setSupportMultipleWindows(supportMultipleWindows);

    webView.getSettings().setAppCacheEnabled(appCacheEnabled);

    webView.getSettings().setAllowFileAccessFromFileURLs(allowFileURLs);
    webView.getSettings().setAllowUniversalAccessFromFileURLs(allowFileURLs);

    webView.getSettings().setUseWideViewPort(useWideViewPort);
    webViewClient.updateInvalidUrlRegex(invalidUrlRegex);

    if (geolocationEnabled) {
      webView.getSettings().setGeolocationEnabled(true);
      webView.setWebChromeClient(
          new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                String origin, GeolocationPermissions.Callback callback) {
              callback.invoke(origin, true, false);
            }
          });
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    if (clearCache) {
      clearCache();
    }

    if (hidden) {
      webView.setVisibility(View.GONE);
    }

    if (clearCookies) {
      clearCookies();
    }

    if (userAgent != null) {
      webView.getSettings().setUserAgentString(userAgent);
    }

    if (!scrollBar) {
      webView.setVerticalScrollBarEnabled(false);
    }

    if (headers != null) {
      webView.loadUrl(url, headers);
    } else {
      webView.loadUrl(url);
    }
  }

  void reloadUrl(String url) {
    webView.loadUrl(url);
  }

  void close(MethodCall call, MethodChannel.Result result) {
    if (webView != null) {
      ViewGroup vg = (ViewGroup) (webView.getParent());
      vg.removeView(webView);
    }
    webView = null;
    if (result != null) {
      result.success(null);
    }

    closed = true;
    FlutterWebviewPlugin.channel.invokeMethod("onDestroy", null);
  }

  void close() {
    close(null, null);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  void eval(MethodCall call, final MethodChannel.Result result) {
    String code = call.argument("code");

    webView.evaluateJavascript(
        code,
        new ValueCallback<String>() {
          @Override
          public void onReceiveValue(String value) {
            result.success(value);
          }
        });
  }
  /** Reloads the Webview. */
  void reload(MethodCall call, MethodChannel.Result result) {
    if (webView != null) {
      webView.reload();
    }
  }

  /** Navigates back on the Webview. */
  void back(MethodCall call, MethodChannel.Result result) {
    if (webView != null && webView.canGoBack()) {
      webView.goBack();
    }
  }
  /** Navigates forward on the Webview. */
  void forward(MethodCall call, MethodChannel.Result result) {
    if (webView != null && webView.canGoForward()) {
      webView.goForward();
    }
  }

  void resize(FrameLayout.LayoutParams params) {
    webView.setLayoutParams(params);
  }
  /** Checks if going back on the Webview is possible. */
  boolean canGoBack() {
    return webView != null && webView.canGoBack();
  }
  /** Checks if going forward on the Webview is possible. */
  boolean canGoForward() {
    return webView != null && webView.canGoForward();
  }

  void hide(MethodCall call, MethodChannel.Result result) {
    if (webView != null) {
      webView.setVisibility(View.GONE);
    }
  }

  void show(MethodCall call, MethodChannel.Result result) {
    if (webView != null) {
      webView.setVisibility(View.VISIBLE);
    }
  }

  void stopLoading(MethodCall call, MethodChannel.Result result) {
    if (webView != null) {
      webView.stopLoading();
    }
  }

  boolean checkCameraPermissions(){
    int checkResult =
        ActivityCompat.checkSelfPermission(activity, permission.WRITE_EXTERNAL_STORAGE)
            | ActivityCompat.checkSelfPermission(activity, permission.CAMERA);
    Log.i(TAG, "checkCameraPermissions: 权限验证结果:" + checkResult);
    return checkResult == PackageManager.PERMISSION_GRANTED;
  }

  void requestCameraPermissions(){
    ActivityCompat.requestPermissions(activity, new String[]{
        permission.WRITE_EXTERNAL_STORAGE,
        permission.CAMERA
    }, CAMERA_REQUEST_CODE);
  }

  LocationClient initLocationClient() {
    // 声明LocationClient类
    LocationClient locationClient = new LocationClient(context.getApplicationContext());
    LocationClientOption option = new LocationClientOption();
    option.setLocationMode(LocationMode.Hight_Accuracy);
    option.setCoorType("bd09ll");
    // 可选，设置发起定位请求的间隔，int类型，单位ms
    // 如果设置为0，则代表单次定位，即仅定位一次，默认为0
    // 如果设置非0，需设置1000ms以上才有效
    option.setScanSpan(0);
    option.setOpenGps(true);
    // 可选，设置是否使用gps，默认false
    // 使用高精度和仅用设备两种定位模式的，参数必须设置为true
    option.setLocationNotify(true);
    // 可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
    option.setIgnoreKillProcess(false);
    // 可选，定位SDK内部是一个service，并放到了独立进程。
    // 设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)
    option.SetIgnoreCacheException(false);
    // 可选，设置是否收集Crash信息，默认收集，即参数为false
    option.setWifiCacheTimeOut(5 * 60 * 1000);
    // 可选，V7.2版本新增能力
    // 如果设置了该接口，首次启动定位时，会先判断当前Wi-Fi是否超出有效期，若超出有效期，会先重新扫描Wi-Fi，然后定位
    option.setEnableSimulateGps(false);
    // 可选，设置是否需要过滤GPS仿真结果，默认需要，即参数为false
    option.setIsNeedAddress(true);
    option.setIsNeedAltitude(false);
    // mLocationClient为第二步初始化过的LocationClient对象
    // 需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
    // 更多LocationClientOption的配置，请参照类参考中LocationClientOption类的详细说明
    locationClient.setLocOption(option);
    // 注册监听函数
    locationClient.registerLocationListener(locationListener);
    return locationClient;
  }

  BDAbstractLocationListener locationListener =
      new BDAbstractLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
          // 此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
          // 以下只列举部分获取经纬度相关（常用）的结果信息
          // 更多结果信息获取说明，请参照类参考中BDLocation类中的说明
          int errorCode = bdLocation.getLocType();
          // 获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
          if (errorCode == BDLocation.TypeGpsLocation
              || errorCode == BDLocation.TypeNetWorkLocation) {
            double latitude = bdLocation.getLatitude();
            double longitude = bdLocation.getLongitude();
            String adCode = bdLocation.getAdCode();
            Log.d(TAG, "onReceiveLocation: lat:"+latitude + ", lng:"+longitude + ", adCode:" + adCode);
            locationClient.stop();
            try{
              JSONObject params = new JSONObject();
              params.put("status", "01");
              params.put("lat", String.valueOf(latitude));
              params.put("lon", String.valueOf(longitude));
              params.put("adcode", adCode);
              JSONObject wrapper = new JSONObject();
              wrapper.put("data", params);
              Log.d(TAG, "回传位置信息：" + wrapper.toString());
              webView.loadUrl("javascript:setLoc(" +wrapper.toString() + ")");
            }catch (Exception e){
              Log.e(TAG, "回传位置失败", e);
            }
          }else{
            Log.d(TAG, "onReceiveLocation: 定位失败:"+errorCode);
            try{
              JSONObject params = new JSONObject();
              params.put("status", "02");
              JSONObject wrapper = new JSONObject();
              wrapper.put("data", params);
              Log.w(TAG, "回传位置信息：" + wrapper.toString());
              webView.loadUrl("javascript:setLoc(" +wrapper.toString() + ")");
            }catch (Exception e){
              Log.e(TAG, "回传位置失败", e);
            }
          }
        }
      };
}
