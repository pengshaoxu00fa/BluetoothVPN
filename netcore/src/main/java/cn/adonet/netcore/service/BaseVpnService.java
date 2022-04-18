package cn.adonet.netcore.service;

import android.app.Notification;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.adonet.netcore.nat.NatSession;
import cn.adonet.netcore.nat.NatSessionManager;
import cn.adonet.netcore.nat.UDPMap;
import cn.adonet.netcore.protocol.TCPProxyServer;
import cn.adonet.netcore.protocol.UDPProxyServer;
import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.tcpip.IPHeader;
import cn.adonet.netcore.tcpip.TCPHeader;
import cn.adonet.netcore.tcpip.UDPHeader;
import cn.adonet.netcore.util.DebugLog;
import cn.adonet.netcore.util.MainCore;

public abstract class BaseVpnService extends android.net.VpnService implements Runnable {

	private static int mLocalIP;
	private static volatile boolean isRunning = false;
	private Thread mVPNThread;
	private ParcelFileDescriptor mVPNInterface;
	private TCPProxyServer mTCPProxyServer;
	private UDPProxyServer mUDPProxyServer;
	private FileOutputStream mVPNOutputStream;
	private FileInputStream mVPNInputStream;

	private byte[] mPacket;
	private IPHeader mIPHeader;
	private TCPHeader mTCPHeader;
	private UDPHeader mUDPHeader;
	private ByteBuffer mDNSBuffer;
	private Handler mMainHandler;
	private final int MAX_TRY_COUNT = 30;
	private int mTryCount;
	private final int BYTE_SIZE_KB = 1024;
	private final int BYTE_SIZE_MB = BYTE_SIZE_KB * 1024;
	private final int BYTE_SIZE_GB = BYTE_SIZE_MB * 1024;


	private NotificationCompat.Builder builder;

	public BaseVpnService() {
		mMainHandler = new Handler(Looper.getMainLooper());
		mPacket = new byte[65536];
		mIPHeader = new IPHeader(mPacket, 0);
		mTCPHeader = new TCPHeader(mPacket, 20);
		mUDPHeader = new UDPHeader(mPacket, 20);
		mDNSBuffer = ((ByteBuffer) ByteBuffer.wrap(mPacket).position(28)).slice();

	}



	//返回vpn网址
	protected abstract int onBuildEstablish(Builder builder);
	protected abstract Notification onCreateNotification();
	protected abstract int getNotificationID();

