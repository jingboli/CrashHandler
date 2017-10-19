# Android  app 全局异常统一处理


### 异常处理需求

Android app 出现 crash 时，会出现 “程序异常退出” 的提示并关闭，体验不好，另外主要是无法知道哪里出现的崩溃，需要知道哪里造成的异常，就需要一个全局抓取异常的处理方式，可以把异常保存到手机或者上传到指定的服务器上，这样有利于 bug 的解决。通过微信订阅号的文章发现了一个全局处理该方式的接口`UncaughtExceptionHandler`。


### 接口`UncaughtExceptionHandler`

* 类位于：java.lang.Thread.UncaughtExceptionHandler
* 接口源码：

			public interface UncaughtExceptionHandler {
				/**
				 * Method invoked when the given thread terminates due to the
				 * given uncaught exception.
				 * <p>Any exception thrown by this method will be ignored by the
				 * Java Virtual Machine.
				 * @param t the thread
				 * @param e the exception
				 */
				void uncaughtException(Thread t, Throwable e);
			}
* 主要是通过实现接口的 `uncaughtException(Thread t, Throwable e)` 方法，实现 crash 的捕获和保存到 sd 卡，然后联网的情况下再上传到服务器。

### 具体实现
1. 创建一个类，实现接口`UncaughtExceptionHandler`

				public class CrashHandler implements UncaughtExceptionHandler
2. 实现接口的方法`uncaughtException(Thread t, Throwable e)`，异常的处理就在这里，比如保存异常信息到 SD 卡，自定义错误弹出信息，自动退出。

				@Override
				public void uncaughtException(Thread t, Throwable e) {
					//收集错误信息，保存到 sd 卡上
					errorInfo2SD();
					//弹出自定义的错误提醒
					new Thread(new Runnable() {
						@Override
						public void run() {
							Looper.prepare();
							Toast.makeText(mContext, "UnCrashException", Toast.LENGTH_SHORT).show();
							Looper.loop();
						}
					});
					//杀掉进程，退出应用
					Process.killProcess(Process.myPid());
					System.exit(1);
				}
3. `CrashHandler` 采用单例模式。

			public class CrashHandler implements UncaughtExceptionHandler {

				private static CrashHandler mInstance;

				private CrashHandler() {

				}

				// 单例模式-懒汉
				public static CrashHandler getInstance() {
					if (mInstance == null) {
						synchronized (CrashHandler.class) {
							if (mInstance == null) {
								mInstance = new CrashHandler();
							}
						}
					}
					return mInstance;
				}

			}
4. 定义一个方法，用于把当前应用注册到系统的异常处理中，让系统知道由自定义的异常捕获器处理。

		public void register(Context context) {
			mContext = context;
			Thread.setDefaultUncaughtExceptionHandler(this);
		}
5. 在 Application 中注册。

		public class CrashHandlerApplication extends Application {

			@Override
			public void onCreate() {
				super.onCreate();
				CrashHandler.getInstance().register(getApplicationContext());
			}
		}
6. 收集错误信息并保存到 SD 卡上。

		//用于存储设备信息
		private Map<String, String> mInfo = new HashMap<>();
		//格式化时间，作为Log文件名
		private java.text.DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");

		private void errorInfo2SD(Throwable e) {
			PackageManager pm = mContext.getPackageManager();
			try {
				PackageInfo info = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
				// 获取版本信息
				if (info != null) {
					String versionName = TextUtils.isEmpty(info.versionName) ? "未设置版本名称" : info.versionName;
					String versionCode = info.versionCode + "";
					mInfo.put("versionName", versionName);
					mInfo.put("versionCode", versionCode);
				}
				// 获取设备信息
				Field[] fields = Build.class.getFields();
				if (fields != null && fields.length > 0) {
					for (Field field : fields) {
						field.setAccessible(true);
						mInfo.put(field.getName(), field.get(null).toString());
					}
				}
				// 存储信息到 sd 卡指定目录
				saveErrorInfo(e);
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		private void saveErrorInfo(Throwable e) {
			StringBuffer stringBuffer = new StringBuffer();
			for (Map.Entry<String, String> entry : mInfo.entrySet()) {
				String keyName = entry.getKey();
				String value = entry.getValue();
				stringBuffer.append(keyName + "=" + value + "\n");
			}
			stringBuffer.append("\n-----Crash Log Begin-----\n");
			StringWriter stringWriter = new StringWriter();
			PrintWriter writer = new PrintWriter(stringWriter);
			e.printStackTrace(writer);
			Throwable cause = e.getCause();
			while (cause != null) {
				cause.printStackTrace(writer);
				cause = e.getCause();
			}
			writer.close();
			String string = stringWriter.toString();
			stringBuffer.append(string);
			stringBuffer.append("\n-----Crash Log End-----");
			String format = dateFormat.format(new Date());
			String fileName = "crash-" + format + ".log";

			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				String path = mContext.getFilesDir() + File.separator + "crash";
				File dir = new File(path);
				if (!dir.exists()) {
					dir.mkdirs();
				}
				FileOutputStream fou = null;
				try {
					fou = new FileOutputStream(new File(path, fileName));
					fou.write(stringBuffer.toString().getBytes());
					fou.flush();
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				} finally {
					try {
						if (fou != null) {
							fou.close();
						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}

7. 以上完成了异常的捕获并保存到 sd 卡上，等待 app 再次启动的时候，上传异常信息到服务器上。
8. 保存到 sd 卡上的位置
![手机SD卡位置](http://opvv5mo0o.bkt.clouddn.com/screen1.png)
9. SD 卡中拿到的 Log 信息

		SUPPORTED_64_BIT_ABIS=[Ljava.lang.String;@58ff504
		versionCode=1
		BOARD=msm8939
		BOOTLOADER=3.19.0.0000
		TYPE=user
		ID=MMB29M
		TIME=1461124295000
		BRAND=htc
		SERIAL=HC4AZYC00984
		HARDWARE=qcom
		SUPPORTED_ABIS=[Ljava.lang.String;@abaed
		CPU_ABI=arm64-v8a
		RADIO=unknown
		IS_DEBUGGABLE=false
		MANUFACTURER=HTC
		SUPPORTED_32_BIT_ABIS=[Ljava.lang.String;@c11fd17
		TAGS=release-keys
		CPU_ABI2=
		UNKNOWN=unknown
		USER=buildteam
		FINGERPRINT=htc/htccn_chs_2/htc_a51dtul:6.0.1/MMB29M/738098.4:user/release-keys
		HOST=ABM110
		PRODUCT=htccn_chs_2
		versionName=1.0
		DISPLAY=MMB29M release-keys
		MODEL=HTC D820u
		DEVICE=htc_a51dtul

		-----Crash Log Begin-----
		java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.CharSequence android.widget.TextView.getText()' on a null object reference
			at cc.lijingbo.crashhandler.MainActivity$1.onClick(MainActivity.java:26)
			at android.view.View.performClick(View.java:5232)
			at android.view.View$PerformClick.run(View.java:21289)
			at android.os.Handler.handleCallback(Handler.java:739)
			at android.os.Handler.dispatchMessage(Handler.java:95)
			at android.os.Looper.loop(Looper.java:168)
			at android.app.ActivityThread.main(ActivityThread.java:5885)
			at java.lang.reflect.Method.invoke(Native Method)
			at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:797)
			at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:687)

		-----Crash Log End-----
