package cn.adonet.netcore;


import android.text.TextUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;


/**
 * Created by zengzheying on 15/12/28.
 */
public class ProxyConfig {

	private static final ProxyConfig instance = new ProxyConfig();

	private int MTU;

	public static ProxyConfig getInstance() {
		return instance;
	}


	public void setMtu(int mtu) {
		this.MTU = mtu;
	}

	public int getMTU() {
		if (MTU > 1400 && MTU <= 20000) {
			return MTU;
		} else {
			return 20000;
		}
	}






	public IPAddress getDefaultLocalIP() {
		return new IPAddress("10.8.0.2", 32);
	}


	public static class IPAddress {
		public final String Address;
		public final int PrefixLength;

		public IPAddress(String address, int prefixLength) {
			Address = address;
			PrefixLength = prefixLength;
		}

		public IPAddress(String ipAddressString) {
			String[] arrStrings = ipAddressString.split("/");
			String address = arrStrings[0];
			int prefixLength = 32;
			if (arrStrings.length > 1) {
				prefixLength = Integer.parseInt(arrStrings[1]);
			}

			this.Address = address;
			this.PrefixLength = prefixLength;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof IPAddress)) {
				return false;
			} else {
				return this.toString().equals(o.toString());
			}
		}

		@Override
		public String toString() {
			return String.format("%s/%d", Address, PrefixLength);
		}
	}
}
