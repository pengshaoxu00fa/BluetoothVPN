package cn.adonet.netcore.nat;


import java.io.Serializable;
import java.util.LinkedHashMap;

public class HttpSession implements Serializable {
    public NatSession natSession;
    public StringBuilder requestHeaderBuilder = new StringBuilder();
    public StringBuilder responseHeaderBuilder = new StringBuilder();
    private String requestHeaderFlag;
    private String[] requestHeaderFlags;

    public boolean isParseRequestHeaderFinish = false;
    public boolean isParseResponseHeaderFinish = false;

    private LinkedHashMap<String, String> requestHeaderMap;



    private void parseRequestHeader() {
        String[] headers = requestHeaderBuilder.toString().split("\r\n");
        if (headers.length > 0) {
            requestHeaderFlag = headers[0];
            requestHeaderFlags = headers[0].split(" ");
            requestHeaderMap = new LinkedHashMap<>();
            for (int i = 1; i < headers.length; i++) {
                if (headers[i].length() > 0 && headers[i].indexOf(": ") != -1) {
                    String[] entity = headers[i].split(": ");
                    requestHeaderMap.put(entity[0], entity.length > 1 ? entity[1] : "");
                }
            }
        }
    }

    public String getAction() {
        if (requestHeaderFlags == null) {
            parseRequestHeader();
        }
        if (requestHeaderFlags != null && requestHeaderFlags.length >= 1) {
            return requestHeaderFlags[0];
        } else {
            return "";
        }
    }

    public String getHttpRoute() {
        if (requestHeaderFlags == null) {
            parseRequestHeader();
        }
        if (requestHeaderFlags != null && requestHeaderFlags.length >= 2) {
            return requestHeaderFlags[1];
        } else {
            return "";
        }
    }

    public String getHttpVersion() {
        if (requestHeaderFlags == null) {
            parseRequestHeader();
        }
        if (requestHeaderFlags != null && requestHeaderFlags.length >= 3) {
            return requestHeaderFlags[2];
        } else {
            return "";
        }
    }

    public String getHttpFlag() {
        if (requestHeaderFlag == null) {
            parseRequestHeader();
        }
        return requestHeaderFlag;
    }



    public LinkedHashMap<String,String> getHeaderMap() {
        if (requestHeaderMap == null) {
            parseRequestHeader();
        }
        return requestHeaderMap;
    }


    @Override
    public String toString() {
        return requestHeaderBuilder.toString() + responseHeaderBuilder.toString();
    }
}
