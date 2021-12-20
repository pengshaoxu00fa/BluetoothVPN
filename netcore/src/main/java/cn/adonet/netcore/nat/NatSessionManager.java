package cn.adonet.netcore.nat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.util.DebugLog;

/**
 * Created by zengzheying on 15/12/29.
 * NAT管理对象
 */
public class NatSessionManager {

	private static final int MAX_SESSION_COUNT = 64; //会话保存的最大个数
	private static final long SESSION_TIME_OUT_MS = 2 * 60 * 1000;
	private static final LinkedHashMap<Short, NatSession> mSessions = new LinkedHashMap<>();
	private static final List<Short> clearKeys = new ArrayList<>();


	/**
	 * 通过本地端口获取会话信息
	 *
	 * @param portKey 本地端口
	 * @return 会话信息
	 */
	public synchronized static NatSession getSession(short portKey) {
		return mSessions.get(portKey);
	}

	public synchronized static void clearSession() {
		if (mSessions != null) {
			mSessions.clear();
		}
	}

	/**
	 * 获取会话个数
	 *
	 * @return 会话个数
	 */
	public synchronized static int getSessionCount() {
		return mSessions.size();
	}

	/**
	 * 清除过期的会话
	 */
	private  synchronized static void clearExpiredSessions() {
		clearKeys.clear();
		long now = System.currentTimeMillis();
		int count = 0;
		for (Map.Entry<Short, NatSession> entry : mSessions.entrySet()) {
			NatSession session = entry.getValue();
			if (now - session.lastActivityTime > SESSION_TIME_OUT_MS) {
				clearKeys.add(entry.getKey());
				count ++;
			}
		}
		for (Short key : clearKeys) {
			NatSession session = mSessions.get(key);
//			if (session != null && session.tunnel != null) {
//				session.tunnel.dispose();
//			}
			mSessions.remove(key);

		}
		DebugLog.d("all session count:%s", getSessionCount());
		if (count > 0) {
			DebugLog.d("clear session：" + count);
		}
	}

	/**
	 * 创建会话
	 *
	 * @param portKey    源端口
	 * @param remoteIP   远程ip
	 * @param remotePort 远程端口
	 * @return NatSession对象
	 */
	public synchronized static NatSession createSession(short portKey, int remoteIP, short remotePort) {
		if (mSessions.size() > MAX_SESSION_COUNT) {
			clearExpiredSessions(); //清除过期的会话
		}

		NatSession session = new NatSession();
		session.key = portKey;
		session.lastNanoTime = System.nanoTime();
		session.remoteIP = remoteIP;
		session.remotePort = remotePort;
		session.lastActivityTime = System.currentTimeMillis();

		mSessions.put(portKey, session);
		return session;
	}


	public synchronized static void removeSession(NatSession session) {
		DebugLog.d("remove session and cur session count is %s", mSessions.size());
		if (session != null) {
			mSessions.remove(session.key);
		}
	}
}
