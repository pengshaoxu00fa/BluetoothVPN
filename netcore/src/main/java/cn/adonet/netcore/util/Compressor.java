package cn.adonet.netcore.util;
import androidx.annotation.Nullable;

/**
 * Created by zengzheying on 16/1/2.
 */
public interface Compressor {

	@Nullable
	byte[] compress(byte[] source) throws Exception;

	@Nullable
	byte[] uncompress(byte[] cipher) throws Exception;
}
