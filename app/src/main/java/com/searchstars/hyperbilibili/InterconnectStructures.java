package com.searchstars.hyperbilibili;

import java.util.List;
import java.util.Map;

public class InterconnectStructures {
    public static class InterconnectPacketIn {
        public String id;
        public String message;
    }

    public static class InterconnectPacketOut {
        public String id;
        public String response;
    }

    public static class InterconnectMessage {
        public String msgtype;
        public String message;
    }

    public static class InterconnectFetchRequest {
        public String url;
        public String data; //仅适用于Hyperbili，因为Hyperbili的fetch经过规范化使用处理，data只允许传递String
        public Map<String, String> header;
        public String method;
        public String responseType; //没啥用
    }

    public static class InterconnectFetchResponse {
        public int code;
        public String data; // 规范化处理，仅能返回string，会在js端进行统一parse
        public Map<String, List<String>> headers;
    }
}