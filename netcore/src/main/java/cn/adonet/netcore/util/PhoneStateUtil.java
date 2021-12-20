package cn.adonet.netcore.util;

import android.content.Context;
import android.content.pm.PackageManager;


/**
 * Created by zengzheying on 16/1/20.
 */
public class PhoneStateUtil {

	public static String getVersionName(Context context) {
		String result = "";

		try {
			result = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0)
					.versionName;
		} catch (PackageManager.NameNotFoundException ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
		}

		return result;
	}

	public static int getVersionCode(Context context) {
		int result = 0;

		try {
			result = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0)
					.versionCode;
		} catch (PackageManager.NameNotFoundException ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
		}

		return result;
	}

}
