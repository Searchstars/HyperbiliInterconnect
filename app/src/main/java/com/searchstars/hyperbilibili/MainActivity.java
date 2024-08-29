package com.searchstars.hyperbilibili;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.annotation.Nullable;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
import com.xiaomi.xms.wearable.tasks.OnFailureListener;
import com.xiaomi.xms.wearable.tasks.OnSuccessListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private WebView main_webview = null;
    public static UIParams current_params = new UIParams();
    public static List<String> logs = new ArrayList<>(Arrays.asList("Hyperbili Interconnect Tool V1", "https://github.com/searchstars/hyperbilibili_interconnect"));

    public static NodeApi nodeApi = null;
    public static AuthApi authApi = null;
    public static MessageApi messageApi = null;

    public static String connectedNodeId = "";

    @Override
    protected void onDestroy(){
        messageApi.removeListener(connectedNodeId);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main_webview = findViewById(R.id.main_webview);

        WebSettings webSettings = main_webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setDomStorageEnabled(true);

        // 设置自定义 WebViewClient
        main_webview.setWebViewClient(new LocalContentWebViewClient());
        WebView.setWebContentsDebuggingEnabled(true);

        // 引入JS交互逻辑
        main_webview.addJavascriptInterface(new JSKit(), "androidlib");

        // 加载本地HTML文件
        main_webview.loadUrl("file:///android_asset/index.html");

        nodeApi = Wearable.getNodeApi(this.getApplicationContext());
        authApi = Wearable.getAuthApi(this.getApplicationContext());
        messageApi = Wearable.getMessageApi(this.getApplicationContext());
        nodeApi.getConnectedNodes().addOnSuccessListener(new OnSuccessListener<List<Node>>() {
            @Override
            public void onSuccess(List<Node> nodes) {
                logs.add("Node Count: " + nodes.size());
                if (!nodes.isEmpty()){
                    current_params.connected = true;
                    current_params.connected_device_name = nodes.get(0).name;
                    logs.add("Connected to device: " + nodes.get(0).name);
                    authApi.checkPermission(nodes.get(0).id, Permission.DEVICE_MANAGER).addOnSuccessListener(new OnSuccessListener<Boolean>() {
                        @Override
                        public void onSuccess(Boolean aBoolean) {
                            current_params.mifitness_connected = true;
                            connectedNodeId = nodes.get(0).id;
                            logs.add("checkPermission: Permission.DEVICE_MANAGER状态为" + aBoolean.toString());

                            authApi.requestPermission(connectedNodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY).addOnSuccessListener(new OnSuccessListener<Permission[]>() {
                                @Override
                                public void onSuccess(Permission[] permissions) {
                                    current_params.device_permission = true;
                                    logs.add("权限 Permission.DEVICE_MANAGER 申请成功");
                                    logs.add("所有准备工作已完成！开始等待Hello Packet...");

                                    OnMessageReceivedListener onMessageReceivedListener = new OnMessageReceivedListener() {
                                        @Override
                                        public void onMessageReceived(@NonNull @NotNull String nodeId, @NonNull @NotNull byte[] bytes) {
                                            logs.add("收到长度为" + bytes.length+ "的消息，准备处理");
                                            InterconnectLogic.ProcessMessage(nodeId, new String(bytes));
                                        }
                                    };
                                    messageApi.addListener(connectedNodeId, onMessageReceivedListener).addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void unused) {
                                            logs.add("开始监听消息！");
                                        }
                                    }).addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull @NotNull Exception e) {
                                            logs.add("监听消息失败！错误信息：" + e.toString());
                                        }
                                    });
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull @NotNull Exception e) {
                                    logs.add("设备权限申请失败，错误信息：" + e.toString());
                                }
                            });
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull @NotNull Exception e) {
                            logs.add("在检查设备权限时出现错误：" + e.toString());
                        }
                    });
                }
            }
        });
    }

    private class LocalContentWebViewClient extends WebViewClient {

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // 统一处理所有 file:// 开头的请求
            if (url.startsWith("file:///")) {
                String assetPath = url.replace("file:///","");

                try {
                    InputStream inputStream = getAssets().open(assetPath);
                    String mimeType = getMimeType(assetPath);
                    return new WebResourceResponse(mimeType, "UTF-8", inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                    // 当资源未找到时返回null
                    return null;
                }
            }

            // 默认行为
            return super.shouldInterceptRequest(view, request);
        }

        private String getMimeType(String url) {
            if (url.endsWith(".html")) {
                return "text/html";
            } else if (url.endsWith(".js")) {
                return "application/javascript";
            } else if (url.endsWith(".css")) {
                return "text/css";
            } else if (url.endsWith(".png")) {
                return "image/png";
            } else if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (url.endsWith(".gif")) {
                return "image/gif";
            } else if (url.endsWith(".svg")) {
                return "image/svg+xml";
            } else if (url.endsWith(".woff")) {
                return "font/woff";
            } else if (url.endsWith(".woff2")) {
                return "font/woff2";
            } else if (url.endsWith(".ttf")) {
                return "font/ttf";
            } else {
                return "text/plain";
            }
        }
    }
}