package cn.adonet.netcore.nat;

import java.util.HashMap;

public class UDPMap {
    public static class MapEntity {
        private MapEntity(){
        }

        private HashMap<Short, Address> map = new HashMap<>();

        public void map(short localPort, int remoteIP, short remotePort) {
            Address address = map.get(localPort);
            if (address == null) {
                address = new Address();
                map.put(localPort, address);
            }
            address.ip = remoteIP;
            address.port = remotePort;
        }

        public Address find(short localPort) {
            Address address = map.get(localPort);
            return address;
        }

        public void remove(short localPort) {
            map.remove(localPort);
        }

        public synchronized void clear() {
            map.clear();
        }
    }
    public static class Address{
        public int ip;
        public short port;
    }

    public static MapEntity _TO = new MapEntity();
    public static MapEntity _FROM = new MapEntity();



}
