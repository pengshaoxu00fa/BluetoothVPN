package cn.adonet.netcore.protocol;
import android.net.VpnService;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import cn.adonet.netcore.nat.UDPMap;
import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.util.LRU;
import cn.adonet.netcore.util.MainCore;

/**
 * Created by zengzheying on 15/12/29.
 * DNS代理
 */
public class UDPProxyServer implements Runnable{

	public volatile boolean mStopped;
	private Selector mSelector;
	private DatagramChannel localChannel;
	private LRU<Short, DatagramChannel> remoteChannels = new LRU<>(16, 0.8f, true, 256);
	private HashMap<DatagramChannel, Short> localPorts = new HashMap<>(16);
	public ReentrantLock lock = new ReentrantLock(true);

	private Thread         mWorkThread;


	public ByteBuffer mByteBuffer = ByteBuffer.allocate(65536);

	private short mPort;

	public short getPort() {
		return mPort;
	}
	public boolean isStopped() {
		return mStopped;
	}

	public UDPProxyServer() throws IOException {
		localChannel = DatagramChannel.open();
		localChannel.configureBlocking(false);
		DatagramSocket ds = localChannel.socket();
		ds.setReceiveBufferSize(65536);
		ds.bind(new InetSocketAddress(0));
		mSelector = Selector.open();
		localChannel.register(mSelector, SelectionKey.OP_READ, localChannel);
		mPort = (short) localChannel.socket().getLocalPort();
		remoteChannels.setRemoveListener(listener);
		UDPMap._FROM.clear();
		UDPMap._TO.clear();
	}


	public void run() {
		try {
			while (!mStopped) {
				mSelector.select();
				if (mStopped) {
					break;
				}
				lock.lock();
				if (mSelector != null) {
					Set<SelectionKey> keys = mSelector.selectedKeys();
					if (keys != null) {
						Iterator it = keys.iterator();
						while (it.hasNext() && !mStopped) {
							SelectionKey key = ( SelectionKey )it.next() ;
							if (key.isValid()) {
								DatagramChannel channel = (DatagramChannel) key.channel();
								if (key.isReadable() && !mStopped) {
									read(channel);
								}
							}
							it.remove();
						}
					}
				}
				lock.unlock();

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		stop();

	}

	public void read(DatagramChannel channel) {
		if (channel == null) {
			return;
		}
		try {
			if (channel == localChannel) {
				mByteBuffer.clear();
				SocketAddress originAddress = localChannel.receive(mByteBuffer);
				mByteBuffer.flip();
				//建一个远程remote；
				if (originAddress != null && originAddress instanceof InetSocketAddress) {
					InetSocketAddress receiveAddress = (InetSocketAddress)originAddress;
					short localPort = (short) receiveAddress.getPort();
					DatagramChannel remoteChannel = remoteChannels.get(localPort);
					if (remoteChannel == null ||
							!remoteChannel.isOpen() ||
							remoteChannel.socket() == null ||
							remoteChannel.socket().isClosed()) {
						remoteChannel = DatagramChannel.open();
						remoteChannel.configureBlocking(false);
						protect(remoteChannel.socket());
						remoteChannels.put(localPort, remoteChannel);
						localPorts.put(remoteChannel, localPort);
						remoteChannel.register(mSelector, SelectionKey.OP_READ, remoteChannel);
					}
					UDPMap.Address addressInfo = UDPMap._TO.find(localPort);
					if (addressInfo != null) {
						InetSocketAddress address = new InetSocketAddress(CommonMethods.ipIntToString(addressInfo.ip), addressInfo.port);
						remoteChannel.send(mByteBuffer, address);
					}
				}

			} else {
				short localPort = localPorts.get(channel);
				channel.configureBlocking(false);
				mByteBuffer.clear();
				InetSocketAddress socketAddress = (InetSocketAddress) channel.receive(mByteBuffer);
				Inet4Address address = (Inet4Address)socketAddress.getAddress();
				mByteBuffer.flip();
				UDPMap._FROM.map(localPort,CommonMethods.ipStringToInt(address.getHostAddress()), (short)(socketAddress.getPort()));
				localChannel.send(mByteBuffer, new InetSocketAddress(address.getHostAddress(), localPort & 0xFFFF));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}


	}


	private void protect(DatagramSocket socket) {
		VpnService service = MainCore.getInstance().getVpnService();
		if (service != null) {
			service.protect(socket);
		}
	}

	/**
	 * 启动线程
	 */
	public void start() {
		mWorkThread = new Thread(this, "UDPWorkThread");
		mWorkThread.start();
	}


	/**
	 * 停止线程
	 */
	public void stop() {
		mStopped = true;
		mPort = 0;
		lock.lock();
		if (mSelector != null) {
			try {
				mSelector.close();
			} catch (Exception e){
			}
			try {
				localChannel.close();
			} catch (Exception e) {
			}
		}
		lock.unlock();
		mWorkThread.interrupt();
		clear();
		UDPMap._FROM.clear();
		UDPMap._TO.clear();
	}


	public synchronized void clear(){
		Iterator<Map.Entry<Short, DatagramChannel>> entries = remoteChannels.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Short, DatagramChannel> entity = entries.next();
			DatagramChannel channel= entity.getValue();
			try {
				channel.close();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				entries.remove();
			}
		}
		localPorts.clear();
	}

	private LRU.RemoveListener<Short, DatagramChannel> listener = new LRU.RemoveListener<Short, DatagramChannel>() {
		@Override
		public void onRemove(Map.Entry<Short, DatagramChannel> entry) {
			if (entry == null) {
				return;
			}
			DatagramChannel channel= entry.getValue();
			try {
				channel.close();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
			}
			UDPMap._TO.remove(entry.getKey());
			UDPMap._FROM.remove(entry.getKey());
			localPorts.remove(channel);
		}
	};

}
