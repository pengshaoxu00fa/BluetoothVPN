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
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import cn.adonet.netcore.nat.UDPMap;
import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.tunel.base.ChannelBinder;
import cn.adonet.netcore.tunel.base.IProxyChannel;
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
	private LRU<Short, IProxyChannel> remoteChannels = new LRU<>(16, 0.8f, true, 256);
	private HashMap<IProxyChannel, Short> localPorts = new HashMap<>(16);
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
			mByteBuffer.clear();
			SocketAddress originAddress = localChannel.receive(mByteBuffer);
			mByteBuffer.flip();
			//建一个远程remote；
			if (originAddress != null && originAddress instanceof InetSocketAddress) {
				InetSocketAddress receiveAddress = (InetSocketAddress)originAddress;
				short localPort = (short) receiveAddress.getPort();
				IProxyChannel remoteChannel = remoteChannels.get(localPort);
				if (remoteChannel == null ||
						!remoteChannel.isOpen()) {
					UDPMap.Address addressInfo = UDPMap._TO.find(localPort);
					ChannelBinder<DatagramChannel> binder = new ChannelBinder<DatagramChannel>() {
						@Override
						public void onProxyChannelRead(Object obj) {
							//代理通道读取到数据后
							//组合数据，通过localChannel发送回去
//							short localPort = localPorts.get(channel);
////							channel.configureBlocking(false);
//							mByteBuffer.clear();
//							InetSocketAddress socketAddress = (InetSocketAddress) channel.receive(mByteBuffer);
//							Inet4Address address = (Inet4Address)socketAddress.getAddress();
//							mByteBuffer.flip();
//							UDPMap._FROM.map(localPort,CommonMethods.ipStringToInt(address.getHostAddress()), (short)(socketAddress.getPort()));
//							try {
//								localChannel.send(mByteBuffer, new InetSocketAddress(address.getHostAddress(), localPort & 0xFFFF));
//							} catch (Exception e){
//							}

						}

						@Override
						public void onProxyChannelReady() {

						}
					};
					binder.localChannel = localChannel;
					binder.proxyChannel = null;
					remoteChannels.put(localPort, remoteChannel);
					localPorts.put(remoteChannel, localPort);
				}
				remoteChannel.write(mByteBuffer);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		Iterator<Map.Entry<Short, IProxyChannel>> entries = remoteChannels.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Short, IProxyChannel> entity = entries.next();
			IProxyChannel channel= entity.getValue();
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
