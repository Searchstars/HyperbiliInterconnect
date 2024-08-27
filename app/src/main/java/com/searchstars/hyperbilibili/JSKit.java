package com.searchstars.hyperbilibili;

import android.webkit.JavascriptInterface;
import com.google.gson.Gson;

public class JSKit {
    public Gson gson = new Gson();

    @JavascriptInterface
    public String GetUIParams(){
        String result = gson.toJson(MainActivity.current_params);
        //MainActivity.logs.add("[JavaScriptInterface] GetUIParams Return: " + result);
        return result;
    }

    @JavascriptInterface
    public String GetLogs(){
        String result = gson.toJson(MainActivity.logs);
        //MainActivity.logs.add("[JavaScriptInterface] GetLogs Return: " + result);
        return result;
    }

    @JavascriptInterface
    public void ClearQRKey(){
        MainActivity.current_params.qrcode_key = "";
    }
}