	//启动Vpn工作线程
	@Override
	public void onCreate() {
		mVPNThread = new Thread(this, "VPNServiceThread");
		mVPNThread.start();
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);

	}

	private void startFrontServer() {
		try {
			Notification notification = onCreateNotification();
			startForeground(getNotificationID(), notification);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private void stopFrontServer() {
		try {
			stopForeground(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	//停止Vpn工作线程
	@Override
	public void onDestroy() {
		if (mVPNThread != null) {
			mVPNThread.interrupt();
		}
		stopFrontServer();
		stop();
		super.onDestroy();
	}


	//建立VPN，同时监听出口流量
	private void runVPN() throws Exception {
		this.mVPNInterface = establishVPN();
		this.mVPNOutputStream = new FileOutputStream(mVPNInterface.getFileDescriptor());
		this.mVPNInputStream = new FileInputStream(mVPNInterface.getFileDescriptor());
		int size = 0;
		while (size != -1 && isRunning) {
			while ((size = mVPNInputStream.read(mPacket)) > 0 && isRunning) {
				if (mTCPProxyServer.isStopped()) {
					while (mTCPProxyServer.isStopped() && isRunning) {
						startTCPProxy();
						try {
							Thread.sleep(500);
						} catch (Exception e) {
						}
					}
				} else if (mUDPProxyServer.isStopped()) {
					while (mUDPProxyServer.isStopped() && isRunning) {
						startUDPProxy();
						try {
							Thread.sleep(500);
						} catch (Exception e) {
						}
					}
				} else {
					mTryCount = 0;
					onIPPacketReceived(mIPHeader, size);
				}
			}
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
	}



	void onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
		switch (ipHeader.getProtocol()) {
			case IPHeader.TCP:
				TCPHeader tcpHeader = mTCPHeader;
				tcpHeader.mOffset = ipHeader.getHeaderLength(); //矫正TCPHeader里的偏移量，使它指向真正的TCP数据地址
				if (tcpHeader.getSourcePort() == mTCPProxyServer.getPort()) {
					NatSession session = NatSessionManager.getSession((short) tcpHeader.getDestinationPort());
					if (session != null) {
						ipHeader.setSourceIP(ipHeader.getDestinationIP());
						tcpHeader.setSourcePort(session.remotePort);
						ipHeader.setDestinationIP(mLocalIP);

						CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
						mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
					} else {
						//某程序主动发送过来的包
						DebugLog.i("NoSession: %s %s\n", ipHeader.toString(), tcpHeader.toString());
					}
				} else {
					//添加端口映射
					short portKey = tcpHeader.getSourcePort();
					NatSession session = NatSessionManager.getSession(portKey);
					if (session == null ||
							session.remoteIP != ipHeader.getDestinationIP() ||
							session.remotePort != tcpHeader.getDestinationPort()) {
						session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader
								.getDestinationPort());
					}
					session.lastNanoTime = System.nanoTime();
					session.packetSent++; //注意顺序

					int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
					//转发给本地TCP服务器
					ipHeader.setSourceIP(ipHeader.getDestinationIP());
					ipHeader.setDestinationIP(mLocalIP);
					tcpHeader.setDestinationPort(mTCPProxyServer.getPort());

					CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
					mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
					session.bytesSent += tcpDataSize; //注意顺序
				}
				break;
			case IPHeader.UDP:
				UDPHeader udpHeader = mUDPHeader;
				udpHeader.mOffset = ipHeader.getHeaderLength();
				if (udpHeader.getSourcePort() == mUDPProxyServer.getPort()) {
					UDPMap.Address address = UDPMap._FROM.find(udpHeader.getDestinationPort());
					if (address != null) {
						ipHeader.setSourceIP(address.ip);
						udpHeader.setSourcePort(address.port);
						ipHeader.setDestinationIP(mLocalIP);
						CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
						mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
					} else {
						//没有请求的消息，广播过来的消息
						DebugLog.i("无主udp消息 %s %s\n", ipHeader.toString(), udpHeader.toString());
					}

				} else {
					//转发给本地的代理UDP服务器
					DebugLog.i("收到UDP信息 %s %s\n", ipHeader.toString(), udpHeader.toString());
					UDPMap._TO.map(udpHeader.getSourcePort(),  ipHeader.getDestinationIP(), udpHeader.getDestinationPort());
					ipHeader.setSourceIP(ipHeader.getDestinationIP());
					ipHeader.setDestinationIP(mLocalIP);
					udpHeader.setDestinationPort(mUDPProxyServer.getPort());
					CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
					mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
				}
				break;
		}

	}

	private void waitUntilPrepared() {
		while (prepare(this) != null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				if (DebugLog.IS_DEBUG) {
					e.printStackTrace();
				}
				DebugLog.e("waitUntilPrepared catch an exception %s\n", e);
			}
		}
	}

	private ParcelFileDescriptor establishVPN(){
		Builder builder = new Builder();
		mLocalIP = onBuildEstablish(builder);
		ParcelFileDescriptor pfdDescriptor = builder.establish();
		return pfdDescriptor;
	}

	@Override
	public void run() {
		isRunning = true;
		waitUntilPrepared();
		//启动TCP代理服务
		while (isRunning && mTryCount <= MAX_TRY_COUNT) {
			mMainHandler.post(new Runnable() {
				@Override
				public void run() {
					startFrontServer();
				}
			});
			startUDPProxy();
			startTCPProxy();
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			processVPN();
			mMainHandler.post(new Runnable() {
				@Override
				public void run() {
					stopFrontServer();
				}
			});
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		dispose();


	}

	private void startTCPProxy() {
		if (mTCPProxyServer != null) {
			mTCPProxyServer.stop();
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}

		try {
			mTCPProxyServer = new TCPProxyServer(0);
		} catch (Exception e) {
			mTCPProxyServer = null;
			if (DebugLog.IS_DEBUG) {
				e.printStackTrace();
			}
			mTryCount ++;
		}
		if (mTCPProxyServer != null) {
			mTCPProxyServer.start();
		}
	}

	private void startUDPProxy() {
		if (mUDPProxyServer != null) {
			mUDPProxyServer.stop();
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}

		try {
			mUDPProxyServer = new UDPProxyServer();
		} catch (Exception e) {
			mUDPProxyServer = null;
			if (DebugLog.IS_DEBUG) {
				e.printStackTrace();
			}
			mTryCount ++;
		}
		if (mUDPProxyServer != null) {
			mUDPProxyServer.start();
		}
	}
	private void processVPN() {
		try {
			runVPN();
		} catch (Exception e) {
			if (DebugLog.IS_DEBUG) {
				e.printStackTrace();
			}
			mTryCount ++;
		} finally {
			disconnectVPN();
		}
	}

	public void disconnectVPN() {
		try {
			if (mVPNInputStream != null) {
				mVPNInputStream.close();
				mVPNInputStream = null;
			}
		} catch (Exception e){
		}

		try {
			if (mVPNOutputStream != null) {
				mVPNOutputStream.close();
				mVPNOutputStream = null;
			}
		} catch (Exception e){
		}
		try {
			if (mVPNInterface != null) {
				mVPNInterface.close();
				mVPNInterface = null;
			}
		} catch (Exception e) {
		}
	}

	private synchronized void dispose() {
		//断开VPN
		disconnectVPN();
		//停止TCP代理服务
		if (mTCPProxyServer != null) {
			mTCPProxyServer.stop();
			mTCPProxyServer = null;
			DebugLog.i("TcpProxyServer stopped.\n");
		}

		//停止UDP代理服务
		if (mUDPProxyServer != null) {
			mUDPProxyServer.stop();
			mUDPProxyServer = null;
			DebugLog.i("UdpProxyServer stopped.\n");
		}
		NatSessionManager.clearSession();
		stopSelf();
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void stop() {
		isRunning = false;
		MainCore.getInstance().setInetSocketAddress(null);
	}
}
