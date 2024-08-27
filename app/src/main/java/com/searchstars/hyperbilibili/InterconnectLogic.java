package com.searchstars.hyperbilibili;

import android.util.Log;
import com.searchstars.hyperbilibili.InterconnectStructures.*;
import com.google.gson.Gson;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class InterconnectLogic {
    private static final Gson gson = new Gson();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final BlockingQueue<MessageTask> messageQueue = new LinkedBlockingQueue<>();
    private static final long MESSAGE_DELAY_MS = 500; // 间隔500毫秒
    private static final String defaultResponse = "{\"content\": \"OK\"}";

    static {
        // 初始化时开始调度任务，每隔500毫秒处理一个消息
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 从队列中获取消息并发送，如果队列为空则不会发送
                MessageTask task = messageQueue.poll();
                if (task != null) {
                    sendMessageNow(task.nodeId, task.result);
                }
            } catch (Exception e) {
                MainActivity.logs.add("发送消息时出错: " + e.getMessage());
            }
        }, 0, MESSAGE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static void ProcessMessage(String nodeId, String data) {
        MainActivity.logs.add("收到消息：" + data);
        Log.d("RecvMsg: ", data);

        InterconnectPacketIn packet = gson.fromJson(data, InterconnectPacketIn.class);
        InterconnectMessage message = gson.fromJson(packet.message, InterconnectMessage.class);

        InterconnectPacketOut result = new InterconnectPacketOut();
        result.id = packet.id;

        switch (message.msgtype) {
            case "FETCH":
                Fetch(gson.fromJson(message.message, InterconnectFetchRequest.class)).thenAccept(response -> {
                    result.response = gson.toJson(response);
                    MainActivity.logs.add("Response: " + result.response);
                    enqueueMessage(nodeId, result); // 将消息加入队列
                }).exceptionally(e -> {
                    MainActivity.logs.add("Fetch failed: " + e.getMessage());
                    return null;
                });
                break;
            case "SHOWQR":
                result.response = defaultResponse;
                MainActivity.current_params.qrcode_key = message.message;
                enqueueMessage(nodeId, result);
                break;
            case "HELLO":
                result.response = defaultResponse;
                MainActivity.logs.add("Hello Packet已收到，Let's Go！");
                enqueueMessage(nodeId, result); // 将消息加入队列
                break;
        }
    }

    private static CompletableFuture<InterconnectFetchResponse> Fetch(InterconnectFetchRequest params) {
        MainActivity.logs.add("Fetch " + params.url);

        MainActivity.logs.add("准备创建future");
        CompletableFuture<InterconnectFetchResponse> future = new CompletableFuture<>();

        MainActivity.logs.add("创建okhttp客户端");
        OkHttpClient client = new OkHttpClient();
        MainActivity.logs.add("准备build request");
        Request request = buildRequest(params);
        MainActivity.logs.add("开始call okhttp");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                MainActivity.logs.add("okhttp failure: " + e.toString());
                e.printStackTrace();
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                MainActivity.logs.add("okhttp success");
                InterconnectFetchResponse result = new InterconnectFetchResponse();
                result.code = response.code();
                result.data = response.body() != null ? response.body().string() : null;
                result.headers = headersToMap(response.headers());
                Log.d("Headers", gson.toJson(result.headers));
                MainActivity.logs.add("Fetch结果：" + gson.toJson(result));
                future.complete(result);
            }
        });

        return future;
    }

    private static void enqueueMessage(String nodeId, InterconnectPacketOut result) {
        // 将消息加入队列
        messageQueue.offer(new MessageTask(nodeId, result));
    }

    private static void sendMessageNow(String nodeId, InterconnectPacketOut result) {
        MainActivity.logs.add("消息送出：" + gson.toJson(result));
        Log.d("SendMsg: ", gson.toJson(result));
        Log.d("SendMsg Size: ", String.valueOf(gson.toJson(result).length()));
        MainActivity.messageApi.sendMessage(nodeId, gson.toJson(result).getBytes());
    }

    private static Map<String, List<String>> headersToMap(Headers headers) {
        Map<String, List<String>> headersMap = new HashMap<>();

        // 遍历 Headers 的所有名称，并将它们放入 Map 中
        for (String name : headers.names()) {
            // 如果 header 名称是 "set-cookie"，则替换为 "Set-Cookie"
            String adjustedName = "set-cookie".equalsIgnoreCase(name) ? "Set-Cookie" : name;

            // 获取当前 header 名称的所有值，如果存在，添加到现有的列表中，否则创建一个新的列表
            List<String> values = headersMap.getOrDefault(adjustedName, new ArrayList<>());
            // 使用 headers.values(name) 替代 headers.get(name) 来获取所有值
            values.addAll(headers.values(name));
            headersMap.put(adjustedName, values);


            Log.d("HeadersCheck", name + "=" + gson.toJson(values));
        }

        Log.d("HeadersMap", gson.toJson(headersMap));

        return headersMap;
    }

    private static Request buildRequest(InterconnectFetchRequest params) {
        if (params.method == null) {
            params.method = "GET";
        }

        MainActivity.logs.add("[buildRequest] params=" + gson.toJson(params));
        MainActivity.logs.add("[buildRequest] METHOD=" + params.method);

        // 创建 Request.Builder 并设置 url 和 headers
        Request.Builder requestBuilder = new Request.Builder()
                .url(params.url)
                .headers(Headers.of(params.header));

        MainActivity.logs.add("基本rb已构建");

        // 根据 HTTP 方法设置请求方法和请求体
        if (params.method.equalsIgnoreCase("POST")) {
            MainActivity.logs.add("创建POST请求体");
            // 创建请求体
            RequestBody body = RequestBody.create(
                    params.data.getBytes(StandardCharsets.UTF_8)
            );
            requestBuilder.method(params.method, body);
        } else if (params.method.equalsIgnoreCase("GET")) {
            MainActivity.logs.add("创建GET请求体");
            requestBuilder.method(params.method, null); // GET 方法不需要请求体
        }

        MainActivity.logs.add("[buildRequest] 完成");

        // 构建并返回 Request 对象
        return requestBuilder.build();
    }

    // 内部类用于存储待发送的消息任务
    private static class MessageTask {
        String nodeId;
        InterconnectPacketOut result;

        MessageTask(String nodeId, InterconnectPacketOut result) {
            this.nodeId = nodeId;
            this.result = result;
        }
    }
}