package cn.adonet.netcore.nat;

import android.text.TextUtils;

import java.io.Serializable;

import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.tunel.Tunnel;

public class NatSession implements Serializable {
	public static final int SESSION_TYPE_HTTP  = 1;
	public static final int SESSION_TYPE_HTTPS = 2;
	public static final int SESSION_TYPE_TCP = 3;
	public short key;
	public int remoteIP;
	public short remotePort;
	public String remoteHost;
	public int bytesSent;
	public int packetSent;
	public long lastNanoTime;
	public int sessionType; // 1 http; 2 https; 3 other
	public Long lastActivityTime;//最后活动时间
	public boolean isFinishResponse = false;

	public HttpSession httpSession;

	public String getHost() {
		if (!TextUtils.isEmpty(remoteHost)) {
			return remoteHost;
		} else {
			return new StringBuffer()
					.append(CommonMethods.ipIntToString(remoteIP))
					.append(":")
					.append(remotePort & 0xFFFF)
					.toString();
		}
	}

	@Override
	public String toString() {
		return String.format("%s/%s:%d packet: %d",
				remoteHost,
				CommonMethods.ipIntToString(remoteIP),
				remotePort & 0xFFFF, packetSent);
	}
}
