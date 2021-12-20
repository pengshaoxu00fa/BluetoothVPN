package cn.adonet.netcore.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRU<K,V> extends LinkedHashMap<K, V>{

    private static final long serialVersionUID = 1L;
    private int maxSize;
    private RemoveListener mRemoveListener;

    public interface RemoveListener<KK,VV>{
        void onRemove(Map.Entry<KK,VV> entry) ;
    }

    public LRU(int initialCapacity,
               float loadFactor,
               boolean accessOrder,
               int maxSize) {
        super(initialCapacity, loadFactor, accessOrder);
        this.maxSize = maxSize;
    }

    public void setRemoveListener(RemoveListener mRemoveListener) {
        this.mRemoveListener = mRemoveListener;
    }

    /**
     * @description 重写LinkedHashMap中的removeEldestEntry方法，当LRU中元素多余maxSize个时，
     *              删除最不经常使用的元素
     * @author rico
     * @created 2017年5月12日 上午11:32:51
     * @param eldest
     * @return
     * @see java.util.LinkedHashMap#removeEldestEntry(java.util.Map.Entry)
     */
    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        // TODO Auto-generated method stub
        if(size() > maxSize){
            if (mRemoveListener != null) {
                mRemoveListener.onRemove(eldest);
            }
            return true;
        }
        return false;
    }

}
